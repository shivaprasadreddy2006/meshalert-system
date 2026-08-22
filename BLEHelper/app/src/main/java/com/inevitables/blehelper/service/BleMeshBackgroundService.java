package com.inevitables.blehelper.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.inevitables.blehelper.MainActivity;
import com.inevitables.blehelper.R;
import com.inevitables.blehelper.mesh.BleConstants;
import com.inevitables.blehelper.mesh.BleMeshManager;
import com.inevitables.blehelper.mesh.BleMeshServerManager;
import com.inevitables.blehelper.mesh.DiscoveredBleDevice;
import com.inevitables.blehelper.mesh.MeshPacket;

import com.inevitables.blehelper.net.WebBridgeManager;

public class BleMeshBackgroundService extends Service implements BleMeshManager.BleMeshListener {
    private static final String TAG = "BleMeshBgService";

    public static final String CHANNEL_ID = "ble_mesh_service_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_SERVICE = "com.inevitables.blehelper.action.START_SERVICE";
    public static final String ACTION_STOP_SERVICE = "com.inevitables.blehelper.action.STOP_SERVICE";
    public static final String ACTION_DISCONNECT = "com.inevitables.blehelper.action.DISCONNECT";
    public static final String ACTION_TOGGLE_WAKELOCK = "com.inevitables.blehelper.action.TOGGLE_WAKELOCK";
    public static final String ACTION_TOGGLE_SERVER = "com.inevitables.blehelper.action.TOGGLE_SERVER";
    public static final String EXTRA_ENABLE_WAKELOCK = "extra_enable_wakelock";
    public static final String EXTRA_ENABLE_SERVER = "extra_enable_server";

    private final IBinder mBinder = new LocalBinder();
    private BleMeshManager mMeshManager;
    private BleMeshServerManager mServerManager;
    private PowerManager.WakeLock mWakeLock;
    private boolean mIsRunning = false;
    private boolean mWakeLockEnabled = false;
    private boolean mServerEnabled = true;

    public class LocalBinder extends Binder {
        public BleMeshBackgroundService getService() {
            return BleMeshBackgroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        mMeshManager = BleMeshManager.getInstance(this);
        mServerManager = BleMeshServerManager.getInstance(this);
        mMeshManager.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SERVICE.equals(action)) {
                stopForegroundService();
                return START_NOT_STICKY;
            } else if (ACTION_DISCONNECT.equals(action)) {
                if (mMeshManager != null) {
                    mMeshManager.disconnect();
                }
            } else if (ACTION_TOGGLE_WAKELOCK.equals(action)) {
                boolean enable = intent.getBooleanExtra(EXTRA_ENABLE_WAKELOCK, false);
                setWakeLockEnabled(enable);
            } else if (ACTION_TOGGLE_SERVER.equals(action)) {
                boolean enable = intent.getBooleanExtra(EXTRA_ENABLE_SERVER, true);
                setServerEnabled(enable);
            }
        }

        startInForeground();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void startInForeground() {
        mIsRunning = true;

        // Start node server & advertiser if enabled
        if (mServerEnabled && mServerManager != null) {
            mServerManager.startServer();
        }

        // Continuously scan in background for connectionless alerts from any device
        if (mMeshManager != null && !mMeshManager.isScanning()) {
            mMeshManager.startScan(false);
        }

        Notification notification = buildNotification(
                getString(R.string.notification_title),
                getString(R.string.notification_text_disconnected)
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    public void stopForegroundService() {
        mIsRunning = false;
        if (mServerManager != null) {
            mServerManager.stopServer();
        }
        if (mMeshManager != null && mMeshManager.isScanning()) {
            mMeshManager.stopScan();
        }
        releaseWakeLock();
        stopForeground(true);
        stopSelf();
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public boolean isWakeLockEnabled() {
        return mWakeLockEnabled;
    }

    public void setWakeLockEnabled(boolean enable) {
        mWakeLockEnabled = enable;
        if (enable) {
            acquireWakeLock();
        } else {
            releaseWakeLock();
        }
    }

    public boolean isServerEnabled() {
        return mServerEnabled;
    }

    public void setServerEnabled(boolean enable) {
        mServerEnabled = enable;
        if (mServerManager != null) {
            if (enable && mIsRunning) {
                mServerManager.startServer();
            } else {
                mServerManager.stopServer();
            }
        }
    }

    private void acquireWakeLock() {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BLEHelper:MeshWakeLock");
            }
        }
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire(12 * 60 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            try {
                mWakeLock.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Background Service Channel (Quiet, standard foreground channel)
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                serviceChannel.setDescription(getString(R.string.notification_channel_desc));
                serviceChannel.setShowBadge(false);
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification(String title, String content) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent stopIntent = new Intent(this, BleMeshBackgroundService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent discIntent = new Intent(this, BleMeshBackgroundService.class);
        discIntent.setAction(ACTION_DISCONNECT);
        PendingIntent discPendingIntent = PendingIntent.getService(
                this, 2, discIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", discPendingIntent)
                .addAction(android.R.drawable.ic_delete, "Stop Service", stopPendingIntent);

        return builder.build();
    }

    private void updateNotification() {
        if (!mIsRunning) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            String title = getString(R.string.notification_title);
            String content;
            if (mMeshManager.getConnectionState() == BleMeshManager.STATE_READY && mMeshManager.getCurrentDevice() != null) {
                content = String.format(
                        getString(R.string.notification_text_connected),
                        mMeshManager.getCurrentDevice().getName(),
                        mMeshManager.getCurrentRssi(),
                        mMeshManager.getRxCount(),
                        mMeshManager.getTxCount()
                );
            } else if (mMeshManager.getConnectionState() == BleMeshManager.STATE_CONNECTING) {
                content = "Connecting to BLE Mesh node...";
            } else {
                content = "Forwarding Mesh alerts to Web Application";
            }
            manager.notify(NOTIFICATION_ID, buildNotification(title, content));
        }
    }

    @Override
    public void onScanResult(DiscoveredBleDevice device) {}

    @Override
    public void onScanStateChanged(boolean isScanning) {}

    @Override
    public void onConnectionStateChanged(int state, String message, DiscoveredBleDevice device) {
        updateNotification();
    }

    @Override
    public void onRssiUpdated(int rssi, String quality) {
        updateNotification();
    }

    @Override
    public void onMtuUpdated(int mtu) {}

    @Override
    public void onPhyUpdated(int txPhy, int rxPhy) {}

    @Override
    public void onPacketTransmitted(MeshPacket packet) {
        updateNotification();
    }

    @Override
    public void onPacketReceived(MeshPacket packet) {
        updateNotification();
    }

    @Override
    public void onAlertReceived(int alertId, int level, String sender, String message) {
        // Native Android notifications/dialogs disabled.
        // Forward alert immediately to the Web Application!
        WebBridgeManager bridge = WebBridgeManager.getInstance(this);
        if (bridge.isAutoForwardEnabled()) {
            bridge.sendAlert(alertId, level, sender, message, sender, null);
        }
    }

    @Override
    public void onLog(String tag, String message, int level) {}

    @Override
    public void onStatisticsUpdated(int txCount, int rxCount, long totalBytes, int errorCount) {
        updateNotification();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsRunning = false;
        if (mServerManager != null) {
            mServerManager.stopServer();
        }
        if (mMeshManager != null && mMeshManager.isScanning()) {
            mMeshManager.stopScan();
        }
        releaseWakeLock();
        if (mMeshManager != null) {
            mMeshManager.removeListener(this);
        }
    }
}
