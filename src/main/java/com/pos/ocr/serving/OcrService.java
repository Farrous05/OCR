package com.pos.ocr.serving;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.pipeline.OcrPipeline;
import com.pos.ocr.pipeline.ProcessingResult;

import java.awt.image.BufferedImage;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency front-end. The CPU hot path (OCR + geometry + validation) is stateless and
 * thread-safe per call, so a single {@link OcrPipeline} is shared across a fixed worker pool.
 *
 * <p><b>Backpressure, not buffering.</b> The queue is a bounded {@link ArrayBlockingQueue} of
 * {@code 2 × workers}; overflow triggers {@link ThreadPoolExecutor.CallerRunsPolicy}, which runs
 * the task on the submitting thread. The ingest layer therefore slows down at exactly the rate
 * the workers can sustain, instead of accumulating multi-megabyte {@code BufferedImage}s on the
 * heap until the JVM dies. An unbounded queue in front of an OCR pipeline is a deferred OOM.
 *
 * <p>Pool sizing: OCR work is CPU-bound and the underlying inference runtime may use intra-op
 * threads of its own — default to half the cores and tune with the runtime's thread settings
 * (e.g. ONNX Runtime {@code intra_op=1..2}) so total threads ≈ cores.
 *
 * <p>Shutdown contract: stop submitting before {@link #close()}. Tasks rejected after shutdown
 * are dropped by CallerRunsPolicy (their futures never complete) — that is JDK behavior, and the
 * reason ingest must drain first.
 */
public final class OcrService implements AutoCloseable {

    private final OcrPipeline pipeline;
    private final ThreadPoolExecutor executor;

    public OcrService(OcrPipeline pipeline) {
        this(pipeline, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public OcrService(OcrPipeline pipeline, int workers) {
        int w = Math.max(1, workers);
        this.pipeline = pipeline;
        this.executor = new ThreadPoolExecutor(
                w, w,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2 * w),
                new WorkerThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** Synchronous processing for in-JVM POS calls (bypasses the queue entirely). */
    public ProcessingResult process(BufferedImage image, DocumentType type) {
        return pipeline.process(image, type);
    }

    /**
     * Async submission. When the bounded queue is full this runs the pipeline on the CALLING
     * thread before returning — by design: the caller pays the cost of its own burst.
     */
    public Future<ProcessingResult> submit(BufferedImage image, DocumentType type) {
        return executor.submit(() -> pipeline.process(image, type));
    }

    /** Instantaneous queue depth — export as a gauge; sustained saturation means undersized pool. */
    public int queueDepth() {
        return executor.getQueue().size();
    }

    public int activeWorkers() {
        return executor.getActiveCount();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ocr-worker-" + seq.incrementAndGet());
            t.setDaemon(false);
            return t;
        }
    }
}
