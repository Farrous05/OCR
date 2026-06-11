package com.pos.ocr.model;

import java.math.BigDecimal;

/**
 * A single line item. {@code quantity} and {@code unitPrice} may be null when the
 * source layout omits them (common on retail receipts that print only name + total).
 */
public final class LineItem {

    private final String name;
    private final BigDecimal quantity;
    private final Money unitPrice;
    private Money lineTotal;

    public LineItem(String name, BigDecimal quantity, Money unitPrice, Money lineTotal) {
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public static LineItem of(String name, Money lineTotal) {
        return new LineItem(name, null, null, lineTotal);
    }

    public String name() {
        return name;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public Money lineTotal() {
        return lineTotal;
    }

    /** Used by the self-healing loop to repair a misread line total. */
    public void overrideLineTotal(Money repaired) {
        this.lineTotal = repaired;
    }

    /** True when qty and unitPrice are both present (enables qty*unit==total check). */
    public boolean hasUnitMath() {
        return quantity != null && unitPrice != null;
    }

    @Override
    public String toString() {
        return "LineItem{name='" + name + "', qty=" + quantity
                + ", unit=" + unitPrice + ", total=" + lineTotal + '}';
    }
}
