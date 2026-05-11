package com.radiance.client.constant;

// PBRVertexFormats deferred during 1.20.1 backport — see
// src/deferred/java/com/radiance/client/vertex/PBRVertexFormats.java.
// Re-import once the format is rewritten against 1.20.1's VertexFormat shape.
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;

public class Constants {
    private static final long ORDINAL_TABLE_MAGIC = 0x5241445f4f524453L; // RAD_ORDS
    private static final long ORDINAL_TABLE_VERSION = 1L;
    private static final long ORDINAL_TABLE_SECTION_COUNT = 5L;

    private static final long SECTION_VERTEX_FORMATS = 1L;
    private static final long SECTION_DRAW_MODES = 2L;
    private static final long SECTION_INDEX_TYPES = 3L;
    private static final long SECTION_GEOMETRY_TYPES = 4L;
    private static final long SECTION_RAY_TRACING_FLAGS = 5L;

    private static final long ENTRY_ACTIVE = 0L;
    private static final long ENTRY_RESERVED = 1L;

    private static final long[] RESERVED_VERTEX_FORMAT_ORDINALS = {10L, 11L, 12L};

    public enum IndexTypes {
        SHORT(VertexFormat.IndexType.SHORT, 0),
        INT(VertexFormat.IndexType.INT, 1);

