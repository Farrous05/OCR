package com.pos.ocr.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void arithmeticIsExactAtScaleTwo() {
        Money a = Money.of("0.10");
        Money b = Money.of("0.20");
        assertEquals(Money.of("0.30"), a.add(b), "0.10+0.20 must be exactly 0.30 (no double drift)");
        assertEquals(Money.of("0.10"), b.subtract(a));
    }

    @Test
    void multiplyByQuantity() {
        assertEquals(Money.of("3.00"), Money.of("1.50").multiply(new java.math.BigDecimal("2")));
    }

    @Test
    void equalsWithinEpsilon() {
        assertTrue(Money.of("5.78").equalsWithin(Money.of("5.79"), Money.EPSILON));
        assertFalse(Money.of("5.78").equalsWithin(Money.of("5.80"), Money.EPSILON));
    }

    @Test
    void equalityIgnoresTrailingScaleDifferences() {
        assertEquals(Money.of("5"), Money.of("5.00"));
        assertEquals(Money.of("5").hashCode(), Money.of("5.00").hashCode());
    }
}
