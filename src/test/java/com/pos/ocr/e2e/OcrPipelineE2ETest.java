package com.pos.ocr.e2e;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.model.Money;
import com.pos.ocr.ocr.StubOcrEngine;
import com.pos.ocr.pipeline.ExtractionTier;
import com.pos.ocr.pipeline.OcrPipeline;
import com.pos.ocr.pipeline.ProcessingResult;
import com.pos.ocr.pipeline.ProcessingStatus;
import com.pos.ocr.pipeline.ReviewQueue;
import com.pos.ocr.preprocessing.PreprocessingPipeline;
import com.pos.ocr.structuring.GeometryStructurer;
import com.pos.ocr.testutil.Fixtures;
import com.pos.ocr.testutil.TestImages;
import com.pos.ocr.validation.MathValidator;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Full Image -> Pre-processing -> OCR -> Structuring -> Validation -> disposition. */
class OcrPipelineE2ETest {

    private final BufferedImage goodImage = TestImages.sharpCheckerboard(400, 400, 4);

    private OcrPipeline pipelineFor(StubOcrEngine engine, ReviewQueue queue) {
        ExtractionTier tier = new ExtractionTier("rapidocr+geometry", engine, new GeometryStructurer());
        return OcrPipeline.singleTier(new PreprocessingPipeline(), tier, new MathValidator(), queue);
    }

    @Test
    void cleanReceiptIsAcceptedWithCorrectTotals() {
        ReviewQueue queue = new ReviewQueue();
        OcrPipeline pipeline = pipelineFor(new StubOcrEngine(Fixtures.cleanReceiptTokens()), queue);

        ProcessingResult result = pipeline.process(goodImage, DocumentType.RECEIPT);

        assertEquals(ProcessingStatus.ACCEPTED, result.status());
        assertTrue(result.validation().isValid());
        assertEquals(Money.of("5.78"), result.document().total());
        assertEquals("MARKET FRESH", result.document().storeName());
        assertEquals(0, queue.size());
        assertTrue(result.confidence() > 0.9);
    }

    @Test
    void blurryImageIsRejectedAtQualityGateWithoutOcr() {
        ReviewQueue queue = new ReviewQueue();
        OcrPipeline pipeline = pipelineFor(new StubOcrEngine(Fixtures.cleanReceiptTokens()), queue);

        ProcessingResult result = pipeline.process(TestImages.blurryUniform(400, 400, 128),
                DocumentType.RECEIPT);

        assertEquals(ProcessingStatus.REJECTED_QUALITY, result.status());
        assertNull(result.document(), "no OCR should have run on a rejected image");
    }

    @Test
    void mathInvalidDocumentGoesToReviewQueueNotAccepted() {
        ReviewQueue queue = new ReviewQueue();
        OcrPipeline pipeline = pipelineFor(new StubOcrEngine(Fixtures.brokenTotalTokens()), queue);

        ProcessingResult result = pipeline.process(goodImage, DocumentType.RECEIPT);

        assertEquals(ProcessingStatus.NEEDS_REVIEW, result.status());
        assertFalse(result.validation().isValid());
        assertEquals(1, queue.size(), "invalid document must be queued for human review");
    }

    @Test
    void failoverEscalatesToSecondTier() {
        ReviewQueue queue = new ReviewQueue();
        ExtractionTier weak = new ExtractionTier("rapidocr+geometry",
                new StubOcrEngine(Fixtures.brokenTotalTokens()), new GeometryStructurer());
        ExtractionTier strong = new ExtractionTier("vlm+geometry",
                new StubOcrEngine(Fixtures.cleanReceiptTokens()), new GeometryStructurer());
        OcrPipeline pipeline = new OcrPipeline(new PreprocessingPipeline(),
                List.of(weak, strong), new MathValidator(), queue, true);

        ProcessingResult result = pipeline.process(goodImage, DocumentType.RECEIPT);

        assertEquals(ProcessingStatus.ACCEPTED, result.status());
        assertEquals("vlm+geometry", result.tierReached(), "should fail over to the stronger tier");
        assertEquals(0, queue.size());
    }
}
