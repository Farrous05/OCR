package com.pos.ocr.preprocessing;

import java.awt.image.BufferedImage;

/** Output of the pre-processing pipeline: cleaned image + diagnostics. */
public record PreprocessingResult(BufferedImage image, QualityReport quality, double deskewAngle) {

    public boolean passedQualityGate() {
        return quality.acceptable();
    }
}
