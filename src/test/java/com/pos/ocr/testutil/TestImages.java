package com.pos.ocr.testutil;

import java.awt.image.BufferedImage;

/** Synthetic image factories so tests need no fixture files or models. */
public final class TestImages {

    private TestImages() {
    }

    /** High-frequency checkerboard -> high Laplacian variance (passes the focus gate). */
    public static BufferedImage sharpCheckerboard(int w, int h, int block) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean on = ((x / block) + (y / block)) % 2 == 0;
                int v = on ? 255 : 0;
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    /** Flat gray -> near-zero Laplacian variance (fails the focus gate). */
    public static BufferedImage blurryUniform(int w, int h, int gray) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int p = (gray << 16) | (gray << 8) | gray;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, p);
            }
        }
        return img;
    }

    /**
     * Luminance field of horizontal "text" stripes tilted by {@code angleDeg}. Used to verify
     * the deskew estimator recovers the tilt.
     */
    public static double[][] tiltedStripes(int w, int h, int period, double angleDeg) {
        double[][] lum = new double[h][w];
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double cx = w / 2.0;
        double cy = h / 2.0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - cx;
                double dy = y - cy;
                double ry = -dx * sin + dy * cos; // rotated vertical coordinate
                int band = (int) Math.floor(((ry % period) + period) % period);
                lum[y][x] = band < period / 2 ? 0 : 255; // dark stripe / light gap
            }
        }
        return lum;
    }
}
