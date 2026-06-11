package com.pos.ocr.preprocessing;

import java.awt.image.BufferedImage;

/**
 * Tier-0 quality gate. Rejects images that are too small or too blurry to OCR reliably,
 * using variance-of-Laplacian as a focus metric (low variance == blurry).
 */
public final class ImageQualityGate {

    private final double minBlurScore;
    private final int minWidth;
    private final int minHeight;

    public ImageQualityGate() {
        this(100.0, 200, 200);
    }

    public ImageQualityGate(double minBlurScore, int minWidth, int minHeight) {
        this.minBlurScore = minBlurScore;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    public QualityReport assess(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double blur = varianceOfLaplacian(GrayscaleOps.luminance(img));

        if (w < minWidth || h < minHeight) {
            return QualityReport.reject(blur, w, h,
                    "Resolution too low: " + w + "x" + h + " (min " + minWidth + "x" + minHeight + ")");
        }
        if (blur < minBlurScore) {
            return QualityReport.reject(blur, w, h,
                    "Image too blurry: focus score " + String.format("%.1f", blur)
                            + " < " + minBlurScore);
        }
        return QualityReport.ok(blur, w, h);
    }

    /** Variance of the Laplacian: convolve with the 4-neighbour Laplacian, return variance. */
    public static double varianceOfLaplacian(double[][] lum) {
        int h = lum.length;
        int w = lum[0].length;
        double sum = 0;
        double sumSq = 0;
        long n = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                double lap = lum[y - 1][x] + lum[y + 1][x] + lum[y][x - 1] + lum[y][x + 1]
                        - 4 * lum[y][x];
                sum += lap;
                sumSq += lap * lap;
                n++;
            }
        }
        if (n == 0) return 0;
        double mean = sum / n;
        return (sumSq / n) - (mean * mean);
    }
}
