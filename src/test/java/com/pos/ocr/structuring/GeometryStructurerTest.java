package com.pos.ocr.structuring;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.model.LineItem;
import com.pos.ocr.model.Money;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class GeometryStructurerTest {

    private final GeometryStructurer structurer = new GeometryStructurer();

    @Test
    void extractsMetadata() {
        StructuredDocument doc = structurer.structure(Fixtures.cleanReceiptTokens(), DocumentType.RECEIPT);
        assertEquals("MARKET FRESH", doc.storeName());
        assertEquals("R-9001", doc.invoiceId());
        assertEquals("2026-06-10", doc.date());
    }

    @Test
    void extractsLineItemsWithQtyUnitTotal() {
        StructuredDocument doc = structurer.structure(Fixtures.cleanReceiptTokens(), DocumentType.RECEIPT);
        assertEquals(2, doc.lineItems().size());

        LineItem apple = doc.lineItems().get(0);
        assertEquals("Apple", apple.name());
        assertEquals(0, new BigDecimal("2").compareTo(apple.quantity()));
        assertEquals(Money.of("1.50"), apple.unitPrice());
        assertEquals(Money.of("3.00"), apple.lineTotal());

        LineItem milk = doc.lineItems().get(1);
        assertEquals("Whole Milk", milk.name());
        assertEquals(Money.of("2.25"), milk.lineTotal());
    }

    @Test
    void extractsTotalsAndTender() {
        StructuredDocument doc = structurer.structure(Fixtures.cleanReceiptTokens(), DocumentType.RECEIPT);
        assertEquals(Money.of("5.25"), doc.subtotal());
        assertEquals(Money.of("5.78"), doc.total());
        assertEquals(Money.of("10.00"), doc.tendered());
        assertEquals(Money.of("4.22"), doc.change());
        assertEquals(Money.of("0.53"), doc.taxSum());
    }
}
