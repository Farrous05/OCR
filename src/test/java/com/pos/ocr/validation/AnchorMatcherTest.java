package com.pos.ocr.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnchorMatcherTest {

    private final AnchorMatcher matcher = new AnchorMatcher();

    @Test
    void matchesExactAnchors() {
        assertEquals(Anchor.TOTAL, matcher.match("TOTAL").orElseThrow().anchor());
        assertEquals(Anchor.SUBTOTAL, matcher.match("SUBTOTAL").orElseThrow().anchor());
        assertEquals(Anchor.CHANGE, matcher.match("CHANGE").orElseThrow().anchor());
    }

    @Test
    void toleratesOcrMangling() {
        assertEquals(Anchor.TOTAL, matcher.match("TOIAL").orElseThrow().anchor());
        assertEquals(Anchor.SUBTOTAL, matcher.match("5UBTOTAL").orElseThrow().anchor());
    }

    @Test
    void distinguishesSubtotalFromTotal() {
        assertEquals(Anchor.SUBTOTAL, matcher.match("SUBTOTAL").orElseThrow().anchor());
        assertEquals(Anchor.TOTAL, matcher.match("TOTAL").orElseThrow().anchor());
    }

    @Test
    void rejectsUnrelatedWords() {
        assertTrue(matcher.match("BANANA").isEmpty());
        assertTrue(matcher.match("123").isEmpty());
    }
}
