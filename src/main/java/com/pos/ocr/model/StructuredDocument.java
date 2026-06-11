package com.pos.ocr.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The structured output handed to the POS backend. Built by the structuring stage,
 * verified/repaired by the validation stage. Fields are nullable because real-world
 * documents omit them; the validator decides whether the populated subset is trustworthy.
 */
public final class StructuredDocument {

    private DocumentType type = DocumentType.GENERIC;
    private String storeName;
    private String date;
    private String invoiceId;

    private final List<LineItem> lineItems = new ArrayList<>();
    private final List<TaxLine> taxLines = new ArrayList<>();

    private Money subtotal;
    private Money total;
    private Money tendered;
    private Money change;

    public DocumentType type() { return type; }
    public void setType(DocumentType type) { this.type = type; }

    public String storeName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public String date() { return date; }
    public void setDate(String date) { this.date = date; }

    public String invoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public List<LineItem> lineItems() { return lineItems; }
    public void addLineItem(LineItem item) { lineItems.add(item); }

    public List<TaxLine> taxLines() { return taxLines; }
    public void addTaxLine(TaxLine tax) { taxLines.add(tax); }

    public Money subtotal() { return subtotal; }
    public void setSubtotal(Money subtotal) { this.subtotal = subtotal; }

    public Money total() { return total; }
    public void setTotal(Money total) { this.total = total; }

    public Money tendered() { return tendered; }
    public void setTendered(Money tendered) { this.tendered = tendered; }

    public Money change() { return change; }
    public void setChange(Money change) { this.change = change; }

    /** Sum of all line-item totals. */
    public Money lineItemSum() {
        Money sum = Money.ZERO;
        for (LineItem li : lineItems) {
            if (li.lineTotal() != null) sum = sum.add(li.lineTotal());
        }
        return sum;
    }

    /** Sum of all tax lines. */
    public Money taxSum() {
        Money sum = Money.ZERO;
        for (TaxLine t : taxLines) {
            if (t.amount() != null) sum = sum.add(t.amount());
        }
        return sum;
    }

    public List<LineItem> lineItemsView() {
        return Collections.unmodifiableList(lineItems);
    }
}
