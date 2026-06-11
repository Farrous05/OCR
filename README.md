# POS Receipt OCR Pipeline

Self-hosted, **general-document** OCR pipeline (receipt-optimized) for a Java POS system.
Zero cloud APIs. Deterministic math is the final arbiter of correctness — models propose,
arithmetic disposes.

## Design boundary

Engines sit **behind interfaces** (`OcrEngine`, `DocumentStructurer`): `RapidOcrRealEngine`
is the production Stage-A engine (PP-OCRv4 via RapidOCR's ONNX runtime, in-JVM, models +
natives bundled by `rapidocr-onnx-platform`); a deterministic `StubOcrEngine` drives the test
suite so it never depends on model inference. Everything deterministic — pre-processing
metrics, geometry structuring, **all validation**, and orchestration — is covered by 59 tests.

Generality: structuring is geometry/anchor driven, not per-merchant templated. A receipt is a
narrow one-table document; an invoice a wider one — same algorithm. `DocumentType` only selects
a pre-processing preset and a math `ValidationProfile`; it does not change detect/recognize/structure.

## Pipeline flow

```
Image
  │  Phase 1  preprocessing/
  ▼  quality gate (variance-of-Laplacian) → grayscale → deskew
[Tier 0]  fail → REJECTED_QUALITY (re-scan, never OCR garbage)
  │  Phase 2  ocr/ + structuring/
  ▼  OcrEngine (RapidOcrRealEngine | StubOcrEngine) → OcrToken[] (text+bbox+conf)
     TableReconstructor → rows → GeometryStructurer → StructuredDocument
  │  Phase 3  validation/
  ▼  NumericNormalizer + CharacterRepair + AnchorMatcher (Levenshtein)
     MathValidator: Σitems==subtotal, total per TaxMode (ADD_ON: subtotal+tax==total,
     INCLUSIVE: Σitems==total), qty*unit==line, tender-total==change
     + plausibility bounds (no absurd qty/totals) + adaptive epsilon (0.005 × nItems)
     + self-heal (max ONE repair; a derived value is never the sole evidence)
  │  Phase 4  pipeline/ + serving/
  ▼  OcrPipeline tiered ladder: first math-valid tier → ACCEPTED
     per-tier error boundary (engine crash ⇒ demote, never throw)
     per-request deadline budget (default 10s) checked between tiers
     none valid → best candidate + tier-failure log → ReviewQueue → NEEDS_REVIEW
     OcrService: bounded queue (2×workers) + CallerRunsPolicy backpressure
```

A math-invalid document is **never** returned as ACCEPTED — it is demoted to the next tier or
to human review. Acceptance is gated, not vacuous: it requires a grand total, at least one
binding cross-field check, and zero plausibility violations — "nothing was checkable" routes
to review, not to the ledger.

## Repo-list mapping (production wiring)

| Role | Choice | License |
|------|--------|---------|
| Detect/recognize | PaddleOCR models via **RapidOcr-Java**/DJL (ONNX, in-JVM) | Apache-2.0 |
| Structuring | geometry-first now; **Donut** fine-tuned next | MIT |
| Hard-case GPU tier | DeepSeek-OCR / dots.ocr (gRPC) | MIT |
| Deterministic floor | Tesseract + invoice2data (opportunistic cache) | Apache/MIT |
| Avoid | LayoutLMv3 (CC-BY-NC), MinerU (AGPL); flag Surya (revenue cap) | — |

`RapidOcrRealEngine` is the wired Stage-A adapter (PP-OCRv4, ONNX, in-JVM). Donut/DeepSeek-OCR
plug in behind the same `OcrEngine`/`DocumentStructurer` seams as later tiers.

## Build & test

```bash
mvn test      # 59 tests: unit + integration + e2e + concurrency + failure isolation
mvn package
```

Requires JDK 21+.

## Try it

```bash
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
CP="target/classes:$(cat target/cp.txt)"

# 1. Built-in scenarios with the stub engine (accept / review / quality-reject):
java -cp "$CP" com.pos.ocr.Demo

# 2. Real OCR (PP-OCRv4, in-JVM): render a sample receipt, then read it for real.
#    First run downloads nothing extra but pays ~seconds of native session init.
java -cp "$CP" com.pos.ocr.Demo --render /tmp/receipt.png
java -cp "$CP" com.pos.ocr.Demo /tmp/receipt.png

# 3. Your own receipt photo:
java -cp "$CP" com.pos.ocr.Demo path/to/your-receipt.jpg
```

The demo prints the disposition (ACCEPTED / NEEDS_REVIEW / REJECTED_QUALITY), extracted
metadata, line items, every math check with expected-vs-actual, repairs, and violations.

## Module map

- `model/` — `Money` (BigDecimal, never double), `BoundingBox`, `LineItem`, `StructuredDocument`
- `preprocessing/` — `ImageQualityGate`, `DeskewEstimator`, `Binarizer`, `PreprocessingPipeline`
- `ocr/` — `OcrEngine`, `StubOcrEngine`, `RapidOcrRealEngine` (PP-OCRv4, in-JVM)
- `structuring/` — `TableReconstructor`, `GeometryStructurer`
- `validation/` — `NumericNormalizer`, `StringSimilarity`, `AnchorMatcher`, `CharacterRepair`,
  `MathValidator`, `ValidationProfile`
- `pipeline/` — `OcrPipeline`, `ExtractionTier`, `ReviewQueue`, `ProcessingResult`
- `serving/` — `OcrService` (concurrent front-end)
