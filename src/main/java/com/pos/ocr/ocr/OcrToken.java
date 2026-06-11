package com.pos.ocr.ocr;

import com.pos.ocr.model.BoundingBox;

/**
 * One recognized text fragment with its geometry and recognizer confidence.
 * The bounding box is what lets the validation stage audit VLM output against
 * ground-truth tokens (a value with no box is a hallucination).
 */
public record OcrToken(String text, double confidence, BoundingBox box) {

    public OcrToken {
        if (text == null) text = "";
    }
}
