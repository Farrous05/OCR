package com.pos.ocr.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of deterministic validation: the individual math checks, any self-heal repairs,
 * plausibility violations, and a math-confidence score. The pipeline combines this with OCR
 * confidence to decide accept / escalate / human-review.
 *
 * <p>Validity is gated, not vacuous: a document is valid only when at least one BINDING
 * cross-field check actually executed, every executed check passed, and no plausibility
 * violation was recorded. An empty check list is never valid — "nothing was checkable" must
 * route to review, not to the POS ledger.
 */
public final class ValidationResult {

    /**
     * One executed math check. {@code binding} marks a comparison between two independently
     * extracted values (real cross-field evidence). A check against a value that was derived
     * from the very numbers it is compared to (e.g. a self-healed subtotal) is non-binding:
     * it documents the repair but can never be the sole evidence of validity.
     */
    public record Check(String name, boolean passed, String expected, String actual, boolean binding) {
    }

    private final List<Check> checks = new ArrayList<>();
    private final List<String> repairs = new ArrayList<>();
    private final List<String> violations = new ArrayList<>();

    public void addCheck(String name, boolean passed, String expected, String actual) {
        addCheck(name, passed, expected, actual, true);
    }

    public void addCheck(String name, boolean passed, String expected, String actual, boolean binding) {
        checks.add(new Check(name, passed, expected, actual, binding));
    }

    public void addRepair(String description) {
        repairs.add(description);
    }

    /** A plausibility-bound breach (absurd quantity, missing/negative total, ...). */
    public void addViolation(String description) {
        violations.add(description);
    }

    public List<Check> checks() {
        return Collections.unmodifiableList(checks);
    }

    public List<String> repairs() {
        return Collections.unmodifiableList(repairs);
    }

    public List<String> violations() {
        return Collections.unmodifiableList(violations);
    }

    /**
     * The correctness gate: at least one binding check executed, all executed checks passed,
     * zero violations. Deliberately strict — the cost of a false ACCEPT (corrupt ledger entry)
     * dwarfs the cost of a false review.
     */
    public boolean isValid() {
        return violations.isEmpty()
                && !checks.isEmpty()
                && checks.stream().allMatch(Check::passed)
                && checks.stream().anyMatch(Check::binding);
    }

    public long passedCount() {
        return checks.stream().filter(Check::passed).count();
    }

    /**
     * Fraction of checks passed; 0.0 when nothing was checkable. Absence of evidence is not
     * evidence of correctness — an unverifiable candidate must rank below a partially verified
     * one when the pipeline picks the best failure for human review.
     */
    public double mathScore() {
        if (checks.isEmpty()) return 0.0;
        return (double) passedCount() / checks.size();
    }

    public Check failedCheck() {
        return checks.stream().filter(c -> !c.passed()).findFirst().orElse(null);
    }
}
