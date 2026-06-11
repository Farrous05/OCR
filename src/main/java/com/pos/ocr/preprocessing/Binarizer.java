package com.pos.ocr.preprocessing;

/**
 * Otsu global thresholding. Used ONLY for the Tesseract fallback path — modern detectors
 * (PaddleOCR/RapidOCR) prefer grayscale input, so the deep-learning path skips this.
 */
public final class Binarizer {

    private Binarizer() {
    }

    /** Returns the Otsu threshold (0..255) maximising inter-class variance. */
    public static int otsuThreshold(double[][] lum) {
        int[] hist = new int[256];
        long total = 0;
        for (double[] row : lum) {
            for (double v : row) {
                int b = Math.min(255, Math.max(0, (int) Math.round(v)));
                hist[b]++;
                total++;
            }
        }
        double sum = 0;
        for (int t = 0; t < 256; t++) sum += (double) t * hist[t];

        double sumB = 0;
        long wB = 0;
        double maxVar = -1;
        int threshold = 0;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            long wF = total - wB;
            if (wF == 0) break;
            sumB += (double) t * hist[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > maxVar) {
                maxVar = between;
                threshold = t;
            }
        }
        return threshold;
    }

    /** Binarize to a 0/255 mask (255 = foreground/dark text becomes 0 background 255). */
    public static int[][] binarize(double[][] lum) {
        int t = otsuThreshold(lum);
        int h = lum.length;
        int w = lum[0].length;
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y][x] = lum[y][x] <= t ? 0 : 255;
            }
        }
        return out;
    }
}
