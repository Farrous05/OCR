package com.pos.ocr.e2e;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.ocr.StubOcrEngine;
import com.pos.ocr.pipeline.ExtractionTier;
import com.pos.ocr.pipeline.OcrPipeline;
import com.pos.ocr.pipeline.ProcessingResult;
import com.pos.ocr.pipeline.ProcessingStatus;
import com.pos.ocr.pipeline.ReviewQueue;
import com.pos.ocr.preprocessing.PreprocessingPipeline;
import com.pos.ocr.serving.OcrService;
import com.pos.ocr.structuring.GeometryStructurer;
import com.pos.ocr.testutil.Fixtures;
import com.pos.ocr.testutil.TestImages;
import com.pos.ocr.validation.MathValidator;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** A single stateless pipeline served concurrently from a worker pool. */
class OcrServiceConcurrencyTest {

    @Test
    void handlesConcurrentRequestsConsistently() throws Exception {
        ReviewQueue queue = new ReviewQueue();
        ExtractionTier tier = new ExtractionTier("rapidocr+geometry",
                new StubOcrEngine(Fixtures.cleanReceiptTokens()), new GeometryStructurer());
        OcrPipeline pipeline = OcrPipeline.singleTier(
                new PreprocessingPipeline(), tier, new MathValidator(), queue);

        BufferedImage img = TestImages.sharpCheckerboard(400, 400, 4);
        int n = 64;

        try (OcrService service = new OcrService(pipeline, 8)) {
            List<Future<ProcessingResult>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(service.submit(img, DocumentType.RECEIPT));
            }
            int accepted = 0;
            for (Future<ProcessingResult> f : futures) {
                if (f.get().status() == ProcessingStatus.ACCEPTED) accepted++;
            }
            assertEquals(n, accepted, "all concurrent requests should be accepted deterministically");
        }
        assertEquals(0, queue.size());
    }
}
