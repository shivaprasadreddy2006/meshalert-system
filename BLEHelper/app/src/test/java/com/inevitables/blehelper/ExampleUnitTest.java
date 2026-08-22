package com.inevitables.blehelper;

import com.inevitables.blehelper.mesh.BleConstants;
import com.inevitables.blehelper.mesh.MeshPacket;

import org.junit.Test;

import static org.junit.Assert.*;

public class ExampleUnitTest {

    @Test
    public void testMeshPacketParsingNetworkPdu() {
        // Complete Network PDU: SAR=0x00, PDU_TYPE=0x00 (Network PDU)
        byte[] raw = new byte[]{0x00, 0x01, 0x02, (byte) 0xAA, (byte) 0xBB};
        MeshPacket packet = new MeshPacket(MeshPacket.Direction.RX, raw, "Test Packet");

        assertEquals(MeshPacket.Direction.RX, packet.getDirection());
        assertEquals(BleConstants.SAR_COMPLETE, packet.getSarType());
        assertEquals(BleConstants.PDU_TYPE_NETWORK, packet.getPduType());
        assertEquals("Network PDU", packet.getPduTypeName());
        assertEquals("Complete", packet.getSarTypeName());
        assertEquals("00 01 02 AA BB", packet.getHexDump());
        assertNotNull(packet.getFormattedTime());
    }

    @Test
    public void testMeshPacketProxyConfig() {
        // SAR=0x00 (Complete), PDU_TYPE=0x02 (Proxy Config), Opcode=0x00 (Set Filter Type), FilterType=0x00 (Whitelist)
        byte[] raw = new byte[]{0x02, 0x00, 0x00};
        MeshPacket packet = new MeshPacket(MeshPacket.Direction.TX, raw, "Proxy Filter Set");

        assertEquals(MeshPacket.Direction.TX, packet.getDirection());
        assertEquals(BleConstants.SAR_COMPLETE, packet.getSarType());
        assertEquals(BleConstants.PDU_TYPE_PROXY_CONFIGURATION, packet.getPduType());
        assertEquals("Proxy Config", packet.getPduTypeName());
        assertEquals("02 00 00", packet.getHexDump());
    }

    @Test
    public void testMeshPacketSarSegments() {
        // SAR=0x40 (First Segment), PDU_TYPE=0x00
        byte[] firstSeg = new byte[]{(byte) 0x40, 0x11, 0x22};
        MeshPacket packet1 = new MeshPacket(MeshPacket.Direction.RX, firstSeg, "First Seg");
        assertEquals(BleConstants.SAR_FIRST, packet1.getSarType());
        assertEquals("First Segment", packet1.getSarTypeName());

        // SAR=0x80 (Continuation Segment), PDU_TYPE=0x00
        byte[] contSeg = new byte[]{(byte) 0x80, 0x33, 0x44};
        MeshPacket packet2 = new MeshPacket(MeshPacket.Direction.RX, contSeg, "Cont Seg");
        assertEquals(BleConstants.SAR_CONTINUATION, packet2.getSarType());
        assertEquals("Continuation", packet2.getSarTypeName());

        // SAR=0xC0 (Last Segment), PDU_TYPE=0x00
        byte[] lastSeg = new byte[]{(byte) 0xC0, 0x55, 0x66};
        MeshPacket packet3 = new MeshPacket(MeshPacket.Direction.RX, lastSeg, "Last Seg");
        assertEquals(BleConstants.SAR_LAST, packet3.getSarType());
        assertEquals("Last Segment", packet3.getSarTypeName());
    }

    @Test
    public void testMeshUuids() {
        assertNotNull(BleConstants.MESH_PROXY_SERVICE_UUID);
        assertNotNull(BleConstants.MESH_PROXY_DATA_IN_UUID);
        assertNotNull(BleConstants.MESH_PROXY_DATA_OUT_UUID);
        assertNotNull(BleConstants.MESH_PROVISIONING_SERVICE_UUID);
    }
}