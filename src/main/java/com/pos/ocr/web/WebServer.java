package com.pos.ocr.web;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.ocr.OcrEngine;
import com.pos.ocr.ocr.RapidOcrRealEngine;
import com.pos.ocr.pipeline.ExtractionTier;
import com.pos.ocr.pipeline.OcrPipeline;
import com.pos.ocr.pipeline.ProcessingResult;
import com.pos.ocr.pipeline.ReviewQueue;
import com.pos.ocr.preprocessing.PreprocessingPipeline;
import com.pos.ocr.structuring.GeometryStructurer;
import com.pos.ocr.validation.MathValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Zero-dependency test UI for the pipeline, on the JDK's built-in {@link HttpServer}.
 *
 * <pre>
 *   GET  /            single-page UI (drag & drop a receipt image)
 *   POST /api/ocr     raw image bytes in the body -> ProcessingResult JSON
 *   GET  /api/sample  rendered sample receipt PNG (to try without a photo)
 * </pre>
 *
 * This is a test bench, not the production edge: no auth, no TLS, binds localhost only.
 */
public final class WebServer {

    /** Cap uploads at 20 MB — a phone photo is well under this; anything bigger is abuse. */
    private static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

    private final OcrPipeline pipeline;
    private final ReviewQueue reviewQueue = new ReviewQueue();
    private final HttpServer server;

    public WebServer(int port, OcrEngine engine) throws IOException {
        ExtractionTier tier = new ExtractionTier(engine.name() + "+geometry", engine,
                new GeometryStructurer());
        this.pipeline = OcrPipeline.singleTier(
                new PreprocessingPipeline(), tier, new MathValidator(), reviewQueue);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/ocr", this::handleOcr);
        server.createContext("/api/sample", this::handleSample);
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        WebServer ws = new WebServer(port, new RapidOcrRealEngine());
        ws.start();
        System.out.println("Receipt OCR test UI -> http://localhost:" + ws.port());
        System.out.println("(first request pays ~seconds of ONNX session init)");
        Thread.currentThread().join();
    }

    // ---- handlers ----

    private void handleIndex(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] html = loadResource("/web/index.html");
        respond(ex, 200, "text/html; charset=utf-8", html);
    }

    private void handleOcr(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "text/plain", "POST an image body".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] body = readBounded(ex.getRequestBody());
        if (body == null) {
            respond(ex, 413, "text/plain", "image too large".getBytes(StandardCharsets.UTF_8));
            return;
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(body));
        if (img == null) {
            respond(ex, 400, "application/json",
                    "{\"error\":\"not a decodable image (use PNG/JPEG)\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        long t0 = System.currentTimeMillis();
        ProcessingResult result = pipeline.process(img, DocumentType.RECEIPT);
        long millis = System.currentTimeMillis() - t0;
        respond(ex, 200, "application/json",
                Json.of(result, millis).getBytes(StandardCharsets.UTF_8));
    }

    private void handleSample(HttpExchange ex) throws IOException {
        BufferedImage sample = SampleReceipt.render();
        var baos = new java.io.ByteArrayOutputStream();
        ImageIO.write(sample, "png", baos);
        respond(ex, 200, "image/png", baos.toByteArray());
    }

    // ---- plumbing ----

    private static byte[] readBounded(InputStream in) throws IOException {
        var baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_UPLOAD_BYTES) return null;
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static byte[] loadResource(String path) throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing classpath resource: " + path);
            return in.readAllBytes();
        }
    }

    private static void respond(HttpExchange ex, int code, String contentType, byte[] body)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
