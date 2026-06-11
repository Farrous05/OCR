package com.pos.ocr.ocr;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Stage-A contract: detect + recognize text, returning tokens with geometry and confidence.
 *
 * <p>Production implementations adapt PaddleOCR/PP-OCR models via RapidOcr-Java or DJL
 * (in-JVM, ONNX). Tests use a deterministic {@link StubOcrEngine} so the full pipeline runs
 * without model weights or a GPU.
 */
public interface OcrEngine {

    List<OcrToken> recognize(BufferedImage image);

    /** Human-readable engine identifier for logging / tier attribution. */
    String name();
}
