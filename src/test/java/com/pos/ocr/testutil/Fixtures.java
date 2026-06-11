package com.pos.ocr.testutil;

import com.pos.ocr.ocr.OcrToken;
import com.pos.ocr.ocr.StubOcrEngine;

import java.util.List;

/** Deterministic token fixtures representing OCR output for a clean receipt. */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * A fully consistent receipt:
     * <pre>
     *   MARKET FRESH
     *   Invoice #R-9001
     *   2026-06-10
     *   Apple        2   1.50   3.00
     *   Whole Milk   1   2.25   2.25
     *   SUBTOTAL                5.25
     *   TAX                     0.53
     *   TOTAL                   5.78
     *   CASH                   10.00
     *   CHANGE                  4.22
     * </pre>
     */
    public static List<OcrToken> cleanReceiptTokens() {
        return new StubOcrEngine.Builder().rowHeight(30)
                .token("MARKET", 0.97, 0, 10, 90)
                .token("FRESH", 0.97, 0, 110, 90)
                .token("Invoice", 0.96, 1, 10, 100)
                .token("#R-9001", 0.96, 1, 120, 120)
                .token("2026-06-10", 0.96, 2, 10, 160)
                .token("Apple", 0.95, 3, 10, 80)
                .token("2", 0.95, 3, 200, 30)
                .token("1.50", 0.95, 3, 260, 50)
                .token("3.00", 0.95, 3, 330, 50)
                .token("Whole", 0.95, 4, 10, 70)
                .token("Milk", 0.95, 4, 90, 60)
                .token("1", 0.95, 4, 200, 30)
                .token("2.25", 0.95, 4, 260, 50)
                .token("2.25", 0.95, 4, 330, 50)
                .token("SUBTOTAL", 0.96, 5, 10, 140)
                .token("5.25", 0.96, 5, 330, 50)
                .token("TAX", 0.96, 6, 10, 100)
                .token("0.53", 0.96, 6, 330, 50)
                .token("TOTAL", 0.96, 7, 10, 120)
                .token("5.78", 0.96, 7, 330, 50)
                .token("CASH", 0.96, 8, 10, 100)
                .token("10.00", 0.96, 8, 330, 60)
                .token("CHANGE", 0.96, 9, 10, 120)
                .token("4.22", 0.96, 9, 330, 50)
                .tokens();
    }

    /** Same receipt but with the TOTAL misread (5.78 -> 9.78): math will not close. */
    public static List<OcrToken> brokenTotalTokens() {
        return new StubOcrEngine.Builder().rowHeight(30)
                .token("MARKET", 0.97, 0, 10, 90)
                .token("Apple", 0.95, 3, 10, 80)
                .token("2", 0.95, 3, 200, 30)
                .token("1.50", 0.95, 3, 260, 50)
                .token("3.00", 0.95, 3, 330, 50)
                .token("SUBTOTAL", 0.96, 5, 10, 140)
                .token("3.00", 0.96, 5, 330, 50)
                .token("TAX", 0.96, 6, 10, 100)
                .token("0.00", 0.96, 6, 330, 50)
                .token("TOTAL", 0.96, 7, 10, 120)
                .token("9.78", 0.40, 7, 330, 50)
                .tokens();
    }
}
