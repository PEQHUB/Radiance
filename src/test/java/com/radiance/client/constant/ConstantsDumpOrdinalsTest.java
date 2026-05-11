package com.radiance.client.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstantsDumpOrdinalsTest {

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
        assertTrue(java.util.Arrays.equals(a, b),
            "dumpOrdinals must produce the same table on every call (used for ABI handshake)");
    }
}
