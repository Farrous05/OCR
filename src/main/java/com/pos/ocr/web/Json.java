package com.pos.ocr.web;

import com.pos.ocr.model.LineItem;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.model.TaxLine;
import com.pos.ocr.pipeline.ProcessingResult;
import com.pos.ocr.validation.ValidationResult;

/**
 * Minimal hand-rolled JSON writer for {@link ProcessingResult} — keeps the web layer
 * dependency-free. Only writes; the API never parses client JSON.
 */
final class Json {

    private Json() {
    }

    static String of(ProcessingResult r, long millis) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append('{');
        field(sb, "status", r.status().name()).append(',');
        field(sb, "tier", r.tierReached()).append(',');
        sb.append("\"confidence\":").append(String.format("%.3f", r.confidence())).append(',');
        sb.append("\"millis\":").append(millis).append(',');
        field(sb, "quality", r.quality() == null ? null : r.quality().reason()).append(',');

        sb.append("\"document\":");
        document(sb, r.document());
        sb.append(',');

        sb.append("\"checks\":[");
        if (r.validation() != null) {
            boolean first = true;
            for (ValidationResult.Check c : r.validation().checks()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('{');
                field(sb, "name", c.name()).append(',');
                sb.append("\"passed\":").append(c.passed()).append(',');
                field(sb, "expected", c.expected()).append(',');
                field(sb, "actual", c.actual());
                sb.append('}');
            }
        }
        sb.append("],");

        stringArray(sb, "repairs", r.validation() == null ? null : r.validation().repairs());
        sb.append(',');
        stringArray(sb, "violations", r.validation() == null ? null : r.validation().violations());
        sb.append(',');
        stringArray(sb, "tierFailures", r.tierFailures());
        sb.append('}');
        return sb.toString();
    }

    private static void document(StringBuilder sb, StructuredDocument d) {
        if (d == null) {
            sb.append("null");
            return;
        }
        sb.append('{');
        field(sb, "store", d.storeName()).append(',');
        field(sb, "invoiceId", d.invoiceId()).append(',');
        field(sb, "date", d.date()).append(',');
        field(sb, "subtotal", str(d.subtotal())).append(',');
        field(sb, "tax", str(d.taxSum())).append(',');
        field(sb, "total", str(d.total())).append(',');
        field(sb, "tendered", str(d.tendered())).append(',');
        field(sb, "change", str(d.change())).append(',');

        sb.append("\"items\":[");
        boolean first = true;
        for (LineItem li : d.lineItems()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            field(sb, "name", li.name()).append(',');
            field(sb, "qty", li.quantity() == null ? null : li.quantity().toPlainString()).append(',');
            field(sb, "unit", str(li.unitPrice())).append(',');
            field(sb, "total", str(li.lineTotal()));
            sb.append('}');
        }
        sb.append("],");

        sb.append("\"taxLines\":[");
        first = true;
        for (TaxLine t : d.taxLines()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            field(sb, "label", t.label()).append(',');
            field(sb, "amount", str(t.amount()));
            sb.append('}');
        }
        sb.append("]}");
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static void stringArray(StringBuilder sb, String name, java.util.List<String> values) {
        sb.append('"').append(name).append("\":[");
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escape(values.get(i))).append('"');
            }
        }
        sb.append(']');
    }

    private static StringBuilder field(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(value)).append('"');
        }
        return sb;
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
