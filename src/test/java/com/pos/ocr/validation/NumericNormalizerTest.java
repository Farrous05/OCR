package com.pos.ocr.validation;

import com.pos.ocr.model.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumericNormalizerTest {

    @Test
    void usGrouping() {
        assertEquals(Money.of("1234.56"), NumericNormalizer.parseMoney("1,234.56").orElseThrow());
    }

    @Test
    void euGrouping() {
        assertEquals(Money.of("1234.56"), NumericNormalizer.parseMoney("1.234,56").orElseThrow());
    }

    @Test
    void stripsCurrencyGlyph() {
        assertEquals(Money.of("5.78"), NumericNormalizer.parseMoney("$5.78").orElseThrow());
    }

    @Test
    void singleCommaIsDecimal() {
        assertEquals(Money.of("5.78"), NumericNormalizer.parseMoney("5,78").orElseThrow());
    }

    @Test
    void threeTrailingDigitsTreatedAsThousands() {
        assertEquals(Money.of("1234.00"), NumericNormalizer.parseMoney("1.234").orElseThrow());
        assertEquals(Money.of("1234.00"), NumericNormalizer.parseMoney("1,234").orElseThrow());
    }

    @Test
    void negativeAndParenthesised() {
        assertEquals(Money.of("-5.00"), NumericNormalizer.parseMoney("-5.00").orElseThrow());
    }

    @Test
    void letterContaminationFailsDirectParse() {
        assertTrue(NumericNormalizer.parseMoney("l2.5O").isEmpty(),
                "letters should fail direct parse so repair can engage");
    }

    @Test
    void repairPathRecoversMisreadDigits() {
        assertEquals(Money.of("12.50"),
                NumericNormalizer.parseMoneyWithRepair("l2.5O").orElseThrow());
        assertEquals(Money.of("8.00"),
                NumericNormalizer.parseMoneyWithRepair("B.OO").orElseThrow());
    }

    @Test
    void garbageReturnsEmpty() {
        assertTrue(NumericNormalizer.parseMoney("----").isEmpty());
        assertTrue(NumericNormalizer.parseMoney("").isEmpty());
    }
}
