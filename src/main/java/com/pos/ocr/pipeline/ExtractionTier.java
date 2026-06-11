package com.pos.ocr.pipeline;

import com.pos.ocr.ocr.OcrEngine;
import com.pos.ocr.structuring.DocumentStructurer;

/**
 * One rung of the failure ladder: an OCR engine paired with a structurer. Cheap CPU tiers go
 * first (RapidOCR + geometry); GPU VLM tiers (Donut / DeepSeek-OCR) come later, only for
 * documents the cheaper tiers cannot validate.
 */
public record ExtractionTier(String name, OcrEngine engine, DocumentStructurer structurer) {
}
