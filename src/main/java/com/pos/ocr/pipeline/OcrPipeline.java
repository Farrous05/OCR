package com.pos.ocr.pipeline;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.ocr.OcrToken;
import com.pos.ocr.preprocessing.PreprocessingPipeline;
import com.pos.ocr.preprocessing.PreprocessingResult;
import com.pos.ocr.validation.MathValidator;
import com.pos.ocr.validation.ValidationProfile;
import com.pos.ocr.validation.ValidationResult;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase-4 orchestration: the tiered failure ladder.
 *
 * <pre>
 *   Tier 0  quality gate          -> fail: REJECT_QUALITY (re-scan, never OCR garbage)
 *   Tier 1..N  engine+structurer  -> first math-valid result: ACCEPT
 *   Final   none valid            -> best candidate to ReviewQueue: NEEDS_REVIEW
 * </pre>
 *
 * <p>Two invariants hold no matter what the tiers do:
 * <ul>
 *   <li><b>Failure isolation.</b> Each tier runs inside its own error boundary. A native engine
 *       crash (ONNX, JNI link failure, OOM during inference, gRPC error) is recorded as a tier
 *       failure and demotes to the next tier — it never escapes {@code process()}. The worst
 *       case is always a {@link ProcessingResult}, never a stack trace.</li>
 *   <li><b>Deadline budget.</b> Every request carries a wall-clock budget (default
 *       {@link #DEFAULT_DEADLINE}) checked before each tier — the only safe preemption point,
 *       since a native inference call cannot be interrupted once started. Exhausted budget skips
 *       the remaining (slower) tiers and routes the best candidate so far to review, so degraded
 *       inputs cannot pin workers for the sum of all tier latencies.</li>
 * </ul>
 *
 * A math-invalid document is never returned as ACCEPTED — it is demoted to the next tier or to
 * human review. This is what lets the engine be general-purpose yet safe.
 */
public final class OcrPipeline {

    /** Default whole-request wall-clock budget across all tiers. */
    public static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(10);

    private final PreprocessingPipeline preprocessing;
    private final List<ExtractionTier> tiers;
    private final MathValidator validator;
    private final ReviewQueue reviewQueue;
    private final boolean selfHeal;
    private final long deadlineNanosBudget;
    private final ValidationProfile profileOverride; // null -> derive from DocumentType

    public OcrPipeline(PreprocessingPipeline preprocessing, List<ExtractionTier> tiers,
                       MathValidator validator, ReviewQueue reviewQueue, boolean selfHeal) {
        this(preprocessing, tiers, validator, reviewQueue, selfHeal, DEFAULT_DEADLINE, null);
    }

    /**
     * @param deadline        total wall-clock budget per request, spanning all tiers
     * @param profileOverride explicit validation profile (e.g. RECEIPT_TAX_INCLUSIVE for a VAT
     *                        region); null derives the profile from the document type
     */
    public OcrPipeline(PreprocessingPipeline preprocessing, List<ExtractionTier> tiers,
                       MathValidator validator, ReviewQueue reviewQueue, boolean selfHeal,
                       Duration deadline, ValidationProfile profileOverride) {
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("at least one extraction tier required");
        }
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("deadline must be positive: " + deadline);
        }
        this.preprocessing = preprocessing;
        this.tiers = List.copyOf(tiers);
        this.validator = validator;
        this.reviewQueue = reviewQueue;
        this.selfHeal = selfHeal;
        this.deadlineNanosBudget = deadline.toNanos();
        this.profileOverride = profileOverride;
    }

    public ProcessingResult process(BufferedImage image, DocumentType type) {
        final long deadlineNanos = System.nanoTime() + deadlineNanosBudget;

        // Tier 0: quality gate.
        PreprocessingResult pre = preprocessing.process(image);
        if (!pre.passedQualityGate()) {
            return ProcessingResult.rejectedQuality(pre.quality());
        }

        ValidationProfile profile = profileOverride != null
                ? profileOverride : ValidationProfile.forType(type);
        ProcessingResult best = null;
        List<String> failures = new ArrayList<>();

        // Tiers 1..N: stop at the first math-valid extraction.
        for (int i = 0; i < tiers.size(); i++) {
            if (System.nanoTime() - deadlineNanos >= 0) {
                for (int j = i; j < tiers.size(); j++) {
                    failures.add(tiers.get(j).name() + ": skipped (deadline budget exhausted)");
                }
                break;
            }

            ExtractionTier tier = tiers.get(i);
            ProcessingResult candidate;
            try {
                List<OcrToken> tokens = tier.engine().recognize(pre.image());
                StructuredDocument doc = tier.structurer().structure(tokens, type);
                ValidationResult vr = validator.validate(doc, profile, selfHeal);
                double confidence = avgConfidence(tokens) * vr.mathScore();
                candidate = new ProcessingResult(
                        vr.isValid() ? ProcessingStatus.ACCEPTED : ProcessingStatus.NEEDS_REVIEW,
                        tier.name(), doc, vr, confidence, pre.quality(), List.copyOf(failures));
            } catch (Exception | LinkageError | OutOfMemoryError | StackOverflowError e) {
                // LinkageError/OOM/SOE are exactly what broken native engines throw; anything
                // more fundamental (other VirtualMachineErrors) should still take the JVM down.
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                failures.add(tier.name() + ": " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " - " + e.getMessage()));
                continue;
            }

            if (candidate.accepted()) {
                return candidate;
            }
            if (best == null || candidate.confidence() > best.confidence()) {
                best = candidate;
            }
        }

        // Final: nothing validated -> human review with the strongest candidate and the full
        // failure log. Even all-tiers-crashed yields a reviewable result, never an exception.
        ProcessingResult terminal = best != null
                ? new ProcessingResult(best.status(), best.tierReached(), best.document(),
                        best.validation(), best.confidence(), best.quality(), List.copyOf(failures))
                : new ProcessingResult(ProcessingStatus.NEEDS_REVIEW, "none", null,
                        new ValidationResult(), 0.0, pre.quality(), List.copyOf(failures));
        reviewQueue.submit(terminal);
        return terminal;
    }

    private static double avgConfidence(List<OcrToken> tokens) {
        if (tokens.isEmpty()) return 0.0;
        double sum = 0;
        for (OcrToken t : tokens) sum += t.confidence();
        return sum / tokens.size();
    }

    /** Convenience factory for a single-tier pipeline. */
    public static OcrPipeline singleTier(PreprocessingPipeline pre, ExtractionTier tier,
                                         MathValidator validator, ReviewQueue queue) {
        return new OcrPipeline(pre, List.of(tier), validator, queue, true);
    }
}
