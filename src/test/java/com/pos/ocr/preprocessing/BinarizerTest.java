package com.pos.ocr.preprocessing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinarizerTest {

    @Test
    void otsuFindsThresholdBetweenTwoModes() {
        // Bimodal luminance: half dark (30), half light (220). Threshold should land between.
        double[][] lum = new double[10][10];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                lum[y][x] = x < 5 ? 30 : 220;
            }
        }
        int t = Binarizer.otsuThreshold(lum);
        // For a clean bimodal split Otsu places the threshold at the lower mode boundary:
        // pixels <= t are dark/background, the rest foreground.
        assertTrue(t >= 30 && t < 220, "threshold should separate the two modes, got " + t);
    }

    @Test
    void binarizeProducesOnlyZeroAnd255() {
        double[][] lum = {{10, 200}, {50, 240}};
        int[][] bin = Binarizer.binarize(lum);
        for (int[] row : bin) {
            for (int v : row) {
                assertTrue(v == 0 || v == 255);
            }
        }
    }
}
