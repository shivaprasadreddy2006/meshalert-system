package com.inevitables.blehelper.mesh;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressLint("MissingPermission")
public class BleMeshServerManager {
    private static final String TAG = "BleMeshServer";

    public interface MeshServerListener {
        void onServerStateChanged(boolean isAdvertising, boolean isGattServerRunning);
        void onClientConnected(BluetoothDevice device);
        void onClientDisconnected(BluetoothDevice device);
        void onDataReceived(BluetoothDevice device, byte[] data);
        void onAlertReceived(int alertId, int level, String senderName, String message);
        void onServerLog(String tag, String message, int level);
    }

    private static BleMeshServerManager sInstance;
    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<MeshServerListener> mListeners = new CopyOnWriteArrayList<>();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private BluetoothLeAdvertiser mAdvertiser;

    private boolean mIsAdvertising = false;
    private boolean mIsGattServerRunning = false;
    private final List<BluetoothDevice> mConnectedClients = Collections.synchronizedList(new ArrayList<>());

    private BluetoothGattCharacteristic mCharDataIn;
    private BluetoothGattCharacteristic mCharDataOut;

    private BleMeshServerManager(Context context) {
        mContext = context.getApplicationContext();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager != null) {
            mBluetoothAdapter = mBluetoothManager.getAdapter();
        }
    }

    public static synchronized BleMeshServerManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BleMeshServerManager(context);
        }
        return sInstance;
    }

    public void addListener(MeshServerListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(MeshServerListener listener) {
        mListeners.remove(listener);
    }

    public boolean isAdvertising() {
        return mIsAdvertising;
    }

    public boolean isGattServerRunning() {
        return mIsGattServerRunning;
    }

    public List<BluetoothDevice> getConnectedClients() {
        return new ArrayList<>(mConnectedClients);
    }

    public synchronized void startServer() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            log(TAG, "Cannot start mesh node: Bluetooth disabled", BleMeshManager.LOG_ERROR);
            return;
        }

        startGattServer();
        startAdvertising();
    }

    public synchronized void stopServer() {
        stopAdvertising();
        stopGattServer();
    }

    private void startGattServer() {
        if (mGattServer != null) return;

        mGattServer = mBluetoothManager.openGattServer(mContext, mGattServerCallback);
        if (mGattServer == null) {
            log(TAG, "Failed to open GATT Server", BleMeshManager.LOG_ERROR);
            return;
        }

        // Mesh Proxy Service (0x1828)
        BluetoothGattService proxyService = new BluetoothGattService(
                BleConstants.MESH_PROXY_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        // Data In (0x2ADE) - Write
        mCharDataIn = new BluetoothGattCharacteristic(
                BleConstants.MESH_PROXY_DATA_IN_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        // Data Out (0x2ADF) - Notify / Read
        mCharDataOut = new BluetoothGattCharacteristic(
                BleConstants.MESH_PROXY_DATA_OUT_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
        );

        // CCCD Descriptor for Notifications
        BluetoothGattDescriptor cccd = new BluetoothGattDescriptor(
                BleConstants.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
        );
        mCharDataOut.addDescriptor(cccd);

        proxyService.addCharacteristic(mCharDataIn);
        proxyService.addCharacteristic(mCharDataOut);

        mGattServer.addService(proxyService);
        mIsGattServerRunning = true;
        log(TAG, "Mesh GATT Proxy Server started (0x1828, In: 0x2ADE, Out: 0x2ADF)", BleMeshManager.LOG_SUCCESS);
        notifyState();
    }

    private void stopGattServer() {
        if (mGattServer != null) {
            try {
                mGattServer.clearServices();
                mGattServer.close();
            } catch (Exception e) {
                log(TAG, "Error stopping GATT Server: " + e.getMessage(), BleMeshManager.LOG_WARN);
            }
            mGattServer = null;
        }
        mConnectedClients.clear();
        mIsGattServerRunning = false;
        notifyState();
    }

    public void startAdvertising() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            log(TAG, "BLE Multiple Advertisement is not supported on this hardware", BleMeshManager.LOG_WARN);
            return;
        }

        mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        if (mAdvertiser == null) {
            log(TAG, "BluetoothLeAdvertiser unavailable", BleMeshManager.LOG_ERROR);
            return;
        }

        stopAdvertising();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(BleConstants.MESH_PROXY_SERVICE_UUID))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        try {
            mAdvertiser.startAdvertising(settings, advertiseData, scanResponse, mAdvertiseCallback);
        } catch (Exception e) {
            log(TAG, "Failed to start Mesh advertising: " + e.getMessage(), BleMeshManager.LOG_ERROR);
        }
    }

    public void stopAdvertising() {
        if (mAdvertiser != null && mIsAdvertising) {
            try {
                mAdvertiser.stopAdvertising(mAdvertiseCallback);
            } catch (Exception e) {
                log(TAG, "Error stopping advertising: " + e.getMessage(), BleMeshManager.LOG_WARN);
            }
            mIsAdvertising = false;
            notifyState();
            log(TAG, "Mesh Node Advertising stopped", BleMeshManager.LOG_INFO);
        }
    }

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            mIsAdvertising = true;
            log(TAG, "Mesh Proxy Node is now ADVERTISING as a reachable node! Other phones can scan and connect.", BleMeshManager.LOG_SUCCESS);
            notifyState();
        }

        @Override
        public void onStartFailure(int errorCode) {
            mIsAdvertising = false;
            String errorReason;
            switch (errorCode) {
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    errorReason = "ADVERTISE_FAILED_DATA_TOO_LARGE (1) - Payload exceeded 31 bytes";
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    errorReason = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS (2) - No advertising slots";
                    break;
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    mIsAdvertising = true;
                    notifyState();
                    return;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    errorReason = "ADVERTISE_FAILED_INTERNAL_ERROR (4) - Controller error";
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    errorReason = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED (5) - Hardware unsupported";
                    break;
                default:
                    errorReason = "Unknown Error (" + errorCode + ")";
                    break;
            }
            log(TAG, "Mesh Node Advertising failed: " + errorReason, BleMeshManager.LOG_ERROR);
            notifyState();
        }
    };

    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if (!mConnectedClients.contains(device)) {
                    mConnectedClients.add(device);
                }
                String name = (device.getName() != null) ? device.getName() : "Remote Device";
                log(TAG, "Client connected to this Mesh Node: " + name + " [" + device.getAddress() + "]", BleMeshManager.LOG_SUCCESS);
                mMainHandler.post(() -> {
                    for (MeshServerListener l : mListeners) {
                        l.onClientConnected(device);
                    }
                });
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                mConnectedClients.remove(device);
                log(TAG, "Client disconnected from this Mesh Node: " + device.getAddress(), BleMeshManager.LOG_WARN);
                mMainHandler.post(() -> {
                    for (MeshServerListener l : mListeners) {
                        l.onClientDisconnected(device);
                    }
                });
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (responseNeeded && mGattServer != null) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }

            if (value != null && value.length > 0) {
                handleIncomingMeshPacket(device, value);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (responseNeeded && mGattServer != null) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
            log(TAG, "CCCD subscribed by client " + device.getAddress() + " - Notifications active!", BleMeshManager.LOG_SUCCESS);
        }
    };

    private void handleIncomingMeshPacket(BluetoothDevice device, byte[] data) {
        String senderName = (device != null && device.getName() != null) ? device.getName() : "Remote Node";
        String senderAddress = (device != null) ? device.getAddress() : "Unknown";

        // Check if incoming packet is an Alert
        ParsedAlert alert = parseMeshAlert(data);
        if (alert != null) {
            log(TAG, String.format("🚨 MESH ALERT RECEIVED! ID: 0x%04X, Level: %d, From: %s [%s], Msg: %s", alert.alertId, alert.level, senderName, senderAddress, alert.message), BleMeshManager.LOG_WARN);
            final String alertMsg = alert.message;
            final int alertLvl = alert.level;
            final int alertId = alert.alertId;
            mMainHandler.post(() -> {
                for (MeshServerListener l : mListeners) {
                    l.onAlertReceived(alertId, alertLvl, senderName + " (" + senderAddress + ")", alertMsg);
                }
            });

            // Send Acknowledgment back to client
            byte[] ackPacket = new byte[]{
                    BleConstants.SAR_COMPLETE,
                    BleConstants.OPCODE_MESH_ALERT_ACK,
                    (byte) ((alertId >> 8) & 0xFF),
                    (byte) (alertId & 0xFF)
            };
            sendToConnectedClients(ackPacket);
        } else {
            // Check if ACK
            if (data.length >= 2 && (data[0] == BleConstants.OPCODE_MESH_ALERT_ACK || data[1] == BleConstants.OPCODE_MESH_ALERT_ACK)) {
                log(TAG, "✅ Remote node confirmed receipt of the alert (ACK received)!", BleMeshManager.LOG_SUCCESS);
            }
        }

        mMainHandler.post(() -> {
            for (MeshServerListener l : mListeners) {
                l.onDataReceived(device, data);
            }
        });
    }

    public boolean sendToConnectedClients(byte[] data) {
        if (mGattServer == null || mCharDataOut == null || mConnectedClients.isEmpty()) {
            return false;
        }

        boolean anySuccess = false;
        for (BluetoothDevice client : new ArrayList<>(mConnectedClients)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    int res = mGattServer.notifyCharacteristicChanged(client, mCharDataOut, false, data);
                    if (res == BluetoothGatt.GATT_SUCCESS) {
                        anySuccess = true;
                    }
                } else {
                    mCharDataOut.setValue(data);
                    boolean ok = mGattServer.notifyCharacteristicChanged(client, mCharDataOut, false);
                    if (ok) {
                        anySuccess = true;
                    }
                }
            } catch (Exception e) {
                log(TAG, "Error notifying client " + client.getAddress() + ": " + e.getMessage(), BleMeshManager.LOG_ERROR);
            }
        }
        return anySuccess;
    }

    public static class ParsedAlert {
        public int alertId;
        public int level;
        public String message;
    }

    public static ParsedAlert parseMeshAlert(byte[] data) {
        if (data == null || data.length < 3) return null;

        // Check if Opcode 0xA1 is at index 0: [0xA1, ID_HI, ID_LO, LEVEL, MSG...]
        if (data[0] == BleConstants.OPCODE_MESH_ALERT) {
            ParsedAlert alert = new ParsedAlert();
            if (data.length >= 4) {
                alert.alertId = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                alert.level = data[3] & 0xFF;
                alert.message = (data.length > 4) ? new String(data, 4, data.length - 4, StandardCharsets.UTF_8) : "";
            } else {
                alert.alertId = (int) (System.currentTimeMillis() & 0xFFFF);
                alert.level = data[1] & 0xFF;
                alert.message = (data.length > 2) ? new String(data, 2, data.length - 2, StandardCharsets.UTF_8) : "";
            }
            return alert;
        }

        // Check if Opcode 0xA1 is at index 1 (after SAR header 0x00): [SAR, 0xA1, ID_HI, ID_LO, LEVEL, MSG...]
        if (data.length >= 4 && data[1] == BleConstants.OPCODE_MESH_ALERT) {
            ParsedAlert alert = new ParsedAlert();
            if (data.length >= 5) {
                alert.alertId = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
                alert.level = data[4] & 0xFF;
                alert.message = (data.length > 5) ? new String(data, 5, data.length - 5, StandardCharsets.UTF_8) : "";
            } else {
                alert.alertId = (int) (System.currentTimeMillis() & 0xFFFF);
                alert.level = data[2] & 0xFF;
                alert.message = (data.length > 3) ? new String(data, 3, data.length - 3, StandardCharsets.UTF_8) : "";
            }
            return alert;
        }

        // Check anywhere in first 6 bytes
        for (int i = 0; i < Math.min(data.length - 3, 6); i++) {
            if (data[i] == BleConstants.OPCODE_MESH_ALERT) {
                ParsedAlert alert = new ParsedAlert();
                if (data.length >= i + 4) {
                    alert.alertId = ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
                    alert.level = data[i + 3] & 0xFF;
                    alert.message = (data.length > i + 4) ? new String(data, i + 4, data.length - (i + 4), StandardCharsets.UTF_8) : "";
                } else {
                    alert.alertId = (int) (System.currentTimeMillis() & 0xFFFF);
                    alert.level = data[i + 1] & 0xFF;
                    alert.message = (data.length > i + 2) ? new String(data, i + 2, data.length - (i + 2), StandardCharsets.UTF_8) : "";
                }
                return alert;
            }
        }

        return null;
    }

    public boolean broadcastAlertBeacon(int alertId, int alertLevel, String message) {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) return false;

        mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        if (mAdvertiser == null) return false;

        byte[] msgBytes = (message != null) ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
        int maxLen = Math.min(msgBytes.length, 10);
        // Payload: [0xA1, ID_HI, ID_LO, LEVEL, MSG...]
        byte[] payload = new byte[4 + maxLen];
        payload[0] = BleConstants.OPCODE_MESH_ALERT;
        payload[1] = (byte) ((alertId >> 8) & 0xFF);
        payload[2] = (byte) (alertId & 0xFF);
        payload[3] = (byte) alertLevel;
        System.arraycopy(msgBytes, 0, payload, 4, maxLen);

        // Temporarily stop regular advertising to ensure hardware slot is free for the alert burst
        final boolean wasAdvertising = mIsAdvertising;
        if (wasAdvertising) {
            stopAdvertising();
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(3500) // 3.5 seconds burst (clean and fast)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        // Broadcast with BOTH Service UUID (0x1828) AND Manufacturer Data
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(BleConstants.MESH_PROXY_SERVICE_UUID))
                .addManufacturerData(BleConstants.MESH_MANUFACTURER_ID, payload)
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        final AdvertiseCallback burstCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                log(TAG, "🚨 Broadcasted Alert Burst (ID: 0x" + Integer.toHexString(alertId).toUpperCase() + ") into the air!", BleMeshManager.LOG_TX);
                // Resume standard advertising after 3.8s
                mMainHandler.postDelayed(() -> {
                    if (wasAdvertising) {
                        startAdvertising();
                    }
                }, 3800);
            }

            @Override
            public void onStartFailure(int errorCode) {
                log(TAG, "Alert beacon broadcast failed (code: " + errorCode + ")", BleMeshManager.LOG_WARN);
                if (wasAdvertising) {
                    startAdvertising();
                }
            }
        };

        try {
            mAdvertiser.startAdvertising(settings, data, scanResponse, burstCallback);
            return true;
        } catch (Exception e) {
            log(TAG, "Error broadcasting alert beacon: " + e.getMessage(), BleMeshManager.LOG_ERROR);
            if (wasAdvertising) {
                startAdvertising();
            }
            return false;
        }
    }

    private void notifyState() {
        mMainHandler.post(() -> {
            for (MeshServerListener l : mListeners) {
                l.onServerStateChanged(mIsAdvertising, mIsGattServerRunning);
            }
        });
    }

    private void log(String tag, String message, int level) {
        mMainHandler.post(() -> {
            for (MeshServerListener l : mListeners) {
                l.onServerLog(tag, message, level);
            }
        });
    }
}
