package com.pos.ocr.pipeline;

import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.preprocessing.QualityReport;
import com.pos.ocr.validation.ValidationResult;

import java.util.List;

/**
 * Immutable outcome of a single document run: disposition, the tier that produced it,
 * the structured document, its validation, a combined confidence score, and the per-tier
 * failure log (engine crashes, deadline skips) for the review UI and ops dashboards.
 *
 * <p>This is the pipeline's only output type — a request never surfaces an exception.
 */
public record ProcessingResult(
        ProcessingStatus status,
        String tierReached,
        StructuredDocument document,
        ValidationResult validation,
        double confidence,
        QualityReport quality,
        List<String> tierFailures) {

    public ProcessingResult {
        tierFailures = tierFailures == null ? List.of() : List.copyOf(tierFailures);
    }

    public boolean accepted() {
        return status == ProcessingStatus.ACCEPTED;
    }

    public static ProcessingResult rejectedQuality(QualityReport quality) {
        return new ProcessingResult(ProcessingStatus.REJECTED_QUALITY,
                "quality-gate", null, null, 0.0, quality, List.of());
    }
}
