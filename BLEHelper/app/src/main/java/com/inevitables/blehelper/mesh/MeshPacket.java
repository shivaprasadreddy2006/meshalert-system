package com.inevitables.blehelper.mesh;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MeshPacket {
    public enum Direction {
        TX, RX
    }

    private final Direction direction;
    private final byte[] rawData;
    private final long timestamp;
    private final byte pduType;
    private final byte sarType;
    private final String description;

    public MeshPacket(Direction direction, byte[] rawData, String description) {
        this.direction = direction;
        this.rawData = rawData != null ? rawData.clone() : new byte[0];
        this.timestamp = System.currentTimeMillis();
        this.description = description;

        if (this.rawData.length > 0) {
            byte header = this.rawData[0];
            this.sarType = (byte) (header & BleConstants.SAR_MASK);
            this.pduType = (byte) (header & BleConstants.PDU_TYPE_MASK);
        } else {
            this.sarType = BleConstants.SAR_COMPLETE;
            this.pduType = BleConstants.PDU_TYPE_NETWORK;
        }
    }

    public Direction getDirection() {
        return direction;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte getPduType() {
        return pduType;
    }

    public byte getSarType() {
        return sarType;
    }

    public String getDescription() {
        return description;
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getPduTypeName() {
        switch (pduType) {
            case BleConstants.PDU_TYPE_NETWORK:
                return "Network PDU";
            case BleConstants.PDU_TYPE_MESH_BEACON:
                return "Mesh Beacon";
            case BleConstants.PDU_TYPE_PROXY_CONFIGURATION:
                return "Proxy Config";
            case BleConstants.PDU_TYPE_PROVISIONING:
                return "Provisioning PDU";
            default:
                return String.format(Locale.getDefault(), "Unknown (0x%02X)", pduType);
        }
    }

    public String getSarTypeName() {
        switch (sarType) {
            case BleConstants.SAR_COMPLETE:
                return "Complete";
            case BleConstants.SAR_FIRST:
                return "First Segment";
            case BleConstants.SAR_CONTINUATION:
                return "Continuation";
            case BleConstants.SAR_LAST:
                return "Last Segment";
            default:
                return "Unknown SAR";
        }
    }

    public String getHexDump() {
        StringBuilder sb = new StringBuilder();
        for (byte b : rawData) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
