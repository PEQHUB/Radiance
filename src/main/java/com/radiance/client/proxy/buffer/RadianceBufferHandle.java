package com.radiance.client.proxy.buffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Radiance-owned buffer descriptor. Replaces every JNI reference to MC's BuiltBuffer.
 * Serializes to a fixed 40-byte direct ByteBuffer (LittleEndian) per PRD §4.4.
 *
 * Layout (offsets in bytes):
 *   0  (4): vertexCount         int32 LE
 *   4  (4): indexCount          int32 LE
 *   8  (4): vertexFormatOrdinal int32 LE
 *  12  (4): indexTypeOrdinal    int32 LE
 *  16  (4): drawModeOrdinal     int32 LE
 *  20  (4): hasData             int32 LE (0 or 1)
 *  24  (8): centroidArrayPtr    uint64 LE
 *  32  (4): centroidArrayLen    int32 LE
 *  36  (4): pad                 (zero)
 */
public final class RadianceBufferHandle {

    public static final int LAYOUT_SIZE_BYTES = 40;

    public final int vertexCount;
    public final int indexCount;
    public final int vertexFormatOrdinal;
    public final int indexTypeOrdinal;
    public final int drawModeOrdinal;
    public final boolean hasData;
    public final long centroidArrayPtr;
    public final int centroidArrayLen;

    public RadianceBufferHandle(int vertexCount, int indexCount, int vertexFormatOrdinal,
                                int indexTypeOrdinal, int drawModeOrdinal, boolean hasData,
                                long centroidArrayPtr, int centroidArrayLen) {
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.vertexFormatOrdinal = vertexFormatOrdinal;
        this.indexTypeOrdinal = indexTypeOrdinal;
        this.drawModeOrdinal = drawModeOrdinal;
        this.hasData = hasData;
        this.centroidArrayPtr = centroidArrayPtr;
        this.centroidArrayLen = centroidArrayLen;
    }

    public ByteBuffer toByteBuffer() {
        ByteBuffer buf = ByteBuffer.allocateDirect(LAYOUT_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0, vertexCount);
        buf.putInt(4, indexCount);
        buf.putInt(8, vertexFormatOrdinal);
        buf.putInt(12, indexTypeOrdinal);
        buf.putInt(16, drawModeOrdinal);
        buf.putInt(20, hasData ? 1 : 0);
        buf.putLong(24, centroidArrayPtr);
        buf.putInt(32, centroidArrayLen);
        buf.putInt(36, 0); // pad
        buf.position(0).limit(LAYOUT_SIZE_BYTES);
        return buf;
    }

    public static RadianceBufferHandle fromByteBuffer(ByteBuffer buf) {
        ByteBuffer view = buf.order() == ByteOrder.LITTLE_ENDIAN
            ? buf
            : buf.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        return new RadianceBufferHandle(
            view.getInt(0),
            view.getInt(4),
            view.getInt(8),
            view.getInt(12),
            view.getInt(16),
            view.getInt(20) != 0,
            view.getLong(24),
            view.getInt(32));
    }
}
