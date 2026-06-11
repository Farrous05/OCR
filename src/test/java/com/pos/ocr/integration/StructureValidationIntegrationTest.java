package com.pos.ocr.integration;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.model.Money;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.structuring.GeometryStructurer;
import com.pos.ocr.testutil.Fixtures;
import com.pos.ocr.validation.MathValidator;
import com.pos.ocr.validation.ValidationProfile;
import com.pos.ocr.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Structuring + validation working together over the same token stream. */
class StructureValidationIntegrationTest {

    private final GeometryStructurer structurer = new GeometryStructurer();
    private final MathValidator validator = new MathValidator();

    @Test
    void cleanReceiptStructuresAndValidates() {
        StructuredDocument doc = structurer.structure(Fixtures.cleanReceiptTokens(), DocumentType.RECEIPT);
        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT, true);

        assertTrue(r.isValid(), "math should close end-to-end; failed: " + r.failedCheck());
        assertEquals(Money.of("5.78"), doc.total());
        // Every applicable check ran and passed (subtotal, total, two line maths, tender).
        assertTrue(r.checks().size() >= 4);
    }

    @Test
    void brokenTotalStructuresButFailsValidation() {
        StructuredDocument doc = structurer.structure(Fixtures.brokenTotalTokens(), DocumentType.RECEIPT);
        ValidationResult r = validator.validate(doc, ValidationProfile.RECEIPT, true);
        assertFalse(r.isValid());
        assertEquals("total==subtotal+tax", r.failedCheck().name());
    }
}
