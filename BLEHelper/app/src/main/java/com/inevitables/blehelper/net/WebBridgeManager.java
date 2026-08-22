package com.inevitables.blehelper.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.inevitables.blehelper.mesh.BleConstants;
import com.inevitables.blehelper.mesh.BleMeshManager;

import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
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
 * Manages communication between Android BLE Mesh Helper and the Cloud Web Application (Railway).
 * Uses robust HTTPS REST endpoints to bridge emergency alerts through mobile carrier NAT/firewalls.
 */
public class WebBridgeManager {
    private static final String TAG = "WebBridgeManager";

    private static final String PREFS_NAME = "web_bridge_prefs";
    private static final String KEY_SERVER_URL = "pref_server_url";
    private static final String KEY_AUTO_FORWARD = "pref_auto_forward";

    // Default Railway Production Backend URL
    public static final String DEFAULT_SERVER_URL = "https://meshalert-system-production.up.railway.app";

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

    private boolean mIsConnected = false;

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

    public String getServerUrl() {
        return mPrefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public void setServerUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            String clean = url.trim();
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                clean = "https://" + clean;
            }
            if (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }
            mPrefs.edit().putString(KEY_SERVER_URL, clean).apply();
        }
    }

    public boolean isAutoForwardEnabled() {
        return mPrefs.getBoolean(KEY_AUTO_FORWARD, true);
    }

    public void setAutoForwardEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_AUTO_FORWARD, enabled).apply();
    }

    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     * Tests connection to the Railway backend by sending a heartbeat ping.
     */
    public void connect(BridgeResultCallback callback) {
        mExecutor.execute(() -> {
            String baseUrl = getServerUrl();
            log(TAG, "Connecting to Railway Cloud Backend: " + baseUrl, BleMeshManager.LOG_INFO);

            try {
                URL url = new URL(baseUrl + "/api/device/auto-connect");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();

                int code = conn.getResponseCode();
                conn.disconnect();

                if (code >= 200 && code < 400) {
                    mIsConnected = true;
                    log(TAG, "Connected to Railway Backend (" + baseUrl + ")", BleMeshManager.LOG_SUCCESS);
                    notifyStatus(true, "Connected to Cloud Backend 🟢");
                    if (callback != null) mMainHandler.post(() -> callback.onResult(true, "Connected to Railway Backend!"));
                } else {
                    mIsConnected = false;
                    String err = "HTTP Error " + code + " from " + baseUrl;
                    log(TAG, err, BleMeshManager.LOG_ERROR);
                    notifyStatus(false, err);
                    if (callback != null) mMainHandler.post(() -> callback.onResult(false, err));
                }
            } catch (Exception e) {
                mIsConnected = false;
                String err = "Cannot reach " + baseUrl + " (" + e.getMessage() + ")";
                log(TAG, err, BleMeshManager.LOG_ERROR);
                notifyStatus(false, err);
                if (callback != null) mMainHandler.post(() -> callback.onResult(false, err));
            }
        });
    }

    public void disconnect() {
        mIsConnected = false;
        notifyStatus(false, "Disconnected");
        log(TAG, "Disconnected from Web Bridge", BleMeshManager.LOG_WARN);
    }

    /**
     * Sends an Emergency Alert to the Railway Backend.
     */
    public void sendAlert(int alertId, int level, String sender, String message, String area, BridgeResultCallback callback) {
        mExecutor.execute(() -> {
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

                String baseUrl = getServerUrl();
                URL url = new URL(baseUrl + "/api/alert");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                    writer.write(json.toString());
                    writer.flush();
                }

                int code = conn.getResponseCode();
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    mIsConnected = true;
                    log(TAG, "🚨 Alert forwarded to Cloud Web App: [" + alertType + "] " + message, BleMeshManager.LOG_SUCCESS);
                    notifyStatus(true, "Alert forwarded to Web App 🟢");
                    if (callback != null) mMainHandler.post(() -> callback.onResult(true, "Alert forwarded to Web App!"));
                } else {
                    String err = "Server responded with code " + code;
                    log(TAG, err, BleMeshManager.LOG_ERROR);
                    if (callback != null) mMainHandler.post(() -> callback.onResult(false, err));
                }

            } catch (Exception e) {
                log(TAG, "Failed to send alert to Cloud Web App: " + e.getMessage(), BleMeshManager.LOG_ERROR);
                if (callback != null) mMainHandler.post(() -> callback.onResult(false, "Error: " + e.getMessage()));
            }
        });
    }

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
