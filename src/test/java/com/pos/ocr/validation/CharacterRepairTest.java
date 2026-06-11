package com.pos.ocr.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterRepairTest {

    @Test
    void mapsCommonConfusions() {
        assertEquals("12.50", CharacterRepair.repairNumeric("l2.5O"));
        assertEquals("8.00", CharacterRepair.repairNumeric("B.OO"));
        assertEquals("5.55", CharacterRepair.repairNumeric("S.SS"));
    }

    @Test
    void looksNumericDetectsRealNumbers() {
        assertTrue(CharacterRepair.looksNumeric("1,234.56"));
        assertTrue(CharacterRepair.looksNumeric("5.78"));
        assertFalse(CharacterRepair.looksNumeric("TOTAL"));
    }
}
