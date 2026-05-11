package com.radiance.client.constant;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantsDumpOrdinalsTest {
    private static final long ORDINAL_TABLE_MAGIC = 0x5241445f4f524453L;
    private static final long ORDINAL_TABLE_VERSION = 1L;
    private static final long ORDINAL_TABLE_SECTION_COUNT = 5L;

    private static final long SECTION_VERTEX_FORMATS = 1L;
    private static final long SECTION_DRAW_MODES = 2L;
    private static final long SECTION_INDEX_TYPES = 3L;
    private static final long SECTION_GEOMETRY_TYPES = 4L;
    private static final long SECTION_RAY_TRACING_FLAGS = 5L;

    private static final long ENTRY_ACTIVE = 0L;
    private static final long ENTRY_RESERVED = 1L;

    @Test
    void dumpOrdinalsReturnsNonEmptyTable() {
        long[] ords = Constants.dumpOrdinals();
        assertNotNull(ords);
        assertTrue(ords.length > 0, "ordinal table must contain at least one entry");
    }

    @Test
    void dumpOrdinalsIsDeterministic() {
        long[] a = Constants.dumpOrdinals();
        long[] b = Constants.dumpOrdinals();
        assertArrayEquals(a, b,
            "dumpOrdinals must produce the same table on every call");
    }

    @Test
    void dumpOrdinalsHasAbiHeaderAndSections() {
        ParsedTable table = parse(Constants.dumpOrdinals());

        assertEquals(ORDINAL_TABLE_MAGIC, table.magic);
        assertEquals(ORDINAL_TABLE_VERSION, table.version);
        assertEquals(ORDINAL_TABLE_SECTION_COUNT, table.sectionCount);
        assertArrayEquals(new long[] {
                SECTION_VERTEX_FORMATS,
                SECTION_DRAW_MODES,
                SECTION_INDEX_TYPES,
                SECTION_GEOMETRY_TYPES,
                SECTION_RAY_TRACING_FLAGS
            },
            table.sections.keySet().stream().mapToLong(Long::longValue).toArray());

        assertSection(table, SECTION_VERTEX_FORMATS, 13);
        assertSection(table, SECTION_DRAW_MODES, Constants.DrawModes.values().length);
        assertSection(table, SECTION_INDEX_TYPES, Constants.IndexTypes.values().length);
        assertSection(table, SECTION_GEOMETRY_TYPES, Constants.GeometryTypes.values().length);
        assertSection(table, SECTION_RAY_TRACING_FLAGS, Constants.RayTracingFlags.values().length);
    }

    @Test
    void dumpOrdinalsIncludesReservedVertexFormatOrdinals() {
        ParsedTable table = parse(Constants.dumpOrdinals());
        Section vertexFormats = table.sections.get(SECTION_VERTEX_FORMATS);

        for (Constants.VertexFormats vertexFormat : Constants.VertexFormats.values()) {
            Entry entry = vertexFormats.entry(vertexFormat.getValue());
            assertEquals(vertexFormat.getValue(), entry.abiValue);
            assertEquals(ENTRY_ACTIVE, entry.flags);
        }

        assertReservedVertexFormat(vertexFormats, 10L);
        assertReservedVertexFormat(vertexFormats, 11L);
        assertReservedVertexFormat(vertexFormats, 12L);
    }

    private static void assertSection(ParsedTable table, long sectionId, int entryCount) {
        Section section = table.sections.get(sectionId);
        assertNotNull(section, "missing section " + sectionId);
        assertEquals(entryCount * 3L, section.payloadLength);
        assertEquals(entryCount, section.entries.length);
    }

    private static void assertReservedVertexFormat(Section section, long ordinal) {
        Entry entry = section.entry(ordinal);
        assertEquals(ordinal, entry.abiValue);
        assertEquals(ENTRY_RESERVED, entry.flags);
    }

    private static ParsedTable parse(long[] ords) {
        assertTrue(ords.length >= 3, "ordinal table must include a header");
        ParsedTable table = new ParsedTable(ords[0], ords[1], ords[2]);
        int offset = 3;
        for (int sectionIndex = 0; sectionIndex < table.sectionCount; sectionIndex++) {
            assertTrue(offset + 2 <= ords.length, "section header exceeds table length");
            long sectionId = ords[offset++];
            long payloadLength = ords[offset++];
            assertEquals(0L, payloadLength % 3L, "section payload must contain entry triples");
            assertTrue(offset + payloadLength <= ords.length, "section payload exceeds table length");

            Entry[] entries = new Entry[(int) payloadLength / 3];
            for (int entryIndex = 0; entryIndex < entries.length; entryIndex++) {
                entries[entryIndex] = new Entry(ords[offset], ords[offset + 1], ords[offset + 2]);
                offset += 3;
            }
            table.sections.put(sectionId, new Section(payloadLength, entries));
        }
        assertEquals(ords.length, offset, "parser must consume the entire ordinal table");
        return table;
    }

    private static final class ParsedTable {
        private final long magic;
        private final long version;
        private final long sectionCount;
        private final Map<Long, Section> sections = new LinkedHashMap<>();

        private ParsedTable(long magic, long version, long sectionCount) {
            this.magic = magic;
            this.version = version;
            this.sectionCount = sectionCount;
        }
    }

    private static final class Section {
        private final long payloadLength;
        private final Entry[] entries;

        private Section(long payloadLength, Entry[] entries) {
            this.payloadLength = payloadLength;
            this.entries = entries;
        }

        private Entry entry(long entryId) {
            return Arrays.stream(entries)
                .filter(entry -> entry.entryId == entryId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing entry " + entryId));
        }
    }

    private static final class Entry {
        private final long entryId;
        private final long abiValue;
        private final long flags;

        private Entry(long entryId, long abiValue, long flags) {
            this.entryId = entryId;
            this.abiValue = abiValue;
            this.flags = flags;
        }
    }
}
