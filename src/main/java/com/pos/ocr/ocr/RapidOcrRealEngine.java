package com.pos.ocr.ocr;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.pos.ocr.model.BoundingBox;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Production Stage-A engine: PP-OCRv4 (DBNet detect + angle cls + recognizer) via RapidOCR's
 * ONNX runtime, fully in-JVM (Apache-2.0; natives + models bundled by rapidocr-onnx-platform).
 *
 * <p>The native API is path-based, so the image is staged to a temp PNG per call. The detector's
 * quad box points are collapsed to the axis-aligned {@link BoundingBox} the geometry structurer
 * expects. {@code getBoxScore()} (detection confidence) is used as the token confidence.
 *
 * <p>{@code InferenceEngine.getInstance} is a process-wide singleton holding a native session;
 * first call pays model extraction + session init (~seconds), subsequent calls are fast.
 */
public final class RapidOcrRealEngine implements OcrEngine {

    private final InferenceEngine engine;

    public RapidOcrRealEngine() {
        this.engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
    }

    @Override
    public List<OcrToken> recognize(BufferedImage image) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("ocr-", ".png");
            ImageIO.write(image, "png", tmp.toFile());
            OcrResult result = engine.runOcr(tmp.toString());
            return toTokens(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stage image for OCR", e);
        } finally {
            if (tmp != null) {
                tmp.toFile().delete();
            }
        }
    }

    private static List<OcrToken> toTokens(OcrResult result) {
        List<OcrToken> tokens = new ArrayList<>();
        if (result == null || result.getTextBlocks() == null) return tokens;
        for (TextBlock block : result.getTextBlocks()) {
            String text = block.getText();
            if (text == null || text.isBlank()) continue;
            tokens.add(new OcrToken(text.trim(), block.getBoxScore(), toBox(block.getBoxPoint())));
        }
        return tokens;
    }

    private static BoundingBox toBox(List<Point> quad) {
        if (quad == null || quad.isEmpty()) return new BoundingBox(0, 0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : quad) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
        }
        return new BoundingBox(minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY));
    }

    @Override
    public String name() {
        return "rapidocr-ppocrv4";
    }

    /** Used by the Demo to stage a file path directly (skips the temp re-encode). */
    public List<OcrToken> recognizeFile(File imageFile) {
        return toTokens(engine.runOcr(imageFile.getAbsolutePath()));
    }
}
