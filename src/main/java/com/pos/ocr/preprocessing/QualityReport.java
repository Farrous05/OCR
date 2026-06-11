package com.pos.ocr.preprocessing;

/**
 * Result of the Tier-0 quality gate. {@code acceptable=false} means the image should be
 * rejected to the client for re-scan rather than fed to OCR (never OCR garbage).
 */
public record QualityReport(boolean acceptable, double blurScore, int width, int height, String reason) {

    public static QualityReport ok(double blurScore, int width, int height) {
        return new QualityReport(true, blurScore, width, height, "OK");
    }

    public static QualityReport reject(double blurScore, int width, int height, String reason) {
        return new QualityReport(false, blurScore, width, height, reason);
    }
}
