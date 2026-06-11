package com.pos.ocr.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringSimilarityTest {

    @Test
    void levenshteinBasics() {
        assertEquals(0, StringSimilarity.levenshtein("TOTAL", "TOTAL"));
        assertEquals(1, StringSimilarity.levenshtein("TOTAL", "TOIAL"));
        assertEquals(5, StringSimilarity.levenshtein("", "TOTAL"));
    }

    @Test
    void normalizedSimilarityRange() {
        assertEquals(1.0, StringSimilarity.normalizedSimilarity("CASH", "CASH"));
        assertTrue(StringSimilarity.normalizedSimilarity("TOTAL", "TOIAL") > 0.7);
        assertTrue(StringSimilarity.normalizedSimilarity("TOTAL", "BANANA") < 0.5);
    }

    @Test
    void jaroWinklerRewardsCommonPrefix() {
        double withPrefix = StringSimilarity.jaroWinkler("CHANGE", "CHANGT");
        double noPrefix = StringSimilarity.jaroWinkler("CHANGE", "XHANGE");
        assertTrue(withPrefix > noPrefix);
        assertEquals(1.0, StringSimilarity.jaroWinkler("NET", "NET"), 1e-9);
    }
}
