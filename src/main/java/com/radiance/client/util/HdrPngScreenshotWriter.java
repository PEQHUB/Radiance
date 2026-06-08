package com.radiance.client.util;

import com.radiance.client.option.Options;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class HdrPngScreenshotWriter {

    private static final byte[] PNG_SIGNATURE = new byte[]{
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final int VK_FORMAT_A2R10G10B10_UNORM_PACK32 = 58;
    private static final int VK_FORMAT_A2B10G10R10_UNORM_PACK32 = 64;

    private HdrPngScreenshotWriter() {
    }

    public static void writeHdrCopy(Path outputPngPath, int width, int height, int vkFormat,
        byte[] packedPixels) throws IOException {
        String fileName = outputPngPath.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(".png")) {
            return;
        }

        if (width <= 0 || height <= 0) {
            throw new IOException("Invalid HDR screenshot dimensions");
        }

        long expectedSize = (long) width * height * 4L;
        if (expectedSize > Integer.MAX_VALUE || packedPixels == null
            || packedPixels.length != (int) expectedSize) {
            throw new IOException("Invalid packed HDR screenshot payload");
        }

        if (vkFormat != VK_FORMAT_A2B10G10R10_UNORM_PACK32
            && vkFormat != VK_FORMAT_A2R10G10B10_UNORM_PACK32) {
            throw new IOException("Unsupported HDR screenshot format: " + vkFormat);
        }

        byte[] png = buildHdrPng(width, height, vkFormat, packedPixels);
        Files.write(outputPngPath, png);
    }

    private static byte[] buildHdrPng(int width, int height, int vkFormat, byte[] packedPixels)
        throws IOException {
        byte[] scanlines = buildRgba16Scanlines(width, height, vkFormat, packedPixels);
        byte[] idatPayload = compress(scanlines);

        ByteArrayOutputStream out = new ByteArrayOutputStream(idatPayload.length + 512);
        out.write(PNG_SIGNATURE);
        writeChunk(out, "IHDR", buildIhdrChunk(width, height));
        writeChunk(out, "cICP", buildCicpChunk());
        writeChunk(out, "mDCV", buildMdcvChunk());
        writeChunk(out, "cLLI", buildClliChunk());
        writeChunk(out, "IDAT", idatPayload);
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] buildIhdrChunk(int width, int height) {
        ByteBuffer bb = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
        bb.putInt(width);
        bb.putInt(height);
        bb.put((byte) 16); // bit depth
        bb.put((byte) 6);  // color type RGBA
        bb.put((byte) 0);  // compression
        bb.put((byte) 0);  // filter
        bb.put((byte) 0);  // interlace
        return bb.array();
    }

    private static byte[] compress(byte[] src) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(src.length / 2);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(out,
            new Deflater(Deflater.DEFAULT_COMPRESSION, false))) {
            dos.write(src);
        }
        return out.toByteArray();
    }

    private static byte[] buildRgba16Scanlines(int width, int height, int vkFormat,
        byte[] packedPixels) {
        int rowStride = 1 + width * 8;
        byte[] scanlines = new byte[rowStride * height];

        ByteBuffer src = ByteBuffer.wrap(packedPixels).order(ByteOrder.LITTLE_ENDIAN);
        int dstOff = 0;

        for (int y = 0; y < height; y++) {
            scanlines[dstOff++] = 0;
            for (int x = 0; x < width; x++) {
                int p = src.getInt();

                int r10;
                int g10;
                int b10;
                int a2 = (p >>> 30) & 0x03;

                if (vkFormat == VK_FORMAT_A2B10G10R10_UNORM_PACK32) {
                    r10 = (p) & 0x3FF;
                    g10 = (p >>> 10) & 0x3FF;
                    b10 = (p >>> 20) & 0x3FF;
                } else {
                    b10 = (p) & 0x3FF;
                    g10 = (p >>> 10) & 0x3FF;
                    r10 = (p >>> 20) & 0x3FF;
                }

                int r16 = scale10To16(r10);
                int g16 = scale10To16(g10);
                int b16 = scale10To16(b10);
                int a16 = scale2To16(a2);

                putU16BE(scanlines, dstOff, r16);
                dstOff += 2;
                putU16BE(scanlines, dstOff, g16);
                dstOff += 2;
                putU16BE(scanlines, dstOff, b16);
                dstOff += 2;
                putU16BE(scanlines, dstOff, a16);
                dstOff += 2;
            }
        }

        return scanlines;
    }

    private static int scale10To16(int v10) {
        return (v10 * 65535 + 511) / 1023;
    }

    private static int scale2To16(int v2) {
        return (v2 * 65535 + 1) / 3;
    }

    private static void putU16BE(byte[] dst, int off, int value) {
        dst[off] = (byte) ((value >>> 8) & 0xFF);
        dst[off + 1] = (byte) (value & 0xFF);
    }

    private static byte[] buildCicpChunk() {
        return new byte[]{9, 16, 0, 1};
    }

    private static byte[] buildMdcvChunk() {
        ByteBuffer bb = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN);

        bb.putShort((short) 35400); // Rx 0.708
        bb.putShort((short) 14600); // Ry 0.292
        bb.putShort((short) 8500);  // Gx 0.170
        bb.putShort((short) 39850); // Gy 0.797
        bb.putShort((short) 6550);  // Bx 0.131
        bb.putShort((short) 2300);  // By 0.046
        bb.putShort((short) 15635); // Wx 0.3127
        bb.putShort((short) 16450); // Wy 0.3290

        int peak = Math.max(1, Options.hdrPeakNits);
        bb.putInt(peak * 10000);
        bb.putInt(1); // 0.0001 nits

        return bb.array();
    }

    private static byte[] buildClliChunk() {
        ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        int maxCll = Math.max(1, Options.hdrPeakNits) * 10000;
        int maxFallNits = Math.max(1, Math.min(Options.hdrPeakNits, Options.hdrPaperWhiteNits));
        int maxFall = maxFallNits * 10000;
        bb.putInt(maxCll);
        bb.putInt(maxFall);
        return bb.array();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data)
        throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeIntBE(out, data.length);
        out.write(typeBytes);
        out.write(data);

        CRC32 crc32 = new CRC32();
        crc32.update(typeBytes);
        crc32.update(data);
        writeIntBE(out, (int) crc32.getValue());
    }

    private static void writeIntBE(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
