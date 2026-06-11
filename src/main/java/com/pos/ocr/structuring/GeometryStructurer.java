package com.pos.ocr.structuring;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.model.LineItem;
import com.pos.ocr.model.Money;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.model.TaxLine;
import com.pos.ocr.ocr.OcrToken;
import com.pos.ocr.validation.Anchor;
import com.pos.ocr.validation.AnchorMatcher;
import com.pos.ocr.validation.NumericNormalizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Geometry-driven structuring: reconstruct rows, then classify each row as metadata,
 * an anchor/total line, or a line item — using token positions and fuzzy anchor matching.
 * Document-agnostic by design; {@link DocumentType} only tags the output.
 */
public final class GeometryStructurer implements DocumentStructurer {

    // Findable (not anchored with .*) so the leftmost full date wins, not a digit suffix.
    private static final Pattern DATE = Pattern.compile(
            "(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})");
    private static final Pattern INVOICE = Pattern.compile(
            "(?i).*\\b(invoice|inv|receipt|bill|order)\\b\\s*#?\\s*[:.]?\\s*([A-Za-z0-9-]+).*");

    private final TableReconstructor reconstructor;
    private final AnchorMatcher anchorMatcher;

    public GeometryStructurer() {
        this(new TableReconstructor(), new AnchorMatcher());
    }

    public GeometryStructurer(TableReconstructor reconstructor, AnchorMatcher anchorMatcher) {
        this.reconstructor = reconstructor;
        this.anchorMatcher = anchorMatcher;
    }

    @Override
    public StructuredDocument structure(List<OcrToken> tokens, DocumentType type) {
        StructuredDocument doc = new StructuredDocument();
        doc.setType(type);
        List<List<OcrToken>> rows = reconstructor.reconstructRows(tokens);

        for (List<OcrToken> row : rows) {
            classifyRow(row, doc);
        }
        return doc;
    }

    private void classifyRow(List<OcrToken> row, StructuredDocument doc) {
        String fullText = joinText(row);

        // 1. Anchor / total line takes priority (leading label fuzzy-matches a known anchor).
        Optional<AnchorMatcher.Match> anchor = leadingAnchor(row);
        if (anchor.isPresent()) {
            applyAnchor(anchor.get().anchor(), row, doc);
            return;
        }

        // 2. Metadata: date and invoice id are recognised by their specific patterns and must
        //    win over line-item classification (an invoice id contains digits too).
        var dateMatch = DATE.matcher(fullText);
        if (doc.date() == null && dateMatch.find()) {
            doc.setDate(dateMatch.group(1));
            return;
        }
        var inv = INVOICE.matcher(fullText);
        if (doc.invoiceId() == null && inv.matches()) {
            doc.setInvoiceId(inv.group(2));
            return;
        }

        List<Integer> numericIdx = numericIndices(row);

        // 3. Line item: leading text name + trailing numerics (qty / unit / total).
        if (!numericIdx.isEmpty() && hasLeadingText(row, numericIdx)) {
            addLineItem(row, numericIdx, doc);
            return;
        }

        // 4. Pure-text row near the top with no numerics -> store name.
        if (doc.storeName() == null && numericIdx.isEmpty() && !fullText.isBlank()) {
            doc.setStoreName(fullText);
        }
    }

    private void applyAnchor(Anchor anchor, List<OcrToken> row, StructuredDocument doc) {
        Optional<Money> value = lastMoney(row);
        if (value.isEmpty()) return;
        Money v = value.get();
        switch (anchor) {
            case SUBTOTAL, NET -> { if (doc.subtotal() == null) doc.setSubtotal(v); }
            case TOTAL, GRAND_TOTAL, AMOUNT_DUE -> doc.setTotal(v);
            case TAX, VAT, GST -> doc.addTaxLine(new TaxLine(anchor.name(), v));
            case CASH, TENDER -> doc.setTendered(v);
            case CHANGE -> doc.setChange(v);
            case DISCOUNT -> { /* tracked as negative adjustment if needed */ }
        }
    }

    private void addLineItem(List<OcrToken> row, List<Integer> numericIdx, StructuredDocument doc) {
        int firstNumeric = numericIdx.get(0);
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < firstNumeric; i++) {
            if (name.length() > 0) name.append(' ');
            name.append(row.get(i).text());
        }
        List<Money> numbers = new ArrayList<>();
        for (int idx : numericIdx) {
            NumericNormalizer.parseMoneyWithRepair(row.get(idx).text()).ifPresent(numbers::add);
        }
        if (numbers.isEmpty()) return;

        Money lineTotal = numbers.get(numbers.size() - 1);
        BigDecimal qty = null;
        Money unit = null;
        if (numbers.size() >= 3) {
            qty = numbers.get(0).value();
            unit = numbers.get(1);
        } else if (numbers.size() == 2) {
            qty = numbers.get(0).value();
        }
        doc.addLineItem(new LineItem(name.toString().trim(), qty, unit, lineTotal));
    }

    // ---- helpers ----

    private Optional<AnchorMatcher.Match> leadingAnchor(List<OcrToken> row) {
        StringBuilder label = new StringBuilder();
        for (OcrToken t : row) {
            if (isNumeric(t.text())) break;
            label.append(t.text());
        }
        return anchorMatcher.match(label.toString());
    }

    private List<Integer> numericIndices(List<OcrToken> row) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            if (isNumeric(row.get(i).text())) idx.add(i);
        }
        return idx;
    }

    private boolean hasLeadingText(List<OcrToken> row, List<Integer> numericIdx) {
        return numericIdx.get(0) > 0;
    }

    private Optional<Money> lastMoney(List<OcrToken> row) {
        for (int i = row.size() - 1; i >= 0; i--) {
            if (isNumeric(row.get(i).text())) {
                return NumericNormalizer.parseMoneyWithRepair(row.get(i).text());
            }
        }
        return Optional.empty();
    }

    private boolean isNumeric(String text) {
        return text != null && text.matches(".*\\d.*")
                && NumericNormalizer.parseMoney(text).isPresent();
    }

    private String joinText(List<OcrToken> row) {
        StringBuilder sb = new StringBuilder();
        for (OcrToken t : row) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(t.text());
        }
        return sb.toString();
    }
}
