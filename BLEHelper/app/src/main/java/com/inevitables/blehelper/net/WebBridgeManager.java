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
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android TCP Server — listens on a configurable port (default 7000).
 * The Node.js web server connects to this socket as a client.
 * When an alert is received from the BLE mesh, it is written to all
 * currently-connected server-side clients (i.e. the Node.js instance).
 */
public class WebBridgeManager {
    private static final String TAG = "WebBridgeManager";

    private static final String PREFS_NAME = "web_bridge_prefs";
    private static final String KEY_PORT = "pref_server_tcp_port";
    private static final String KEY_AUTO_FORWARD = "pref_auto_forward";

    public static final int DEFAULT_TCP_PORT = 7000;

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
    // Two threads: one for the accept loop, one for processing/sending
    private final ExecutorService mServerExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mSendExecutor = Executors.newSingleThreadExecutor();
    private final List<WebBridgeListener> mListeners = new CopyOnWriteArrayList<>();

    private ServerSocket mServerSocket;
    // The single connected client socket (the Node.js server)
    private Socket mClientSocket;
    private PrintWriter mWriter;
    private boolean mServerRunning = false;
    private boolean mClientConnected = false;

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
        if (listener != null && !mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(WebBridgeListener listener) {
        mListeners.remove(listener);
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
        return mClientConnected && mClientSocket != null && !mClientSocket.isClosed();
    }

    // ------------------------------------------------------------------
    //  Server Lifecycle
    // ------------------------------------------------------------------

    /**
     * Start the TCP server and begin accepting connections from the Node.js backend.
     */
    public void connect(BridgeResultCallback callback) {
        if (mServerRunning) {
            if (callback != null) mMainHandler.post(() -> callback.onResult(true, "Server already running on port " + getServerTcpPort()));
            return;
        }

        mServerExecutor.execute(() -> {
            int port = getServerTcpPort();
            try {
                mServerSocket = new ServerSocket(port);
                mServerRunning = true;
                log(TAG, "TCP Server listening on port " + port + " — waiting for Node.js connection...", BleMeshManager.LOG_INFO);

                while (mServerRunning) {
                    // Block until the Node.js server connects
                    Socket incoming = mServerSocket.accept();
                    handleNodeConnection(incoming);
                }
            } catch (Exception e) {
                if (mServerRunning) {
                    // Unexpected error
                    log(TAG, "TCP Server error: " + e.getMessage(), BleMeshManager.LOG_ERROR);
                    notifyStatus(false, "Server error: " + e.getMessage());
                }
                mServerRunning = false;
                if (callback != null) mMainHandler.post(() -> callback.onResult(false, e.getMessage()));
            }
        });

        if (callback != null) mMainHandler.post(() -> callback.onResult(true, "TCP Server started on port " + getServerTcpPort()));
    }

    /**
     * Stop the server and close all connections.
     */
    public void disconnect() {
        mServerRunning = false;
        closeClientSocket();
        try {
            if (mServerSocket != null && !mServerSocket.isClosed()) mServerSocket.close();
        } catch (Exception ignored) {}
        mServerSocket = null;
        log(TAG, "TCP Server stopped", BleMeshManager.LOG_WARN);
        notifyStatus(false, "Disconnected");
    }

    // ------------------------------------------------------------------
    //  Connection Handler
    // ------------------------------------------------------------------

    private void handleNodeConnection(Socket socket) {
        // Close any previous client connection
        closeClientSocket();
        mClientSocket = socket;
        mClientConnected = true;

        String remoteAddr = socket.getRemoteSocketAddress().toString();
        log(TAG, "Node.js server connected from " + remoteAddr, BleMeshManager.LOG_SUCCESS);
        notifyStatus(true, "Node.js connected from " + remoteAddr);

        try {
            mWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Read incoming messages from Node.js (ACKs, registration, etc.)
            String line;
            while (mClientConnected && !socket.isClosed() && (line = reader.readLine()) != null) {
                log(TAG, "Node.js → Android: " + line, BleMeshManager.LOG_INFO);
            }
        } catch (Exception e) {
            log(TAG, "Node.js connection lost: " + e.getMessage(), BleMeshManager.LOG_WARN);
        } finally {
            mClientConnected = false;
            closeClientSocket();
            notifyStatus(false, "Node.js disconnected — waiting for reconnect...");
        }
    }

    private void closeClientSocket() {
        mClientConnected = false;
        try {
            if (mWriter != null) mWriter.close();
            if (mClientSocket != null && !mClientSocket.isClosed()) mClientSocket.close();
        } catch (Exception ignored) {}
        mClientSocket = null;
        mWriter = null;
    }

    // ------------------------------------------------------------------
    //  Sending Alerts to Node.js
    // ------------------------------------------------------------------

    /**
     * Sends a BLE Mesh emergency alert to the connected Node.js web server.
     */
    public void sendAlert(int alertId, int level, String sender, String message, String area, BridgeResultCallback callback) {
        mSendExecutor.execute(() -> {
            try {
                String alertType = "GENERAL";
                String priority;

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
                boolean sent = writeToClient(payload);

                if (sent) {
                    log(TAG, "Alert sent to Node.js via TCP: [" + alertType + "] " + message, BleMeshManager.LOG_SUCCESS);
                    if (callback != null) mMainHandler.post(() -> callback.onResult(true, "Alert sent to Node.js"));
                } else {
                    log(TAG, "Node.js not connected — alert not delivered", BleMeshManager.LOG_ERROR);
                    if (callback != null) mMainHandler.post(() -> callback.onResult(false, "Node.js not connected"));
                }

            } catch (Exception e) {
                log(TAG, "Error sending alert: " + e.getMessage(), BleMeshManager.LOG_ERROR);
                if (callback != null) mMainHandler.post(() -> callback.onResult(false, "Error: " + e.getMessage()));
            }
        });
    }

    private boolean writeToClient(String payload) {
        if (mWriter != null && mClientConnected && mClientSocket != null && !mClientSocket.isClosed()) {
            mWriter.println(payload);
            mWriter.flush();
            return !mWriter.checkError();
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private String getIsoTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    private void notifyStatus(boolean connected, String message) {
        mMainHandler.post(() -> {
            for (WebBridgeListener l : mListeners) l.onBridgeStatusChanged(connected, message);
        });
    }

    private void log(String tag, String message, int level) {
        mMainHandler.post(() -> {
            for (WebBridgeListener l : mListeners) l.onBridgeLog(tag, message, level);
        });
    }
}
