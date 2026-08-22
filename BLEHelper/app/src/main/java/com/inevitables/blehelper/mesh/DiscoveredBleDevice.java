package com.inevitables.blehelper.mesh;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.os.ParcelUuid;

import java.util.List;
import java.util.Objects;

public class DiscoveredBleDevice {
    private final BluetoothDevice device;
    private int rssi;
    private ScanRecord scanRecord;
    private long lastSeenTimestamp;
    private boolean isMeshProxy;
    private boolean isMeshProvisioning;
    private String customName;

    public DiscoveredBleDevice(BluetoothDevice device, int rssi, ScanRecord scanRecord) {
        this.device = device;
        this.rssi = rssi;
        this.scanRecord = scanRecord;
        this.lastSeenTimestamp = System.currentTimeMillis();
        evaluateMeshCapabilities();
    }

    public void update(int rssi, ScanRecord scanRecord) {
        this.rssi = rssi;
        this.scanRecord = scanRecord;
        this.lastSeenTimestamp = System.currentTimeMillis();
        evaluateMeshCapabilities();
    }

    private void evaluateMeshCapabilities() {
        this.isMeshProxy = false;
        this.isMeshProvisioning = false;

        if (scanRecord != null) {
            List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
            if (serviceUuids != null) {
                for (ParcelUuid uuid : serviceUuids) {
                    if (uuid != null) {
                        if (BleConstants.MESH_PROXY_SERVICE_UUID.equals(uuid.getUuid())) {
                            this.isMeshProxy = true;
                        }
                        if (BleConstants.MESH_PROVISIONING_SERVICE_UUID.equals(uuid.getUuid())) {
                            this.isMeshProvisioning = true;
                        }
                    }
                }
            }

            // Also check service data map or manufacturer data
            if (scanRecord.getServiceData() != null) {
                for (ParcelUuid uuid : scanRecord.getServiceData().keySet()) {
                    if (BleConstants.MESH_PROXY_SERVICE_UUID.equals(uuid.getUuid())) {
                        this.isMeshProxy = true;
                    }
                    if (BleConstants.MESH_PROVISIONING_SERVICE_UUID.equals(uuid.getUuid())) {
                        this.isMeshProvisioning = true;
                    }
                }
            }
        }
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public String getAddress() {
        return device != null ? device.getAddress() : "Unknown Address";
    }

    public String getName() {
        try {
            if (customName != null && !customName.isEmpty()) return customName;
            if (scanRecord != null && scanRecord.getDeviceName() != null && !scanRecord.getDeviceName().isEmpty()) {
                return scanRecord.getDeviceName();
            }
            if (device != null && device.getName() != null && !device.getName().isEmpty()) {
                return device.getName();
            }
        } catch (SecurityException ignored) {
        }
        return "Unknown BLE Device";
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    public int getRssi() {
        return rssi;
    }

    public ScanRecord getScanRecord() {
        return scanRecord;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public boolean isMeshProxy() {
        return isMeshProxy;
    }

    public boolean isMeshProvisioning() {
        return isMeshProvisioning;
    }

    public String getSignalQuality() {
        if (rssi >= -60) return "Excellent";
        if (rssi >= -75) return "Good";
        if (rssi >= -85) return "Fair";
        return "Poor";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveredBleDevice that = (DiscoveredBleDevice) o;
        return Objects.equals(getAddress(), that.getAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAddress());
    }
}
