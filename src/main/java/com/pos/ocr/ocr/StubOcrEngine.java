package com.pos.ocr.ocr;

import com.pos.ocr.model.BoundingBox;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic OCR engine for tests and offline pipeline exercise. It ignores the image
 * pixels and returns a pre-seeded token list, letting integration/e2e tests verify the
 * structuring + validation + orchestration logic without ML weights.
 */
public final class StubOcrEngine implements OcrEngine {

    private final List<OcrToken> tokens;
    private final String name;

    public StubOcrEngine(List<OcrToken> tokens) {
        this(tokens, "stub");
    }

    public StubOcrEngine(List<OcrToken> tokens, String name) {
        this.tokens = new ArrayList<>(tokens);
        this.name = name;
    }

    @Override
    public List<OcrToken> recognize(BufferedImage image) {
        return new ArrayList<>(tokens);
    }

    @Override
    public String name() {
        return name;
    }

    /** Fluent builder that lays tokens out on a simple grid for readable test fixtures. */
    public static final class Builder {
        private final List<OcrToken> tokens = new ArrayList<>();
        private int rowHeight = 30;

        public Builder rowHeight(int h) {
            this.rowHeight = h;
            return this;
        }

        /** Place a token at logical (row, xStart) with a given width. */
        public Builder token(String text, double confidence, int row, int xStart, int width) {
            int y = row * rowHeight;
            tokens.add(new OcrToken(text, confidence,
                    new BoundingBox(xStart, y, width, rowHeight - 6)));
            return this;
        }

        public StubOcrEngine build() {
            return new StubOcrEngine(tokens);
        }

        public List<OcrToken> tokens() {
            return new ArrayList<>(tokens);
        }
    }
}
