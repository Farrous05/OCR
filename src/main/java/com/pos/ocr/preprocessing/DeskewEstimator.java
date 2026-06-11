package com.pos.ocr.preprocessing;

/**
 * Projection-profile deskew. For each candidate angle we rotate the dark-pixel mask and
 * measure the variance of the horizontal projection (row sums). Text aligned to rows
 * produces sharp peaks/troughs -> high variance. The maximising angle is the skew.
 */
public final class DeskewEstimator {

    private final double maxAngleDeg;
    private final double stepDeg;
    private final double darkThreshold;

    public DeskewEstimator() {
        this(10.0, 0.5, 128.0);
    }

    public DeskewEstimator(double maxAngleDeg, double stepDeg, double darkThreshold) {
        this.maxAngleDeg = maxAngleDeg;
        this.stepDeg = stepDeg;
        this.darkThreshold = darkThreshold;
    }

    /** Returns the estimated skew angle in degrees (positive = counter-clockwise correction). */
    public double estimateAngle(double[][] lum) {
        double bestAngle = 0;
        double bestScore = -1;
        for (double angle = -maxAngleDeg; angle <= maxAngleDeg + 1e-9; angle += stepDeg) {
            double score = projectionVariance(lum, angle);
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }

    private double projectionVariance(double[][] lum, double angleDeg) {
        int h = lum.length;
        int w = lum[0].length;
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double cx = w / 2.0;
        double cy = h / 2.0;

        double[] rowDark = new double[h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Map destination (x,y) back to source via inverse rotation.
                double dx = x - cx;
                double dy = y - cy;
                int sx = (int) Math.round(cx + dx * cos + dy * sin);
                int sy = (int) Math.round(cy - dx * sin + dy * cos);
                if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
                    if (lum[sy][sx] < darkThreshold) {
                        rowDark[y] += 1.0;
                    }
                }
            }
        }
        double mean = 0;
        for (double v : rowDark) mean += v;
        mean /= h;
        double var = 0;
        for (double v : rowDark) var += (v - mean) * (v - mean);
        return var / h;
    }
}
