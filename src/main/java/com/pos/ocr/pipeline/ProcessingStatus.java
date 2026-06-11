package com.pos.ocr.pipeline;

/** Terminal disposition of a document through the pipeline. */
public enum ProcessingStatus {
    /** Validated (math closes); safe to persist to the POS. */
    ACCEPTED,
    /** Failed the Tier-0 quality gate; client should re-scan. Never OCR'd. */
    REJECTED_QUALITY,
    /** All extraction tiers exhausted without math-valid output; routed to human review. */
    NEEDS_REVIEW
}
