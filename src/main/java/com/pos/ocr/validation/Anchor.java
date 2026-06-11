package com.pos.ocr.validation;

/** Canonical receipt/invoice anchor keywords used for fuzzy label binding. */
public enum Anchor {
    SUBTOTAL("SUBTOTAL", "SUBTOT"),
    GRAND_TOTAL("GRANDTOTAL"),
    TOTAL("TOTAL"),
    NET("NET", "NETTOTAL"),
    TAX("TAX"),
    VAT("VAT"),
    GST("GST"),
    AMOUNT_DUE("AMOUNTDUE", "BALANCEDUE"),
    CASH("CASH"),
    CHANGE("CHANGE"),
    TENDER("TENDER", "TENDERED"),
    DISCOUNT("DISCOUNT");

    private final String[] forms;

    Anchor(String... forms) {
        this.forms = forms;
    }

    public String[] forms() {
        return forms;
    }
}
