package com.radiance.client.proxy.buffer;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadianceBufferHandleTest {

    @Test
    void byteBufferLayoutIs40BytesLittleEndian() {
        RadianceBufferHandle h = new RadianceBufferHandle(
            42, 84, 7, 1, 4, true, 0xCAFEBABEL, 16);
        ByteBuffer buf = h.toByteBuffer();
        assertEquals(40, buf.remaining());
        assertEquals(java.nio.ByteOrder.LITTLE_ENDIAN, buf.order());
    }

    @Test
    void roundTripPreservesAllFields() {
        RadianceBufferHandle original = new RadianceBufferHandle(
            42, 84, 7, 1, 4, true, 0xCAFEBABEL, 16);
        ByteBuffer buf = original.toByteBuffer();
        RadianceBufferHandle decoded = RadianceBufferHandle.fromByteBuffer(buf);

        assertEquals(original.vertexCount, decoded.vertexCount);
        assertEquals(original.indexCount, decoded.indexCount);
        assertEquals(original.vertexFormatOrdinal, decoded.vertexFormatOrdinal);
        assertEquals(original.indexTypeOrdinal, decoded.indexTypeOrdinal);
        assertEquals(original.drawModeOrdinal, decoded.drawModeOrdinal);
        assertEquals(original.hasData, decoded.hasData);
        assertEquals(original.centroidArrayPtr, decoded.centroidArrayPtr);
        assertEquals(original.centroidArrayLen, decoded.centroidArrayLen);
    }

    @Test
    void roundTripWithFalseHasData() {
        RadianceBufferHandle original = new RadianceBufferHandle(
            0, 0, 0, 0, 0, false, 0L, 0);
        RadianceBufferHandle decoded = RadianceBufferHandle.fromByteBuffer(original.toByteBuffer());
        assertEquals(false, decoded.hasData);
    }

    @Test
    void byteBufferIsDirectAllocated() {
        RadianceBufferHandle h = new RadianceBufferHandle(
            1, 2, 3, 4, 5, true, 6L, 7);
        ByteBuffer buf = h.toByteBuffer();
        assertTrue(buf.isDirect(), "ByteBuffer must be direct for JNI consumption");
    }
}
