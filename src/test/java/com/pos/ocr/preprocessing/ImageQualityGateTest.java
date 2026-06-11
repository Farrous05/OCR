package com.pos.ocr.preprocessing;

import com.pos.ocr.testutil.TestImages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageQualityGateTest {

    private final ImageQualityGate gate = new ImageQualityGate();

    @Test
    void acceptsSharpAdequateImage() {
        QualityReport r = gate.assess(TestImages.sharpCheckerboard(400, 400, 4));
        assertTrue(r.acceptable(), r.reason());
        assertTrue(r.blurScore() > 100.0);
    }

    @Test
    void rejectsBlurryImage() {
        QualityReport r = gate.assess(TestImages.blurryUniform(400, 400, 128));
        assertFalse(r.acceptable());
        assertTrue(r.reason().contains("blurry"));
    }

    @Test
    void rejectsLowResolutionImage() {
        QualityReport r = gate.assess(TestImages.sharpCheckerboard(100, 100, 4));
        assertFalse(r.acceptable());
        assertTrue(r.reason().contains("Resolution"));
    }
}
