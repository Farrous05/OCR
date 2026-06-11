package com.pos.ocr.structuring;

import com.pos.ocr.model.BoundingBox;
import com.pos.ocr.ocr.OcrToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableReconstructorTest {

    private final TableReconstructor reconstructor = new TableReconstructor();

    @Test
    void groupsTokensByRowAndOrdersLeftToRight() {
        // Two rows; tokens supplied out of order to prove sorting works.
        OcrToken r1c2 = new OcrToken("3.00", 0.9, new BoundingBox(300, 10, 40, 20));
        OcrToken r1c1 = new OcrToken("Apple", 0.9, new BoundingBox(10, 12, 80, 20));
        OcrToken r2c1 = new OcrToken("Bread", 0.9, new BoundingBox(10, 60, 80, 20));
        OcrToken r2c2 = new OcrToken("2.25", 0.9, new BoundingBox(300, 58, 40, 20));

        List<List<OcrToken>> rows = reconstructor.reconstructRows(List.of(r1c2, r2c2, r1c1, r2c1));

        assertEquals(2, rows.size());
        assertEquals("Apple", rows.get(0).get(0).text());
        assertEquals("3.00", rows.get(0).get(1).text());
        assertEquals("Bread", rows.get(1).get(0).text());
        assertEquals("2.25", rows.get(1).get(1).text());
    }

    @Test
    void slightVerticalJitterStaysInSameRow() {
        OcrToken a = new OcrToken("A", 0.9, new BoundingBox(10, 10, 20, 20));
        OcrToken b = new OcrToken("B", 0.9, new BoundingBox(40, 13, 20, 20)); // 3px jitter
        List<List<OcrToken>> rows = reconstructor.reconstructRows(List.of(a, b));
        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).size());
    }
}
