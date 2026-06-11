package com.pos.ocr.validation;

import com.pos.ocr.model.LineItem;
import com.pos.ocr.model.Money;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.model.TaxLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MathValidatorTest {

    private final MathValidator validator = new MathValidator();

    private StructuredDocument consistentReceipt() {
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(new LineItem("Apple", new BigDecimal("2"), Money.of("1.50"), Money.of("3.00")));
        doc.addLineItem(new LineItem("Milk", new BigDecimal("1"), Money.of("2.25"), Money.of("2.25")));
        doc.setSubtotal(Money.of("5.25"));
        doc.addTaxLine(new TaxLine("TAX", Money.of("0.53")));
        doc.setTotal(Money.of("5.78"));
        doc.setTendered(Money.of("10.00"));
        doc.setChange(Money.of("4.22"));
        return doc;
    }

    @Test
    void allChecksPassOnConsistentReceipt() {
        ValidationResult r = validator.validate(consistentReceipt(), ValidationProfile.RECEIPT);
        assertTrue(r.isValid(), "expected valid, failed: " + r.failedCheck());
        assertEquals(1.0, r.mathScore());
    }

    @Test
    void detectsTotalMismatch() {
        StructuredDocument doc = consistentReceipt();
        doc.setTotal(Money.of("9.99"));
        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT);
        assertFalse(r.isValid());
        assertEquals("total==subtotal+tax", r.failedCheck().name());
    }

    @Test
    void selfHealsLineTotalFromQtyTimesUnit() {
        StructuredDocument doc = new StructuredDocument();
        // 2 * 1.50 should be 3.00 but was misread as 3.50.
        doc.addLineItem(new LineItem("Apple", new BigDecimal("2"), Money.of("1.50"), Money.of("3.50")));
        doc.setSubtotal(Money.of("3.00"));
        doc.setTotal(Money.of("3.00"));

        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT, true);
        assertTrue(r.isValid(), "self-heal should fix the line total and close the sum");
        assertEquals(Money.of("3.00"), doc.lineItems().get(0).lineTotal());
        assertFalse(r.repairs().isEmpty());
    }

    @Test
    void selfHealDerivesMissingSubtotal() {
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(LineItem.of("Item A", Money.of("4.00")));
        doc.addLineItem(LineItem.of("Item B", Money.of("6.00")));
        // no subtotal set; total present and equal to the sum (no tax)
        doc.setTotal(Money.of("10.00"));

        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT, true);
        assertTrue(r.isValid());
        assertEquals(Money.of("10.00"), doc.subtotal());
    }

    @Test
    void withoutSelfHealInconsistentLineFails() {
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(new LineItem("Apple", new BigDecimal("2"), Money.of("1.50"), Money.of("3.50")));
        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT, false);
        assertFalse(r.isValid());
    }

    // ---- correctness gate: no vacuous truth ----

    @Test
    void emptyDocumentIsNeverValid() {
        ValidationResult r = validator.validate(new StructuredDocument(), ValidationProfile.RECEIPT);
        assertFalse(r.isValid(), "zero executed checks must not validate (vacuous truth)");
        assertEquals(0.0, r.mathScore(), "no evidence must not score as perfect evidence");
    }

    @Test
    void missingTotalIsAViolationForReceipts() {
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(LineItem.of("Item A", Money.of("4.00")));
        doc.setSubtotal(Money.of("4.00"));
        // subtotal check passes, but there is no grand total to anchor the document
        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT);
        assertFalse(r.isValid());
        assertFalse(r.violations().isEmpty());
    }

    @Test
    void derivedSubtotalAloneIsNotEvidence() {
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(LineItem.of("Item A", Money.of("4.00")));
        // GENERIC does not require a total; the only check is the self-derived subtotal,
        // which compares the sum against itself and must not count as cross-field evidence.
        ValidationResult r = validator.validate(doc, ValidationProfile.GENERIC, true);
        assertFalse(r.isValid(),
                "a subtotal derived from the very sum it is checked against proves nothing");
    }

    @Test
    void skuMisreadAsQuantityIsImplausibleEvenWhenMathCloses() {
        StructuredDocument doc = new StructuredDocument();
        // UPC digits landed in the quantity column; no unit price, so no line check fires.
        doc.addLineItem(new LineItem("MILK", new BigDecimal("49000028"), null, Money.of("3.49")));
        doc.setSubtotal(Money.of("3.49"));
        doc.setTotal(Money.of("3.49"));

        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT);
        assertFalse(r.isValid(), "plausibility bounds must reject absurd quantities");
        assertFalse(r.violations().isEmpty());
    }

    @Test
    void selfHealIsCappedAtOneRepair() {
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(new LineItem("A", new BigDecimal("2"), Money.of("1.50"), Money.of("3.50")));
        doc.addLineItem(new LineItem("B", new BigDecimal("1"), Money.of("2.00"), Money.of("2.50")));
        doc.setSubtotal(Money.of("5.00"));
        doc.setTotal(Money.of("5.00"));

        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT, true);
        assertFalse(r.isValid(), "two broken lines cannot both be repaired away");
        assertEquals(1, r.repairs().size());
    }

    // ---- tax modes ----

    @Test
    void taxInclusiveReceiptValidatesUnderInclusiveProfileOnly() {
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(LineItem.of("Buch", Money.of("9.99")));
        doc.addLineItem(LineItem.of("Stift", Money.of("10.00")));
        doc.addTaxLine(new TaxLine("MWST", Money.of("3.19"))); // informational "incl. VAT"
        doc.setTotal(Money.of("19.99"));

        ValidationResult inclusive = validator.validate(doc, ValidationProfile.RECEIPT_TAX_INCLUSIVE);
        assertTrue(inclusive.isValid(), "gross items + informational VAT must close; failed: "
                + inclusive.failedCheck());

        ValidationResult addOn = validator.validate(doc, ValidationProfile.RECEIPT);
        assertFalse(addOn.isValid(), "the same receipt must fail US add-on math (19.99+3.19 != 19.99)");
    }

    @Test
    void inclusiveModeAcceptsNetStyleSubtotal() {
        // Some VAT receipts print a NET subtotal: net + tax == total, items are gross.
        StructuredDocument doc = new StructuredDocument();
        doc.addLineItem(LineItem.of("Item", Money.of("19.99")));
        doc.setSubtotal(Money.of("16.80"));               // net
        doc.addTaxLine(new TaxLine("VAT", Money.of("3.19")));
        doc.setTotal(Money.of("19.99"));                  // gross

        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT_TAX_INCLUSIVE);
        assertTrue(r.isValid(), "net-style subtotal must be recognized; failed: " + r.failedCheck());
    }

    // ---- adaptive epsilon ----

    @Test
    void adaptiveEpsilonAbsorbsAccumulatedPerLineRounding() {
        // Ten items rounded per line can legitimately drift several cents off the printed
        // subtotal (per-line vs on-total rounding conventions). 0.005 * 10 = 0.05 tolerance.
        StructuredDocument doc = new StructuredDocument();
        for (int i = 0; i < 10; i++) {
            doc.addLineItem(LineItem.of("Item " + i, Money.of("1.00")));
        }
        doc.setSubtotal(Money.of("10.04"));
        doc.setTotal(Money.of("10.04"));

        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT);
        assertTrue(r.isValid(), "4c drift over 10 items is rounding, not a misread; failed: "
                + r.failedCheck());
    }

    @Test
    void adaptiveEpsilonStillCatchesRealMisreads() {
        StructuredDocument doc = new StructuredDocument();
        for (int i = 0; i < 10; i++) {
            doc.addLineItem(LineItem.of("Item " + i, Money.of("1.00")));
        }
        doc.setSubtotal(Money.of("10.06")); // beyond 0.005 * 10
        doc.setTotal(Money.of("10.06"));

        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT);
        assertFalse(r.isValid(), "drift beyond the per-line allowance is a misread");
    }
}
