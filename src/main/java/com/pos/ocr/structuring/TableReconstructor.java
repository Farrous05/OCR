package com.pos.ocr.structuring;

import com.pos.ocr.ocr.OcrToken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generic geometry-based table reconstruction. Groups tokens into rows by vertical overlap,
 * then orders each row left-to-right. Document-agnostic: a receipt is a narrow one-table
 * document, an invoice a wider one — the same clustering applies.
 */
public final class TableReconstructor {

    private final double overlapThreshold;

    public TableReconstructor() {
        this(0.4);
    }

    public TableReconstructor(double overlapThreshold) {
        this.overlapThreshold = overlapThreshold;
    }

    /** Returns rows top-to-bottom; each row's tokens left-to-right. */
    public List<List<OcrToken>> reconstructRows(List<OcrToken> tokens) {
        List<OcrToken> sorted = new ArrayList<>(tokens);
        sorted.sort(Comparator.comparingInt(t -> t.box().centerY()));

        List<List<OcrToken>> rows = new ArrayList<>();
        for (OcrToken token : sorted) {
            List<OcrToken> target = null;
            for (List<OcrToken> row : rows) {
                // Compare against the row's first token as the representative baseline.
                if (row.get(0).box().verticalOverlap(token.box()) >= overlapThreshold) {
                    target = row;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                rows.add(target);
            }
            target.add(token);
        }
        for (List<OcrToken> row : rows) {
            row.sort(Comparator.comparingInt(t -> t.box().x()));
        }
        rows.sort(Comparator.comparingInt(r -> r.get(0).box().centerY()));
        return rows;
    }
}
