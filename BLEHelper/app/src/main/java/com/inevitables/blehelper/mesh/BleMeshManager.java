package com.inevitables.blehelper.mesh;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressLint("MissingPermission")
public class BleMeshManager {
    private static final String TAG = "BleMeshManager";

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_DISCOVERING_SERVICES = 2;
    public static final int STATE_REQUESTING_MTU = 3;
    public static final int STATE_CONFIGURING_CCCD = 4;
    public static final int STATE_READY = 5;
    public static final int STATE_ERROR = 6;

    public static final int LOG_INFO = 0;
    public static final int LOG_SUCCESS = 1;
    public static final int LOG_WARN = 2;
    public static final int LOG_ERROR = 3;
    public static final int LOG_RX = 4;
    public static final int LOG_TX = 5;

    public interface BleMeshListener {
        void onScanResult(DiscoveredBleDevice device);
        void onScanStateChanged(boolean isScanning);
        void onConnectionStateChanged(int state, String message, DiscoveredBleDevice device);
        void onRssiUpdated(int rssi, String quality);
        void onMtuUpdated(int mtu);
        void onPhyUpdated(int txPhy, int rxPhy);
        void onPacketTransmitted(MeshPacket packet);
        void onPacketReceived(MeshPacket packet);
        void onAlertReceived(int alertId, int level, String sender, String message);
        void onLog(String tag, String message, int level);
        void onStatisticsUpdated(int txCount, int rxCount, long totalBytes, int errorCount);
    }

    private static BleMeshManager sInstance;
    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final List<BleMeshListener> mListeners = new CopyOnWriteArrayList<>();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBleScanner;
    private BluetoothGatt mBluetoothGatt;
    private DiscoveredBleDevice mCurrentDevice;

    private BluetoothGattCharacteristic mMeshProxyDataIn;
    private BluetoothGattCharacteristic mMeshProxyDataOut;
    private BluetoothGattCharacteristic mMeshProvDataIn;
    private BluetoothGattCharacteristic mMeshProvDataOut;

    private boolean mIsScanning = false;
    private int mConnectionState = STATE_DISCONNECTED;
    private int mCurrentMtu = 23;
    private int mCurrentRssi = 0;
    private int mTxCount = 0;
    private int mRxCount = 0;
    private long mTotalBytes = 0;
    private int mErrorCount = 0;

    private final Map<String, DiscoveredBleDevice> mDiscoveredDevices = Collections.synchronizedMap(new HashMap<>());
    private final Set<Integer> mProcessedAlertIds = Collections.synchronizedSet(new LinkedHashSet<>());

