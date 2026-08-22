package com.inevitables.blehelper.mesh;

import java.util.UUID;

public final class BleConstants {
    private BleConstants() {}

    // Bluetooth SIG Assigned UUIDs for Mesh Profile
    public static final UUID MESH_PROXY_SERVICE_UUID = UUID.fromString("00001828-0000-1000-8000-00805f9b34fb");
    public static final UUID MESH_PROXY_DATA_IN_UUID = UUID.fromString("00002ade-0000-1000-8000-00805f9b34fb");
    public static final UUID MESH_PROXY_DATA_OUT_UUID = UUID.fromString("00002adf-0000-1000-8000-00805f9b34fb");

    public static final UUID MESH_PROVISIONING_SERVICE_UUID = UUID.fromString("00001827-0000-1000-8000-00805f9b34fb");
    public static final UUID MESH_PROVISIONING_DATA_IN_UUID = UUID.fromString("00002adb-0000-1000-8000-00805f9b34fb");
    public static final UUID MESH_PROVISIONING_DATA_OUT_UUID = UUID.fromString("00002adc-0000-1000-8000-00805f9b34fb");

    // CCCD Descriptor for Notifications
    public static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Mesh Proxy PDU SAR Masks
    public static final byte SAR_COMPLETE = 0x00;
    public static final byte SAR_FIRST = (byte) 0x40;
    public static final byte SAR_CONTINUATION = (byte) 0x80;
    public static final byte SAR_LAST = (byte) 0xC0;
    public static final byte SAR_MASK = (byte) 0xC0;
    public static final byte PDU_TYPE_MASK = 0x3F;

    // Mesh Proxy PDU Types
    public static final byte PDU_TYPE_NETWORK = 0x00;
    public static final byte PDU_TYPE_MESH_BEACON = 0x01;
    public static final byte PDU_TYPE_PROXY_CONFIGURATION = 0x02;
    public static final byte PDU_TYPE_PROVISIONING = 0x03;

    // Proxy Configuration Opcodes
    public static final byte PROXY_CONFIG_SET_FILTER_TYPE = 0x00;
    public static final byte PROXY_CONFIG_FILTER_STATUS = 0x01;
    public static final byte PROXY_CONFIG_ADD_ADDRESSES = 0x02;
    public static final byte PROXY_CONFIG_REMOVE_ADDRESSES = 0x03;

    // Filter Types
    public static final byte FILTER_TYPE_WHITELIST = 0x00;
    public static final byte FILTER_TYPE_BLACKLIST = 0x01;

    // Mesh Alert Protocol Opcode & Levels
    public static final byte OPCODE_MESH_ALERT = (byte) 0xA1;
    public static final byte OPCODE_MESH_ALERT_ACK = (byte) 0xA2;
    public static final byte ALERT_LEVEL_INFO = 0x01;
    public static final byte ALERT_LEVEL_WARN = 0x02;
    public static final byte ALERT_LEVEL_EMERGENCY = 0x03;

    // Custom Mesh Manufacturer ID (e.g. 0x05E3 for test mesh)
    public static final int MESH_MANUFACTURER_ID = 0x05E3;

    // Connection Parameters
    public static final int DEFAULT_REQUESTED_MTU = 517;
    public static final int RSSI_POLL_INTERVAL_MS = 2500;
    public static final int SCAN_TIMEOUT_MS = 30000;
}
