package com.inevitables.blehelper.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.inevitables.blehelper.mesh.BleConstants;
import com.inevitables.blehelper.mesh.BleMeshManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages transmission of BLE Mesh alerts from the Android app to the Node.js Web Application
 * via raw TCP socket (Port 7000) with HTTP fallback (Port 5000).
 */
public class WebBridgeManager {
    private static final String TAG = "WebBridgeManager";

    private static final String PREFS_NAME = "web_bridge_prefs";
    private static final String KEY_HOST = "pref_server_host";
    private static final String KEY_PORT = "pref_server_tcp_port";
    private static final String KEY_AUTO_FORWARD = "pref_auto_forward";

    public static final String DEFAULT_HOST = "10.0.2.2"; // Standard emulator host loopback (or local network IP)
    public static final int DEFAULT_TCP_PORT = 7000;
    public static final int DEFAULT_HTTP_PORT = 5000;

    public interface WebBridgeListener {
        void onBridgeStatusChanged(boolean connected, String message);
        void onBridgeLog(String tag, String message, int level);
    }

    public interface BridgeResultCallback {
        void onResult(boolean success, String message);
    }

    private static WebBridgeManager sInstance;
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final List<WebBridgeListener> mListeners = new CopyOnWriteArrayList<>();

    private Socket mSocket;
    private PrintWriter mWriter;
    private BufferedReader mReader;
    private boolean mIsConnected = false;
    private boolean mIsConnecting = false;