    private final Runnable mRssiPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBluetoothGatt != null && mConnectionState == STATE_READY) {
                mBluetoothGatt.readRemoteRssi();
                mMainHandler.postDelayed(this, BleConstants.RSSI_POLL_INTERVAL_MS);
            }
        }
    };

    private BleMeshManager(Context context) {
        mContext = context.getApplicationContext();
        BluetoothManager bm = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            mBluetoothAdapter = bm.getAdapter();
        }

        // Bridge server manager alerts & logs to this manager's listeners
        BleMeshServerManager serverManager = BleMeshServerManager.getInstance(mContext);
        serverManager.addListener(new BleMeshServerManager.MeshServerListener() {
            @Override
            public void onServerStateChanged(boolean isAdvertising, boolean isGattServerRunning) {}

            @Override
            public void onClientConnected(BluetoothDevice device) {
                log(TAG, "Client connected to this device: " + device.getName() + " [" + device.getAddress() + "]", LOG_SUCCESS);
            }

            @Override
            public void onClientDisconnected(BluetoothDevice device) {
                log(TAG, "Client disconnected from this device: " + device.getAddress(), LOG_WARN);
            }

            @Override
            public void onDataReceived(BluetoothDevice device, byte[] data) {
                mRxCount++;
                mTotalBytes += data.length;
                notifyStats();
            }

            @Override
            public void onAlertReceived(int alertId, int level, String senderName, String message) {
                handleAlertWithDeduplication(alertId, level, senderName, message);
            }

            @Override
            public void onServerLog(String tag, String message, int level) {
                log(tag, message, level);
            }
        });
    }

    public static synchronized BleMeshManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BleMeshManager(context);
        }
        return sInstance;
    }

    public void addListener(BleMeshListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(BleMeshListener listener) {
        mListeners.remove(listener);
    }

    public boolean isBluetoothEnabled() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    public boolean isScanning() {
        return mIsScanning;
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    public DiscoveredBleDevice getCurrentDevice() {
        return mCurrentDevice;
    }

    public int getCurrentMtu() {
        return mCurrentMtu;
    }

    public int getCurrentRssi() {
        return mCurrentRssi;
    }

    public int getTxCount() {
        return mTxCount;
    }

    public int getRxCount() {
        return mRxCount;
    }

    public long getTotalBytes() {
        return mTotalBytes;
    }

    public int getErrorCount() {
        return mErrorCount;
    }

    public List<DiscoveredBleDevice> getDiscoveredDevicesList() {
        synchronized (mDiscoveredDevices) {
            return new ArrayList<>(mDiscoveredDevices.values());
        }
    }

    public void clearDiscoveredDevices() {
        mDiscoveredDevices.clear();
    }

    public void ensureListening() {
        if (!mIsScanning && isBluetoothEnabled()) {
            startScan(true);
        }
    }

    public void startScan(boolean meshProxyOnly) {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            log(TAG, "Cannot start scan: Bluetooth is disabled", LOG_ERROR);
            return;
        }

        mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (mBleScanner == null) {
            log(TAG, "BluetoothLeScanner is unavailable", LOG_ERROR);
            return;
        }

        if (mIsScanning) {
            try {
                mBleScanner.stopScan(mScanCallback);
            } catch (Exception ignored) {
            }
            mIsScanning = false;
        }

        mDiscoveredDevices.clear();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0)
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        if (meshProxyOnly) {
            filters.add(new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(BleConstants.MESH_PROXY_SERVICE_UUID))
                    .build());
            filters.add(new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(BleConstants.MESH_PROVISIONING_SERVICE_UUID))
                    .build());
            log(TAG, "Scanning for Mesh Proxy (0x1828) nodes & alerts...", LOG_INFO);
        } else {
            log(TAG, "Scanning for all BLE devices...", LOG_INFO);
        }

        try {
            mBleScanner.startScan(filters, settings, mScanCallback);
            mIsScanning = true;
            notifyScanState(true);
        } catch (Exception e) {
            log(TAG, "Failed to start BLE scan: " + e.getMessage(), LOG_ERROR);
        }
    }

    public void stopScan() {
        if (mBleScanner != null && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            try {
                mBleScanner.stopScan(mScanCallback);
            } catch (Exception e) {
                log(TAG, "Error stopping scan: " + e.getMessage(), LOG_WARN);
            }
        }
        mIsScanning = false;
        notifyScanState(false);
        log(TAG, "BLE scan stopped. Found " + mDiscoveredDevices.size() + " devices.", LOG_INFO);
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            String deviceName = (device.getName() != null) ? device.getName() : address;

            // Check if broadcasted packet contains a Mesh Alert Beacon
            ScanRecord record = result.getScanRecord();
            if (record != null) {
                BleMeshServerManager.ParsedAlert alert = null;

                // Check 1: Manufacturer Specific Data
                byte[] mfgData = record.getManufacturerSpecificData(BleConstants.MESH_MANUFACTURER_ID);
                if (mfgData != null) {
                    alert = BleMeshServerManager.parseMeshAlert(mfgData);
                }

                // Check 2: Service Data for 0x1828
                if (alert == null && record.getServiceData() != null) {
                    byte[] svcData = record.getServiceData(new ParcelUuid(BleConstants.MESH_PROXY_SERVICE_UUID));
                    if (svcData != null) {
                        alert = BleMeshServerManager.parseMeshAlert(svcData);
                    }
                }

                // Check 3: Raw scan payload bytes
                if (alert == null && record.getBytes() != null) {
                    alert = BleMeshServerManager.parseMeshAlert(record.getBytes());
                }

                if (alert != null) {
                    handleAlertWithDeduplication(alert.alertId, alert.level, deviceName, alert.message);
                }
            }

            DiscoveredBleDevice existing = mDiscoveredDevices.get(address);
            if (existing == null) {
                DiscoveredBleDevice newDevice = new DiscoveredBleDevice(device, result.getRssi(), result.getScanRecord());
                mDiscoveredDevices.put(address, newDevice);
                // Only log if it is a Mesh Proxy or Provisioning node to avoid log flooding
                if (newDevice.isMeshProxy() || newDevice.isMeshProvisioning()) {
                    log(TAG, "Discovered Mesh Node: " + newDevice.getName() + " [" + address + "]", LOG_INFO);
                }
                notifyScanResult(newDevice);
            } else {
                // Only notify if RSSI changed significantly (>= 4 dBm) to keep UI 60fps smooth
                if (Math.abs(existing.getRssi() - result.getRssi()) >= 4) {
                    existing.update(result.getRssi(), result.getScanRecord());
                    notifyScanResult(existing);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult r : results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                mIsScanning = true;
                notifyScanState(true);
                return;
            }

            mIsScanning = false;
            notifyScanState(false);
            mErrorCount++;
            notifyStats();

            String errorMsg;
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMsg = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED (2) - Try turning Bluetooth off & on";
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    errorMsg = "SCAN_FAILED_INTERNAL_ERROR (3) - Bluetooth controller error";
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorMsg = "SCAN_FAILED_FEATURE_UNSUPPORTED (4) - BLE Scanning not supported";
                    break;
                default:
                    errorMsg = "Error code " + errorCode;
                    break;
            }
            log(TAG, "Scan failed: " + errorMsg, LOG_ERROR);
        }
    };

    public void handleAlertWithDeduplication(int alertId, int level, String sender, String message) {
        // Drop duplicate packets from the same alert burst
        if (alertId != 0 && mProcessedAlertIds.contains(alertId)) {
            return; // Already processed this exact alert!
        }

        if (alertId != 0) {
            mProcessedAlertIds.add(alertId);
            if (mProcessedAlertIds.size() > 150) {
                Integer oldest = mProcessedAlertIds.iterator().next();
                mProcessedAlertIds.remove(oldest);
            }
        }

        log(TAG, String.format("🚨 MESH ALERT RECEIVED! ID: 0x%04X, Level: %d, From: %s, Msg: %s", alertId, level, sender, message), LOG_WARN);
        notifyAlert(alertId, level, sender, message);
    }

    public void connect(DiscoveredBleDevice device) {
        if (device == null || device.getDevice() == null) {
            log(TAG, "Invalid device to connect", LOG_ERROR);
            return;
        }

        if (mIsScanning) {
            stopScan();
        }

        disconnect();

        mCurrentDevice = device;
        setConnectionState(STATE_CONNECTING, "Connecting to " + device.getName() + " [" + device.getAddress() + "]...");
        log(TAG, "Initiating GATT connection to " + device.getName() + "...", LOG_INFO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mBluetoothGatt = device.getDevice().connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            mBluetoothGatt = device.getDevice().connectGatt(mContext, false, mGattCallback);
        }
    }

    public void disconnect() {
        mMainHandler.removeCallbacks(mRssiPollRunnable);
        if (mBluetoothGatt != null) {
            try {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
            } catch (Exception e) {
                log(TAG, "Error closing GATT: " + e.getMessage(), LOG_WARN);
            }
            mBluetoothGatt = null;
        }
        mMeshProxyDataIn = null;
        mMeshProxyDataOut = null;
        mMeshProvDataIn = null;
        mMeshProvDataOut = null;
        setConnectionState(STATE_DISCONNECTED, "Disconnected");
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                String reason = (status == 133) ? "GATT_ERROR (133 - Timeout/Connection dropped)" :
                        ((status == 1) ? "GATT_INVALID_HANDLE (1)" : ("status " + status));
                log(TAG, "GATT connection status error: " + reason, LOG_ERROR);
                mErrorCount++;
                notifyStats();
                setConnectionState(STATE_ERROR, "GATT Error (" + reason + ")");
                disconnect();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log(TAG, "Connected to GATT server. Requesting MTU 517...", LOG_SUCCESS);
                setConnectionState(STATE_REQUESTING_MTU, "Connected. Requesting MTU 517...");
                gatt.requestMtu(BleConstants.DEFAULT_REQUESTED_MTU);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log(TAG, "Disconnected from GATT server", LOG_WARN);
                disconnect();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mCurrentMtu = mtu;
                log(TAG, "Negotiated MTU: " + mtu + " bytes", LOG_SUCCESS);
                notifyMtu(mtu);
            } else {
                log(TAG, "MTU request failed (status " + status + "). Keeping MTU " + mCurrentMtu, LOG_WARN);
            }

            setConnectionState(STATE_DISCOVERING_SERVICES, "Discovering GATT services...");
            gatt.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(TAG, "Service discovery failed with status " + status, LOG_ERROR);
                setConnectionState(STATE_ERROR, "Service discovery failed");
                return;
            }

            log(TAG, "Services discovered. Inspecting Mesh profile services...", LOG_INFO);

            BluetoothGattService proxyService = gatt.getService(BleConstants.MESH_PROXY_SERVICE_UUID);
            BluetoothGattService provService = gatt.getService(BleConstants.MESH_PROVISIONING_SERVICE_UUID);

            if (proxyService != null) {
                log(TAG, "Found Mesh Proxy Service (0x1828)", LOG_SUCCESS);
                mMeshProxyDataIn = proxyService.getCharacteristic(BleConstants.MESH_PROXY_DATA_IN_UUID);
                mMeshProxyDataOut = proxyService.getCharacteristic(BleConstants.MESH_PROXY_DATA_OUT_UUID);
            }

            if (provService != null) {
                log(TAG, "Found Mesh Provisioning Service (0x1827)", LOG_INFO);
                mMeshProvDataIn = provService.getCharacteristic(BleConstants.MESH_PROVISIONING_DATA_IN_UUID);
                mMeshProvDataOut = provService.getCharacteristic(BleConstants.MESH_PROVISIONING_DATA_OUT_UUID);
            }

            BluetoothGattCharacteristic notifyChar = (mMeshProxyDataOut != null) ? mMeshProxyDataOut : mMeshProvDataOut;

            if (notifyChar != null) {
                setConnectionState(STATE_CONFIGURING_CCCD, "Enabling Mesh Data Out notifications...");
                enableNotification(gatt, notifyChar);
            } else {
                log(TAG, "Warning: Mesh Proxy/Provisioning characteristics not found. Connected in generic BLE mode.", LOG_WARN);
                setConnectionState(STATE_READY, "Connected (Generic BLE GATT)");
                startRssiPolling();
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log(TAG, "Mesh Data Out notifications subscribed successfully!", LOG_SUCCESS);
                setConnectionState(STATE_READY, "Connected & Ready (Mesh Proxy)");
                startRssiPolling();
            } else {
                log(TAG, "Failed to write CCCD descriptor, status: " + status, LOG_ERROR);
                mErrorCount++;
                notifyStats();
                setConnectionState(STATE_READY, "Connected (Notification setup failed)");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            handleCharacteristicData(characteristic.getUuid().toString(), value);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            handleCharacteristicData(characteristic.getUuid().toString(), value);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mCurrentRssi = rssi;
                String quality = (mCurrentDevice != null) ? mCurrentDevice.getSignalQuality() : "Good";
                if (mCurrentDevice != null) {
                    mCurrentDevice.update(rssi, mCurrentDevice.getScanRecord());
                }
                notifyRssi(rssi, quality);
            }
        }

        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log(TAG, "PHY updated: TX=" + txPhy + ", RX=" + rxPhy, LOG_INFO);
                notifyPhy(txPhy, rxPhy);
            }
        }
    };

    private void enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                gatt.writeDescriptor(descriptor);
            }
        } else {
            log(TAG, "CCCD descriptor not found for characteristic " + characteristic.getUuid(), LOG_WARN);
            setConnectionState(STATE_READY, "Connected & Ready");
            startRssiPolling();
        }
    }

    private void handleCharacteristicData(String uuid, byte[] data) {
        if (data == null || data.length == 0) return;
        mRxCount++;
        mTotalBytes += data.length;
        notifyStats();

        // Check if incoming packet is an Alert
        BleMeshServerManager.ParsedAlert alert = BleMeshServerManager.parseMeshAlert(data);
        if (alert != null) {
            String sender = (mCurrentDevice != null) ? mCurrentDevice.getName() : "Mesh Node";
            handleAlertWithDeduplication(alert.alertId, alert.level, sender, alert.message);

            // Acknowledge back over GATT
            byte[] ack = new byte[]{
                    BleConstants.OPCODE_MESH_ALERT_ACK,
                    (byte) ((alert.alertId >> 8) & 0xFF),
                    (byte) (alert.alertId & 0xFF)
            };
            sendMeshPacket(ack, BleConstants.PDU_TYPE_NETWORK, "ACK response");
        } else if (data.length >= 2 && (data[0] == BleConstants.OPCODE_MESH_ALERT_ACK || data[1] == BleConstants.OPCODE_MESH_ALERT_ACK)) {
            log(TAG, "✅ Remote node confirmed receipt of the alert (ACK received)!", LOG_SUCCESS);
        }

        MeshPacket packet = new MeshPacket(MeshPacket.Direction.RX, data, "Mesh RX from " + uuid);
        log(TAG, String.format("RX [%s | %s] %s (%d bytes)", packet.getPduTypeName(), packet.getSarTypeName(), packet.getHexDump(), data.length), LOG_RX);
        notifyPacketReceived(packet);
    }

    private void startRssiPolling() {
        mMainHandler.removeCallbacks(mRssiPollRunnable);
        mMainHandler.post(mRssiPollRunnable);
    }

    public boolean sendMeshPacket(byte[] pdu, byte pduType, String description) {
        byte header = (byte) (BleConstants.SAR_COMPLETE | (pduType & BleConstants.PDU_TYPE_MASK));
        byte[] fullPacket = new byte[1 + (pdu != null ? pdu.length : 0)];
        fullPacket[0] = header;
        if (pdu != null && pdu.length > 0) {
            System.arraycopy(pdu, 0, fullPacket, 1, pdu.length);
        }

        boolean sentViaGattClient = false;
        boolean sentViaGattServer = false;

        // 1. If connected as GATT Client (to remote Server)
        if (mBluetoothGatt != null && mConnectionState == STATE_READY) {
            BluetoothGattCharacteristic targetChar = (mMeshProxyDataIn != null) ? mMeshProxyDataIn : mMeshProvDataIn;
            if (targetChar != null) {
                sentViaGattClient = writeCharacteristicData(targetChar, fullPacket);
            }
        }

        // 2. If running as GATT Server (with connected Clients)
        BleMeshServerManager serverManager = BleMeshServerManager.getInstance(mContext);
        if (serverManager.isGattServerRunning() && !serverManager.getConnectedClients().isEmpty()) {
            sentViaGattServer = serverManager.sendToConnectedClients(fullPacket);
        }

        boolean success = sentViaGattClient || sentViaGattServer;
        if (success) {
            mTxCount++;
            mTotalBytes += fullPacket.length;
            notifyStats();
            MeshPacket packet = new MeshPacket(MeshPacket.Direction.TX, fullPacket, description != null ? description : "Mesh TX PDU");
            log(TAG, String.format("TX [%s] %s (%d bytes) [Client: %s, Server: %s]",
                    packet.getPduTypeName(), packet.getHexDump(), fullPacket.length,
                    sentViaGattClient ? "YES" : "NO", sentViaGattServer ? "YES" : "NO"), LOG_TX);
            notifyPacketTransmitted(packet);
        } else {
            mErrorCount++;
            notifyStats();
            log(TAG, "No active GATT connection (Broadcasting via connectionless Mesh air packets)", LOG_INFO);
        }
        return success;
    }

    public boolean sendMeshAlert(int alertLevel, String message) {
        // Generate unique 16-bit Alert ID
        int alertId = new Random().nextInt(0x7FFF) + 1;

        byte[] msgBytes = (message != null) ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
        // Payload: [0xA1, ID_HI, ID_LO, LEVEL, MSG...]
        byte[] pdu = new byte[4 + msgBytes.length];
        pdu[0] = BleConstants.OPCODE_MESH_ALERT;
        pdu[1] = (byte) ((alertId >> 8) & 0xFF);
        pdu[2] = (byte) (alertId & 0xFF);
        pdu[3] = (byte) alertLevel;
        System.arraycopy(msgBytes, 0, pdu, 4, msgBytes.length);

        // Mark our own alert as processed so we don't echo our own broadcast
        mProcessedAlertIds.add(alertId);

        String levelStr = (alertLevel == BleConstants.ALERT_LEVEL_EMERGENCY) ? "EMERGENCY" : ((alertLevel == BleConstants.ALERT_LEVEL_WARN) ? "WARNING" : "INFO");
        String desc = String.format("🚨 MESH ALERT (ID: 0x%04X, %s): %s", alertId, levelStr, message);

        // 1. Send via GATT if any client or server connection is active
        boolean gattSent = sendMeshPacket(pdu, BleConstants.PDU_TYPE_NETWORK, desc);

        // 2. Broadcast as connectionless BLE Mesh Beacon into the air (NO CONNECTION REQUIRED)
        boolean beaconSent = BleMeshServerManager.getInstance(mContext).broadcastAlertBeacon(alertId, alertLevel, message);

        log(TAG, String.format("🚨 Sent Mesh Alert (ID: 0x%04X, %s): %s [GATT: %s, Broadcast: %s]",
                alertId, levelStr, message, gattSent ? "YES" : "NO", beaconSent ? "YES" : "NO"), LOG_TX);

        return gattSent || beaconSent;
    }

    public boolean sendProxyConfigSetFilter(byte filterType) {
        byte[] payload = new byte[]{
                BleConstants.PROXY_CONFIG_SET_FILTER_TYPE,
                filterType
        };
        return sendMeshPacket(payload, BleConstants.PDU_TYPE_PROXY_CONFIGURATION, "Proxy Config: Set Filter Type (" + (filterType == 0 ? "Whitelist" : "Blacklist") + ")");
    }

    public boolean sendProxyConfigAddAddress(int address) {
        byte[] payload = new byte[]{
                BleConstants.PROXY_CONFIG_ADD_ADDRESSES,
                (byte) ((address >> 8) & 0xFF),
                (byte) (address & 0xFF)
        };
        return sendMeshPacket(payload, BleConstants.PDU_TYPE_PROXY_CONFIGURATION, String.format("Proxy Config: Add Address 0x%04X", address));
    }

    public boolean sendMeshPing(int srcAddress, int dstAddress) {
        byte[] networkPdu = new byte[]{
                0x00, // IVI & NID
                0x07, // CTL (0) & TTL (7)
                0x00, 0x00, 0x01, // Sequence Number
                (byte) ((srcAddress >> 8) & 0xFF), (byte) (srcAddress & 0xFF), // SRC Address
                (byte) ((dstAddress >> 8) & 0xFF), (byte) (dstAddress & 0xFF), // DST Address
                0x01, 0x02, 0x03, 0x04 // Transport Payload / Ping Tag
        };
        return sendMeshPacket(networkPdu, BleConstants.PDU_TYPE_NETWORK, String.format("Mesh Ping (SRC: 0x%04X -> DST: 0x%04X)", srcAddress, dstAddress));
    }

    public boolean sendCustomHexPayload(String hexString) {
        try {
            String clean = hexString.replaceAll("\\s+", "").replaceAll(":", "");
            if (clean.length() % 2 != 0) {
                log(TAG, "Hex payload must have an even number of digits", LOG_ERROR);
                return false;
            }
            byte[] bytes = new byte[clean.length() / 2];
            for (int i = 0; i < clean.length(); i += 2) {
                bytes[i / 2] = (byte) ((Character.digit(clean.charAt(i), 16) << 4) + Character.digit(clean.charAt(i + 1), 16));
            }
            return sendMeshPacket(bytes, BleConstants.PDU_TYPE_NETWORK, "Custom Hex Payload (" + bytes.length + " bytes)");
        } catch (Exception e) {
            log(TAG, "Error parsing hex payload: " + e.getMessage(), LOG_ERROR);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean writeCharacteristicData(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (mBluetoothGatt == null) return false;
        try {
            int writeType = ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
            characteristic.setWriteType(writeType);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int res = mBluetoothGatt.writeCharacteristic(characteristic, data, writeType);
                return res == BluetoothGatt.GATT_SUCCESS;
            } else {
                characteristic.setValue(data);
                return mBluetoothGatt.writeCharacteristic(characteristic);
            }
        } catch (Exception e) {
            log(TAG, "Exception writing characteristic: " + e.getMessage(), LOG_ERROR);
            return false;
        }
    }

    private void setConnectionState(int state, String message) {
        mConnectionState = state;
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onConnectionStateChanged(state, message, mCurrentDevice);
            }
        });
    }

    private void notifyScanState(boolean scanning) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onScanStateChanged(scanning);
            }
        });
    }

    private void notifyScanResult(DiscoveredBleDevice device) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onScanResult(device);
            }
        });
    }

    private void notifyRssi(int rssi, String quality) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onRssiUpdated(rssi, quality);
            }
        });
    }

    private void notifyMtu(int mtu) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onMtuUpdated(mtu);
            }
        });
    }

    private void notifyPhy(int txPhy, int rxPhy) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onPhyUpdated(txPhy, rxPhy);
            }
        });
    }

    private void notifyPacketTransmitted(MeshPacket packet) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onPacketTransmitted(packet);
            }
        });
    }

    private void notifyPacketReceived(MeshPacket packet) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onPacketReceived(packet);
            }
        });
    }

    private void notifyAlert(int alertId, int level, String sender, String message) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onAlertReceived(alertId, level, sender, message);
            }
        });
    }

    private void notifyStats() {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onStatisticsUpdated(mTxCount, mRxCount, mTotalBytes, mErrorCount);
            }
        });
    }

    private void log(String tag, String message, int level) {
        mMainHandler.post(() -> {
            for (BleMeshListener l : mListeners) {
                l.onLog(tag, message, level);
            }
        });
    }
}
