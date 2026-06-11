package com.pos.ocr.preprocessing;

import java.awt.image.BufferedImage;

/** Grayscale conversion and luminance extraction. Pure Java, no native deps. */
public final class GrayscaleOps {

    private GrayscaleOps() {
    }

    /** Rec. 601 luma. Returns a [height][width] array in 0..255. */
    public static double[][] luminance(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                out[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
            }
        }
        return out;
    }

    public static BufferedImage toGray(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        double[][] lum = luminance(img);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = (int) Math.round(lum[y][x]);
                int p = (v << 16) | (v << 8) | v;
                gray.setRGB(x, y, p);
            }
        }
        return gray;
    }
}
