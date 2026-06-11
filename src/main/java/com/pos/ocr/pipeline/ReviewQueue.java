package com.pos.ocr.pipeline;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe sink for documents that failed every extraction tier. In production this is a
 * durable queue feeding a human-review UI (with all candidate JSONs, bboxes, and the failed
 * check highlighted) and the retraining flywheel. Here it is an in-memory stand-in.
 */
public final class ReviewQueue {

    private final ConcurrentLinkedQueue<ProcessingResult> queue = new ConcurrentLinkedQueue<>();

    public void submit(ProcessingResult result) {
        queue.add(result);
    }

    public int size() {
        return queue.size();
    }

    public ProcessingResult poll() {
        return queue.poll();
    }

    public List<ProcessingResult> drain() {
        return List.copyOf(queue);
    }
}