        private static final Map<VertexFormat.IndexType, Integer>
            BY_INDEX_TYPE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(IndexTypes::getIndexType, IndexTypes::getValue)));

        private final VertexFormat.IndexType indexType;
        private final int value;

        IndexTypes(VertexFormat.IndexType indexType, int value) {
            this.indexType = indexType;
            this.value = value;
        }

        public static int getValue(VertexFormat.IndexType indexType) {
            return BY_INDEX_TYPE.get(indexType);
        }

        public VertexFormat.IndexType getIndexType() {
            return indexType;
        }

        public int getValue() {
            return value;
        }
    }

    public enum DrawModes {
        LINES(VertexFormat.DrawMode.LINES, 0),
        LINE_STRIP(VertexFormat.DrawMode.LINE_STRIP, 1),
        DEBUG_LINES(VertexFormat.DrawMode.DEBUG_LINES, 2),
        DEBUG_LINE_STRIP(VertexFormat.DrawMode.DEBUG_LINE_STRIP, 3),
        TRIANGLES(VertexFormat.DrawMode.TRIANGLES, 4),
        TRIANGLE_STRIP(VertexFormat.DrawMode.TRIANGLE_STRIP, 5),
        TRIANGLE_FAN(VertexFormat.DrawMode.TRIANGLE_FAN, 6),
        QUADS(VertexFormat.DrawMode.QUADS, 7);

        private static final Map<VertexFormat.DrawMode, Integer>
            BY_DRAW_MODE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(DrawModes::getDrawMode, DrawModes::getValue)));

        private final VertexFormat.DrawMode drawMode;
        private final int value;

        DrawModes(VertexFormat.DrawMode drawMode, int value) {
            this.drawMode = drawMode;
            this.value = value;
        }

        public static int getValue(VertexFormat.DrawMode drawMode) {
            return BY_DRAW_MODE.get(drawMode);
        }

        public VertexFormat.DrawMode getDrawMode() {
            return drawMode;
        }

        public int getValue() {
            return value;
        }
    }

    public enum VertexFormats {
        POSITION_COLOR_TEXTURE_LIGHT_NORMAL(
            net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, 0),
        POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL(
            net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            1),
        POSITION_TEXTURE_COLOR_LIGHT(
            net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, 2),
        POSITION(net.minecraft.client.render.VertexFormats.POSITION, 3),
        POSITION_COLOR(net.minecraft.client.render.VertexFormats.POSITION_COLOR, 4),
        LINES(net.minecraft.client.render.VertexFormats.LINES, 5),
        POSITION_COLOR_LIGHT(net.minecraft.client.render.VertexFormats.POSITION_COLOR_LIGHT, 6),
        POSITION_TEXTURE(net.minecraft.client.render.VertexFormats.POSITION_TEXTURE, 7),
        POSITION_TEXTURE_COLOR(net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR, 8),
        POSITION_COLOR_TEXTURE_LIGHT(
            net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, 9);
        // Ordinals 10 and 11 reserved for 1.21+-only formats POSITION_TEXTURE_LIGHT_COLOR
        // and POSITION_TEXTURE_COLOR_NORMAL. These do not exist in 1.20.1 yarn. The slots
        // remain reserved so MCVR's ordinal table stays version-stable. Re-add when
        // backporting to 1.21.x. See PRD §4.4 / §4.6.
        // Ordinal 12 reserved for PBR_TRIANGLE — depends on deferred PBRVertexFormats
        // (uses 1.21+ VertexFormat.builder()). Re-enable in Checkpoint A/B.

        private static final Map<VertexFormat, Integer>
            BY_VERTEX_FORMAT =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(
                    Collectors.toMap(VertexFormats::getVertexFormat, VertexFormats::getValue,
                        (existing, duplicate) -> existing)));

        private final VertexFormat vertexFormat;
        private final int value;

        VertexFormats(VertexFormat vertexFormat, int value) {
            this.vertexFormat = vertexFormat;
            this.value = value;
        }

        public static int getValue(VertexFormat vertexFormat) {
            return BY_VERTEX_FORMAT.get(vertexFormat);
        }

        public VertexFormat getVertexFormat() {
            return vertexFormat;
        }

        public int getValue() {
            return value;
        }
    }

    public enum GeometryTypes {
        SHADOW(0),
        WORLD_SOLID(1),
        WORLD_TRANSPARENT(2),
        WORLD_NO_REFLECT(3),
        WORLD_CLOUD(4),
        BOAT_WATER_MASK(5),
        END_PORTAL(6),
        END_GATEWAY(7);

        private final int value;

        GeometryTypes(int value) {
            this.value = value;
        }

        public static GeometryTypes getGeometryType(RenderLayer renderLayer, boolean reflect) {
            // Stubbed during Checkpoint 0b backport — body originally inspected
            // RenderLayer.MultiPhase.isTranslucent() and the OVERLAY_TRANSPARENCY/etc.
            // RenderPhase.Transparency constants. In 1.20.1 isTranslucent() is not
            // exposed, OVERLAY_TRANSPARENCY does not exist, and equals() on
            // RenderPhase.Transparency is package-private. The only callers were
            // ChunkProxy/EntityProxy which are themselves deferred — see
            // src/deferred/java/com/radiance/client/proxy/. To be reimplemented
            // alongside those proxies in Checkpoint A/B/C/D.
            throw new UnsupportedOperationException(
                "Constants.GeometryTypes.getGeometryType deferred to Checkpoint A/B/C/D");
        }

        public int getValue() {
            return value;
        }
    }

    public enum Coordinates {
        WORLD(0),
        CAMERA(1),
        CAMERA_SHIFT(2);

        private final int value;

        Coordinates(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum RayTracingFlags {
        WORLD(0b00000001),
        PLAYER(0b00000010),
        FISHING_BOBBER(0b00000100),
        HAND(0b00001000),
        WEATHER(0b00010000),
        PARTICLE(0b00100000),
        CLOUD(0b01000000),
        BOAT_WATER_MASK(0b10000000);

        private final int value;

        RayTracingFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Constructs the Java-side ordinal table for the JNI ABI handshake (PRD §4.3 / §4.4).
     *
     * <p>Layout:
     * [magic, version, section-count,
     *   section-id, payload-length, entry-id, abi-value, flags, ...]
     *
     * <p>Each section payload is a sequence of entry triples. Vertex format slots 10, 11,
     * and 12 are emitted as reserved entries so native validation can distinguish intentional
     * gaps from ABI drift.
     */
    public static long[] dumpOrdinals() {
        java.util.List<Long> out = new java.util.ArrayList<>();
        out.add(ORDINAL_TABLE_MAGIC);
        out.add(ORDINAL_TABLE_VERSION);
        out.add(ORDINAL_TABLE_SECTION_COUNT);

        appendVertexFormatSection(out);
        appendEnumSection(out, SECTION_DRAW_MODES, DrawModes.values().length,
            index -> DrawModes.values()[index].getValue());
        appendEnumSection(out, SECTION_INDEX_TYPES, IndexTypes.values().length,
            index -> IndexTypes.values()[index].getValue());
        appendEnumSection(out, SECTION_GEOMETRY_TYPES, GeometryTypes.values().length,
            index -> GeometryTypes.values()[index].getValue());
        appendEnumSection(out, SECTION_RAY_TRACING_FLAGS, RayTracingFlags.values().length,
            index -> RayTracingFlags.values()[index].getValue());

        long[] arr = new long[out.size()];
        for (int idx = 0; idx < arr.length; idx++) arr[idx] = out.get(idx);
        return arr;
    }

    private static void appendVertexFormatSection(java.util.List<Long> out) {
        out.add(SECTION_VERTEX_FORMATS);
        out.add((long) (VertexFormats.values().length + RESERVED_VERTEX_FORMAT_ORDINALS.length) * 3L);
        for (VertexFormats v : VertexFormats.values()) {
            appendEntry(out, v.getValue(), v.getValue(), ENTRY_ACTIVE);
        }
        for (long reservedOrdinal : RESERVED_VERTEX_FORMAT_ORDINALS) {
            appendEntry(out, reservedOrdinal, reservedOrdinal, ENTRY_RESERVED);
        }
    }

    private static void appendEnumSection(java.util.List<Long> out, long sectionId, int entryCount,
                                          java.util.function.IntFunction<Integer> valueAt) {
        out.add(sectionId);
        out.add((long) entryCount * 3L);
        for (int entryId = 0; entryId < entryCount; entryId++) {
            appendEntry(out, entryId, valueAt.apply(entryId), ENTRY_ACTIVE);
        }
    }

    private static void appendEntry(java.util.List<Long> out, long entryId, long abiValue, long flags) {
        out.add(entryId);
        out.add(abiValue);
        out.add(flags);
    }
}
