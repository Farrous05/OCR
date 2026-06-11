package com.pos.ocr.preprocessing;

import com.pos.ocr.testutil.TestImages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeskewEstimatorTest {

    private final DeskewEstimator estimator = new DeskewEstimator();

    @Test
    void horizontalTextHasNoSkew() {
        double angle = estimator.estimateAngle(TestImages.tiltedStripes(300, 300, 20, 0));
        assertTrue(Math.abs(angle) <= 1.0, "expected ~0 skew but got " + angle);
    }

    @Test
    void recoversKnownTiltMagnitude() {
        double angle = estimator.estimateAngle(TestImages.tiltedStripes(300, 300, 20, 5));
        assertTrue(Math.abs(angle) >= 3.0 && Math.abs(angle) <= 7.0,
                "expected ~5deg skew magnitude but got " + angle);
    }
}
