package com.pos.ocr;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.model.LineItem;
import com.pos.ocr.ocr.OcrEngine;
import com.pos.ocr.ocr.StubOcrEngine;
import com.pos.ocr.pipeline.ExtractionTier;
import com.pos.ocr.pipeline.OcrPipeline;
import com.pos.ocr.pipeline.ProcessingResult;
import com.pos.ocr.pipeline.ReviewQueue;
import com.pos.ocr.preprocessing.PreprocessingPipeline;
import com.pos.ocr.structuring.GeometryStructurer;
import com.pos.ocr.validation.MathValidator;
import com.pos.ocr.validation.ValidationResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Runnable demo of the full pipeline.
 *
 * <pre>
 *   java -cp target/classes com.pos.ocr.Demo                  # built-in scenarios, stub OCR
 *   java -cp ... com.pos.ocr.Demo path/to/receipt.jpg          # real image (needs a wired engine)
 * </pre>
 */
public final class Demo {

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--render")) {
            File out = new File(args[1]);
            ImageIO.write(renderSampleReceipt(), "png", out);
            System.out.println("Rendered sample receipt to " + out.getAbsolutePath());
            return;
        }
        if (args.length > 0) {
            runOnImage(args[0]);
            return;
        }
        runScenario("1. Clean receipt", cleanReceiptEngine(), sharpImage());
        runScenario("2. Misread TOTAL (math cannot close)", brokenTotalEngine(), sharpImage());
        runScenario("3. Blurry photo (quality gate)", cleanReceiptEngine(), blurryImage());
    }

    /** Render a realistic receipt as pixels — the real engine must genuinely read the glyphs. */
    private static BufferedImage renderSampleReceipt() {
        return com.pos.ocr.web.SampleReceipt.render();
    }

    private static void runOnImage(String path) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IllegalArgumentException("Cannot read image: " + path);
        OcrEngine engine = RealEngineFactory.create();
        runScenario("Real image: " + path, engine, img);
    }

    private static void runScenario(String title, OcrEngine engine, BufferedImage image) {
        System.out.println("============================================================");
        System.out.println(title);
        System.out.println("============================================================");

        ReviewQueue queue = new ReviewQueue();
        ExtractionTier tier = new ExtractionTier(engine.name() + "+geometry", engine,
                new GeometryStructurer());
        OcrPipeline pipeline = OcrPipeline.singleTier(
                new PreprocessingPipeline(), tier, new MathValidator(), queue);

        ProcessingResult r = pipeline.process(image, DocumentType.RECEIPT);

        System.out.println("Status     : " + r.status());
        System.out.println("Tier       : " + r.tierReached());
        System.out.printf("Confidence : %.3f%n", r.confidence());
        System.out.println("Quality    : " + r.quality().reason()
                + String.format(" (focus=%.0f)", r.quality().blurScore()));

        if (r.document() != null) {
            var d = r.document();
            System.out.println("Store      : " + d.storeName());
            System.out.println("Invoice    : " + d.invoiceId() + "   Date: " + d.date());
            for (LineItem li : d.lineItems()) {
                System.out.printf("  %-20s qty=%-5s unit=%-8s total=%s%n",
                        li.name(), li.quantity(), li.unitPrice(), li.lineTotal());
            }
            System.out.println("Subtotal   : " + d.subtotal()
                    + "   Tax: " + d.taxSum() + "   TOTAL: " + d.total());
            System.out.println("Tendered   : " + d.tendered() + "   Change: " + d.change());
        }
        if (r.validation() != null) {
            ValidationResult v = r.validation();
            System.out.println("Math checks:");
            for (ValidationResult.Check c : v.checks()) {
                System.out.printf("  [%s] %-28s expected=%s actual=%s%n",
                        c.passed() ? "PASS" : "FAIL", c.name(), c.expected(), c.actual());
            }
            v.repairs().forEach(rep -> System.out.println("  repaired: " + rep));
            v.violations().forEach(vio -> System.out.println("  VIOLATION: " + vio));
        }
        r.tierFailures().forEach(f -> System.out.println("  tier failure: " + f));
        System.out.println("Review queue size: " + queue.size());
        System.out.println();
    }

    // ---- fixtures (mirror the test fixtures, inlined so the demo ships in main sources) ----

    private static OcrEngine cleanReceiptEngine() {
        return new StubOcrEngine.Builder().rowHeight(30)
                .token("MARKET", 0.97, 0, 10, 90).token("FRESH", 0.97, 0, 110, 90)
                .token("Invoice", 0.96, 1, 10, 100).token("#R-9001", 0.96, 1, 120, 120)
                .token("2026-06-10", 0.96, 2, 10, 160)
                .token("Apple", 0.95, 3, 10, 80).token("2", 0.95, 3, 200, 30)
                .token("1.50", 0.95, 3, 260, 50).token("3.00", 0.95, 3, 330, 50)
                .token("Whole", 0.95, 4, 10, 70).token("Milk", 0.95, 4, 90, 60)
                .token("1", 0.95, 4, 200, 30).token("2.25", 0.95, 4, 260, 50)
                .token("2.25", 0.95, 4, 330, 50)
                .token("SUBTOTAL", 0.96, 5, 10, 140).token("5.25", 0.96, 5, 330, 50)
                .token("TAX", 0.96, 6, 10, 100).token("0.53", 0.96, 6, 330, 50)
                .token("TOTAL", 0.96, 7, 10, 120).token("5.78", 0.96, 7, 330, 50)
                .token("CASH", 0.96, 8, 10, 100).token("10.00", 0.96, 8, 330, 60)
                .token("CHANGE", 0.96, 9, 10, 120).token("4.22", 0.96, 9, 330, 50)
                .build();
    }

    private static OcrEngine brokenTotalEngine() {
        return new StubOcrEngine.Builder().rowHeight(30)
                .token("MARKET", 0.97, 0, 10, 90)
                .token("Apple", 0.95, 3, 10, 80).token("2", 0.95, 3, 200, 30)
                .token("1.50", 0.95, 3, 260, 50).token("3.00", 0.95, 3, 330, 50)
                .token("SUBTOTAL", 0.96, 5, 10, 140).token("3.00", 0.96, 5, 330, 50)
                .token("TAX", 0.96, 6, 10, 100).token("0.00", 0.96, 6, 330, 50)
                .token("TOTAL", 0.96, 7, 10, 120).token("9.78", 0.40, 7, 330, 50)
                .build();
    }

    private static BufferedImage sharpImage() {
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 400; x++) {
                boolean on = ((x / 4) + (y / 4)) % 2 == 0;
                int v = on ? 255 : 0;
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    private static BufferedImage blurryImage() {
        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        int p = (128 << 16) | (128 << 8) | 128;
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 400; x++) {
                img.setRGB(x, y, p);
            }
        }
        return img;
    }

    static final class RealEngineFactory {
        static OcrEngine create() {
            return new com.pos.ocr.ocr.RapidOcrRealEngine();
        }
    }
}
