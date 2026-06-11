package com.pos.ocr.validation;

import com.pos.ocr.model.LineItem;
import com.pos.ocr.model.Money;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.validation.ValidationProfile.TaxMode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic math validation — the final arbiter of correctness. Models propose; arithmetic
 * disposes. Hardened in three ways:
 *
 * <ul>
 *   <li><b>No vacuous truth.</b> Plausibility bounds run first (a 49-million quantity is an OCR'd
 *       UPC, not commerce, even when its math closes), and {@link ValidationResult#isValid()}
 *       demands at least one binding cross-field check.</li>
 *   <li><b>Adaptive tolerance.</b> Aggregate checks allow half a cent of rounding drift per line
 *       item: merchants legally round per line OR per total and the conventions diverge as
 *       receipts grow. Printed-vs-printed checks (tender − total == change) keep the fixed
 *       one-cent floor.</li>
 *   <li><b>Bounded self-heal.</b> At most one repair per document, and a repaired or derived
 *       value can never be the only evidence of validity (its check is non-binding).</li>
 * </ul>
 */
public final class MathValidator {

    /** Per-line rounding allowance for aggregate checks: half a cent per item. */
    private static final BigDecimal PER_LINE_TOLERANCE = new BigDecimal("0.005");

    /** Plausibility bounds. Outside these is corruption, not commerce. */
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000");
    private static final Money MAX_UNIT_PRICE = Money.of("100000");
    private static final Money MAX_LINE_TOTAL = Money.of("100000");
    private static final Money MAX_TOTAL = Money.of("1000000");

    /** Scale applied when a repair materializes a computed value onto the document. */
    private static final int PRINTED_SCALE = 2;

    private final Money floorEpsilon;

    public MathValidator() {
        this(Money.EPSILON);
    }

    public MathValidator(Money floorEpsilon) {
        this.floorEpsilon = floorEpsilon;
    }

    public ValidationResult validate(StructuredDocument doc, ValidationProfile profile) {
        return validate(doc, profile, false);
    }

    public ValidationResult validate(StructuredDocument doc, ValidationProfile profile, boolean selfHeal) {
        ValidationResult result = new ValidationResult();

        checkPlausibility(doc, profile, result);

        // Per-line math first, so a repaired line feeds the aggregate checks below.
        if (profile.checkLineMath) {
            checkLineMath(doc, result, selfHeal);
        }

        Money itemSum = doc.lineItemSum();
        Money aggregateEpsilon = adaptiveEpsilon(doc.lineItems().size());

        if (profile.checkSubtotal && !doc.lineItems().isEmpty()) {
            checkSubtotal(doc, profile.taxMode, itemSum, aggregateEpsilon, result, selfHeal);
        }

        if (profile.checkTotal && doc.total() != null) {
            checkTotal(doc, profile.taxMode, itemSum, aggregateEpsilon, result);
        }

        // Tender involves three printed values — no aggregation, so no adaptive slack.
        if (profile.checkTender && doc.tendered() != null && doc.change() != null
                && doc.total() != null) {
            Money expected = doc.tendered().subtract(doc.total());
            boolean ok = expected.equalsWithin(doc.change(), floorEpsilon);
            result.addCheck("change==tendered-total", ok, expected.toString(),
                    doc.change().toString(), true);
        }

        return result;
    }

    /** max(floor, 0.005 × nItems): tolerance for checks that aggregate per-line roundings. */
    Money adaptiveEpsilon(int lineItemCount) {
        Money scaled = Money.of(PER_LINE_TOLERANCE.multiply(BigDecimal.valueOf(lineItemCount)));
        return scaled.compareTo(floorEpsilon) > 0 ? scaled : floorEpsilon;
    }

    // ---- individual check groups ----

    private void checkLineMath(StructuredDocument doc, ValidationResult result, boolean selfHeal) {
        for (LineItem item : doc.lineItems()) {
            if (!item.hasUnitMath() || item.lineTotal() == null) continue;
            Money expected = item.unitPrice().multiply(item.quantity());
            boolean ok = expected.equalsWithin(item.lineTotal(), floorEpsilon);
            if (!ok && mayRepair(selfHeal, result)) {
                Money repaired = expected.rounded(PRINTED_SCALE, RoundingMode.HALF_UP);
                result.addRepair("Line '" + item.name() + "' total " + item.lineTotal()
                        + " -> " + repaired + " (qty*unit)");
                item.overrideLineTotal(repaired);
                ok = true;
            }
            result.addCheck("line:" + item.name(), ok, expected.toString(),
                    String.valueOf(item.lineTotal()), true);
        }
    }

    private void checkSubtotal(StructuredDocument doc, TaxMode taxMode, Money itemSum,
                               Money epsilon, ValidationResult result, boolean selfHeal) {
        if (doc.subtotal() == null) {
            if (mayRepair(selfHeal, result)) {
                doc.setSubtotal(itemSum);
                result.addRepair("Subtotal derived from line-item sum: " + itemSum);
                // Derived from the very numbers it would be compared against — non-binding.
                result.addCheck("subtotal==sum(items) [derived]", true,
                        itemSum.toString(), itemSum.toString(), false);
            }
            return;
        }
        boolean ok = doc.subtotal().equalsWithin(itemSum, epsilon)
                // Inclusive receipts may print a NET subtotal while items are gross.
                || (taxMode == TaxMode.INCLUSIVE
                        && doc.subtotal().add(doc.taxSum()).equalsWithin(itemSum, epsilon));
        result.addCheck("subtotal==sum(items)", ok, itemSum.toString(),
                doc.subtotal().toString(), true);
    }

    private void checkTotal(StructuredDocument doc, TaxMode taxMode, Money itemSum,
                            Money epsilon, ValidationResult result) {
        if (taxMode == TaxMode.INCLUSIVE) {
            Money gross = !doc.lineItems().isEmpty() ? itemSum : doc.subtotal();
            boolean grossOk = gross != null && gross.equalsWithin(doc.total(), epsilon);
            // Net-printing convention: subtotal excludes VAT, total includes it.
            boolean netOk = doc.subtotal() != null
                    && doc.subtotal().add(doc.taxSum()).equalsWithin(doc.total(), epsilon);
            result.addCheck("total==gross [tax-inclusive]", grossOk || netOk,
                    gross == null ? "n/a" : gross.toString(), doc.total().toString(), true);
        } else {
            Money base = doc.subtotal() != null ? doc.subtotal() : itemSum;
            Money expected = base.add(doc.taxSum());
            boolean ok = expected.equalsWithin(doc.total(), epsilon);
            result.addCheck("total==subtotal+tax", ok, expected.toString(),
                    doc.total().toString(), true);
        }
    }

    private void checkPlausibility(StructuredDocument doc, ValidationProfile profile,
                                   ValidationResult result) {
        if (profile.requireTotal && doc.total() == null) {
            result.addViolation("grand total missing; cannot accept without one");
        }
        if (doc.total() != null) {
            if (doc.total().compareTo(Money.ZERO) <= 0) {
                result.addViolation("total not positive: " + doc.total());
            }
            if (doc.total().compareTo(MAX_TOTAL) > 0) {
                result.addViolation("total implausibly large: " + doc.total());
            }
            if (doc.taxSum().compareTo(doc.total()) > 0) {
                result.addViolation("tax sum exceeds total: " + doc.taxSum());
            }
        }
        if (doc.taxSum().compareTo(Money.ZERO) < 0) {
            result.addViolation("negative tax sum: " + doc.taxSum());
        }
        if (doc.change() != null && doc.change().compareTo(Money.ZERO) < 0) {
            result.addViolation("negative change: " + doc.change());
        }
        for (LineItem item : doc.lineItems()) {
            // Negative quantities are legitimate (returns/voids); zero and huge are misreads.
            // The >= 1000 bound catches SKU/UPC digits landing in the quantity column.
            if (item.quantity() != null) {
                if (item.quantity().signum() == 0) {
                    result.addViolation("zero quantity on '" + item.name() + "'");
                } else if (item.quantity().abs().compareTo(MAX_QUANTITY) >= 0) {
                    result.addViolation("implausible quantity on '" + item.name()
                            + "' (SKU/UPC misread?): " + item.quantity());
                }
            }
            if (item.unitPrice() != null && item.unitPrice().abs().compareTo(MAX_UNIT_PRICE) > 0) {
                result.addViolation("implausible unit price on '" + item.name() + "': "
                        + item.unitPrice());
            }
            if (item.lineTotal() != null && item.lineTotal().abs().compareTo(MAX_LINE_TOTAL) > 0) {
                result.addViolation("implausible line total on '" + item.name() + "': "
                        + item.lineTotal());
            }
        }
    }

    /** Self-heal budget: one repair per document, ever. */
    private static boolean mayRepair(boolean selfHeal, ValidationResult result) {
        return selfHeal && result.repairs().isEmpty();
    }
}
