package com.pos.ocr.model;

/**
 * Axis-aligned bounding box in pixel coordinates (origin top-left).
 * Geometry helpers here drive the table-reconstruction stage.
 */
public record BoundingBox(int x, int y, int width, int height) {

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    /** Vertical overlap ratio against another box (0..1), relative to the smaller height. */
    public double verticalOverlap(BoundingBox other) {
        int top = Math.max(this.y, other.y);
        int bot = Math.min(this.bottom(), other.bottom());
        int overlap = Math.max(0, bot - top);
        int minHeight = Math.max(1, Math.min(this.height, other.height));
        return (double) overlap / minHeight;
    }
}
