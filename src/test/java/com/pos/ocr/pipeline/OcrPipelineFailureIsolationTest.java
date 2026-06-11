package com.pos.ocr.pipeline;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.ocr.OcrEngine;
import com.pos.ocr.ocr.OcrToken;
import com.pos.ocr.ocr.StubOcrEngine;
import com.pos.ocr.preprocessing.PreprocessingPipeline;
import com.pos.ocr.structuring.GeometryStructurer;
import com.pos.ocr.testutil.Fixtures;
import com.pos.ocr.testutil.TestImages;
import com.pos.ocr.validation.MathValidator;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two hard invariants of the tier ladder: a crashing tier demotes instead of failing the
 * request, and an exhausted deadline budget skips the remaining tiers. The worst case is always
 * a ProcessingResult, never an exception.
 */
class OcrPipelineFailureIsolationTest {

    private final BufferedImage goodImage = TestImages.sharpCheckerboard(400, 400, 4);

    /** Engine that fails the way broken native stacks fail. */
    private static OcrEngine throwing(String name, Throwable boom) {
        return new OcrEngine() {
            @Override
            public List<OcrToken> recognize(BufferedImage image) {
                if (boom instanceof RuntimeException re) throw re;
                if (boom instanceof Error err) throw err;
                throw new IllegalStateException(boom);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private static ExtractionTier tier(String name, OcrEngine engine) {
        return new ExtractionTier(name, engine, new GeometryStructurer());
    }

    @Test
    void engineExceptionDemotesToNextTier() {
        ReviewQueue queue = new ReviewQueue();
        OcrPipeline pipeline = new OcrPipeline(new PreprocessingPipeline(),
                List.of(tier("onnx-cpu", throwing("onnx-cpu", new RuntimeException("ort session crashed"))),
                        tier("vlm-gpu", new StubOcrEngine(Fixtures.cleanReceiptTokens()))),
                new MathValidator(), queue, true);

        ProcessingResult result = pipeline.process(goodImage, DocumentType.RECEIPT);

        assertEquals(ProcessingStatus.ACCEPTED, result.status());
        assertEquals("vlm-gpu", result.tierReached());
        assertEquals(1, result.tierFailures().size());
        assertTrue(result.tierFailures().get(0).contains("onnx-cpu"));
        assertEquals(0, queue.size());
    }

    @Test
    void nativeLinkageErrorIsContainedLikeAnyEngineFailure() {
        ReviewQueue queue = new ReviewQueue();
        OcrPipeline pipeline = new OcrPipeline(new PreprocessingPipeline(),
                List.of(tier("onnx-cpu", throwing("onnx-cpu", new UnsatisfiedLinkError("libonnxruntime.so missing"))),
                        tier("vlm-gpu", new StubOcrEngine(Fixtures.cleanReceiptTokens()))),
                new MathValidator(), queue, true);

        ProcessingResult result = pipeline.process(goodImage, DocumentType.RECEIPT);

        assertEquals(ProcessingStatus.ACCEPTED, result.status());
        assertEquals("vlm-gpu", result.tierReached());
    }

    @Test
    void allTiersCrashingStillYieldsAReviewableResult() {
        ReviewQueue queue = new ReviewQueue();
        OcrPipeline pipeline = new OcrPipeline(new PreprocessingPipeline(),
                List.of(tier("onnx-cpu", throwing("onnx-cpu", new RuntimeException("crash A"))),
                        tier("vlm-gpu", throwing("vlm-gpu", new RuntimeException("crash B")))),
                new MathValidator(), queue, true);

        ProcessingResult result = assertDoesNotThrow(
                () -> pipeline.process(goodImage, DocumentType.RECEIPT));

        assertEquals(ProcessingStatus.NEEDS_REVIEW, result.status());
        assertEquals("none", result.tierReached());
        assertNull(result.document());
        assertEquals(2, result.tierFailures().size());
        assertEquals(1, queue.size(), "the failure must still reach human review");
    }

    @Test
    void exhaustedDeadlineSkipsRemainingTiers() {
        ReviewQueue queue = new ReviewQueue();
        // 1ns budget: already exhausted by the time preprocessing finishes, so no tier may run.
        OcrPipeline pipeline = new OcrPipeline(new PreprocessingPipeline(),
                List.of(tier("onnx-cpu", new StubOcrEngine(Fixtures.cleanReceiptTokens())),
                        tier("vlm-gpu", new StubOcrEngine(Fixtures.cleanReceiptTokens()))),
                new MathValidator(), queue, true, Duration.ofNanos(1), null);

        ProcessingResult result = pipeline.process(goodImage, DocumentType.RECEIPT);

        assertEquals(ProcessingStatus.NEEDS_REVIEW, result.status());
        assertEquals("none", result.tierReached());
        assertEquals(2, result.tierFailures().size());
        assertTrue(result.tierFailures().get(0).contains("skipped"));
        assertEquals(1, queue.size());
    }
}