    private WebBridgeManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized WebBridgeManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new WebBridgeManager(context);
        }
        return sInstance;
    }

    public void addListener(WebBridgeListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(WebBridgeListener listener) {
        mListeners.remove(listener);
    }

    public String getServerHost() {
        return mPrefs.getString(KEY_HOST, DEFAULT_HOST);
    }

    public void setServerHost(String host) {
        mPrefs.edit().putString(KEY_HOST, host != null ? host.trim() : DEFAULT_HOST).apply();
    }

    public int getServerTcpPort() {
        return mPrefs.getInt(KEY_PORT, DEFAULT_TCP_PORT);
    }

    public void setServerTcpPort(int port) {
        mPrefs.edit().putInt(KEY_PORT, port > 0 ? port : DEFAULT_TCP_PORT).apply();
    }

    public boolean isAutoForwardEnabled() {
        return mPrefs.getBoolean(KEY_AUTO_FORWARD, true);
    }

    public void setAutoForwardEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_AUTO_FORWARD, enabled).apply();
    }

    public boolean isConnected() {
        return mIsConnected && mSocket != null && !mSocket.isClosed() && mSocket.isConnected();
    }

    /**
     * Connects or reconnects TCP socket to the Web Application backend.
     */
    public void connect(BridgeResultCallback callback) {
        if (mIsConnecting) return;
        mIsConnecting = true;

        mExecutor.execute(() -> {
            String host = getServerHost();
            int port = getServerTcpPort();

            try {
                closeSocket();
                log(TAG, "Connecting TCP socket to Web Server at " + host + ":" + port + "...", BleMeshManager.LOG_INFO);

                mSocket = new Socket();
                mSocket.connect(new InetSocketAddress(host, port), 4000);
                mWriter = new PrintWriter(new OutputStreamWriter(mSocket.getOutputStream()), true);
                mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));

                mIsConnected = true;
                mIsConnecting = false;

                // Send initial registration packet
                JSONObject reg = new JSONObject();
                reg.put("type", "REGISTRATION");
                reg.put("client", "Android BLE Mesh Node");
                reg.put("timestamp", getIsoTimestamp());
                mWriter.println(reg.toString());

                log(TAG, "Connected to Web Application TCP Server (" + host + ":" + port + ")", BleMeshManager.LOG_SUCCESS);
                notifyStatus(true, "Connected to Web App (" + host + ":" + port + ")");

                if (callback != null) {
                    mMainHandler.post(() -> callback.onResult(true, "Connected to " + host + ":" + port));
                }

                // Start background reader loop for ACKs
                listenForIncomingAcks();

            } catch (Exception e) {
                mIsConnecting = false;
                mIsConnected = false;
                closeSocket();
                String err = "Connection failed to " + host + ":" + port + " (" + e.getMessage() + ")";
                log(TAG, err, BleMeshManager.LOG_ERROR);
                notifyStatus(false, err);

                if (callback != null) {
                    mMainHandler.post(() -> callback.onResult(false, err));
                }
            }
        });
    }

    /**
     * Disconnects the TCP socket.
     */
    public void disconnect() {
        mExecutor.execute(() -> {
            closeSocket();
            mIsConnected = false;
            mIsConnecting = false;
            log(TAG, "Disconnected from Web Application TCP server", BleMeshManager.LOG_WARN);
            notifyStatus(false, "Disconnected");
        });
    }

    private void closeSocket() {
        try {
            if (mWriter != null) mWriter.close();
            if (mReader != null) mReader.close();
            if (mSocket != null && !mSocket.isClosed()) mSocket.close();
        } catch (Exception ignored) {
        }
        mSocket = null;
        mWriter = null;
        mReader = null;
    }

    private void listenForIncomingAcks() {
        try {
            String line;
            while (mSocket != null && !mSocket.isClosed() && (line = mReader.readLine()) != null) {
                log(TAG, "Web App TCP Server response: " + line, BleMeshManager.LOG_INFO);
            }
        } catch (Exception e) {
            if (mIsConnected) {
                log(TAG, "TCP socket connection lost: " + e.getMessage(), BleMeshManager.LOG_WARN);
                mIsConnected = false;
                notifyStatus(false, "Connection lost");
            }
        }
    }

    /**
     * Sends an Alert directly to the Web Application.
     */
    public void sendAlert(int alertId, int level, String sender, String message, String area, BridgeResultCallback callback) {
        mExecutor.execute(() -> {
            try {
                String alertType = "GENERAL";
                String priority = "MEDIUM";

                if (level == BleConstants.ALERT_LEVEL_EMERGENCY) {
                    priority = "CRITICAL";
                } else if (level == BleConstants.ALERT_LEVEL_WARN) {
                    priority = "HIGH";
                } else {
                    priority = "LOW";
                }

                String upperMsg = (message != null) ? message.toUpperCase(Locale.ROOT) : "";
                if (upperMsg.contains("FIRE")) {
                    alertType = "FIRE";
                } else if (upperMsg.contains("STAMPEDE") || upperMsg.contains("CROWD")) {
                    alertType = "STAMPEDE";
                } else if (upperMsg.contains("MEDICAL") || upperMsg.contains("DOCTOR") || upperMsg.contains("AMBULANCE")) {
                    alertType = "MEDICAL";
                } else if (upperMsg.contains("EVACUAT") || upperMsg.contains("EXIT")) {
                    alertType = "EVACUATION";
                }

                String resolvedArea = (area != null && !area.isEmpty()) ? area : (sender != null ? sender : "Floor 1");

                JSONObject json = new JSONObject();
                json.put("type", "ALERT");
                json.put("alertType", alertType);
                json.put("priority", priority);
                json.put("message", message != null ? message : "Emergency alert from BLE Mesh node");
                json.put("area", resolvedArea);
                json.put("alertId", alertId);
                json.put("sender", sender != null ? sender : "Mesh Node");
                json.put("timestamp", getIsoTimestamp());

                String payload = json.toString();

                boolean sentTcp = sendViaTcp(payload);
                if (sentTcp) {
                    log(TAG, "Alert sent to Web App over TCP: [" + alertType + "] " + message, BleMeshManager.LOG_SUCCESS);
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onResult(true, "Alert forwarded to Web App via TCP"));
                    }
                    return;
                }

                // If TCP failed or not connected, send via HTTP POST fallback
                log(TAG, "TCP unavailable, falling back to HTTP POST to Web App...", BleMeshManager.LOG_INFO);
                boolean sentHttp = sendViaHttp(json);
                if (sentHttp) {
                    log(TAG, "Alert sent to Web App over HTTP: [" + alertType + "] " + message, BleMeshManager.LOG_SUCCESS);
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onResult(true, "Alert forwarded to Web App via HTTP"));
                    }
                } else {
                    log(TAG, "Failed to deliver alert to Web App over both TCP and HTTP", BleMeshManager.LOG_ERROR);
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onResult(false, "Could not reach Web Server at " + getServerHost()));
                    }
                }

            } catch (Exception e) {
                log(TAG, "Error formatting alert for Web App: " + e.getMessage(), BleMeshManager.LOG_ERROR);
                if (callback != null) {
                    mMainHandler.post(() -> callback.onResult(false, "Error: " + e.getMessage()));
                }
            }
        });
    }

    private boolean sendViaTcp(String payload) {
        try {
            if (mSocket == null || mSocket.isClosed() || !mSocket.isConnected()) {
                // Try quick auto-connect
                String host = getServerHost();
                int port = getServerTcpPort();
                mSocket = new Socket();
                mSocket.connect(new InetSocketAddress(host, port), 2500);
                mWriter = new PrintWriter(new OutputStreamWriter(mSocket.getOutputStream()), true);
                mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                mIsConnected = true;
                notifyStatus(true, "Connected to Web App (" + host + ":" + port + ")");
            }

            if (mWriter != null) {
                mWriter.println(payload);
                mWriter.flush();
                return true;
            }
        } catch (Exception e) {
            closeSocket();
            mIsConnected = false;
            notifyStatus(false, "Disconnected: " + e.getMessage());
        }
        return false;
    }

    private boolean sendViaHttp(JSONObject json) {
        HttpURLConnection conn = null;
        try {
            String host = getServerHost();
            URL url = new URL("http://" + host + ":" + DEFAULT_HTTP_PORT + "/api/test/alert");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                writer.write(json.toString());
                writer.flush();
            }

            int responseCode = conn.getResponseCode();
            return responseCode >= 200 && responseCode < 300;
        } catch (Exception e) {
            Log.w(TAG, "HTTP fallback error: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String getIsoTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    private void notifyStatus(boolean connected, String message) {
        mMainHandler.post(() -> {
            for (WebBridgeListener l : mListeners) {
                l.onBridgeStatusChanged(connected, message);
            }
        });
    }

    private void log(String tag, String message, int level) {
        mMainHandler.post(() -> {
            for (WebBridgeListener l : mListeners) {
                l.onBridgeLog(tag, message, level);
            }
        });
    }
}
