package com.pos.ocr.preprocessing;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Phase 1 orchestration: quality gate -> grayscale -> deskew.
 *
 * <p>Deliberately NOT binarized: the deep-learning OCR path consumes grayscale. Perspective
 * correction / illumination normalization are the responsibility of a JavaCV/OpenCV-backed
 * implementation behind the same shape; this pure-Java pipeline covers the deterministic,
 * unit-testable core (quality, deskew angle, grayscale).
 */
public final class PreprocessingPipeline {

    private final ImageQualityGate qualityGate;
    private final DeskewEstimator deskewEstimator;

    public PreprocessingPipeline() {
        this(new ImageQualityGate(), new DeskewEstimator());
    }

    public PreprocessingPipeline(ImageQualityGate qualityGate, DeskewEstimator deskewEstimator) {
        this.qualityGate = qualityGate;
        this.deskewEstimator = deskewEstimator;
    }

    public PreprocessingResult process(BufferedImage input) {
        QualityReport report = qualityGate.assess(input);
        if (!report.acceptable()) {
            // Short-circuit: do not waste work deskewing an image we will reject.
            return new PreprocessingResult(input, report, 0.0);
        }
        double[][] lum = GrayscaleOps.luminance(input);
        double angle = deskewEstimator.estimateAngle(lum);
        BufferedImage gray = GrayscaleOps.toGray(input);
        BufferedImage deskewed = Math.abs(angle) < 1e-6 ? gray : rotate(gray, angle);
        return new PreprocessingResult(deskewed, report, angle);
    }

    /** Rotate by the estimated skew so text becomes horizontal. */
    static BufferedImage rotate(BufferedImage src, double angleDeg) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, src.getType() == 0
                ? BufferedImage.TYPE_INT_RGB : src.getType());
        Graphics2D g = dst.createGraphics();
        AffineTransform at = AffineTransform.getRotateInstance(
                Math.toRadians(-angleDeg), w / 2.0, h / 2.0);
        g.setTransform(at);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }
}
