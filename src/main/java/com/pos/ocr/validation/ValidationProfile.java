package com.pos.ocr.validation;

import com.pos.ocr.model.DocumentType;

/**
 * Selects which math checks apply for a document class and how printed tax relates to the
 * grand total. The engine is general; only this profile is document-specific.
 */
public enum ValidationProfile {

    /** US-style retail receipt: tax added on top of the subtotal. */
    RECEIPT(true, true, true, true, TaxMode.ADD_ON, true),
    /** EU/VAT-style receipt: item prices are gross, the tax line is informational. */
    RECEIPT_TAX_INCLUSIVE(true, true, true, true, TaxMode.INCLUSIVE, true),
    INVOICE(true, true, true, false, TaxMode.ADD_ON, true),
    INVOICE_TAX_INCLUSIVE(true, true, true, false, TaxMode.INCLUSIVE, true),
    GENERIC(true, true, false, false, TaxMode.ADD_ON, false);

    /** How the printed tax participates in the total equation. */
    public enum TaxMode {
        /** subtotal + tax == total (tax charged on top). */
        ADD_ON,
        /** sum(items) == total; tax is informational ("incl. VAT"). A printed subtotal may be
         *  gross (== total) or net (subtotal + tax == total) — both conventions exist. */
        INCLUSIVE
    }

    public final boolean checkSubtotal;   // sum(line items) vs subtotal
    public final boolean checkTotal;      // total equation per taxMode
    public final boolean checkLineMath;   // qty * unit == line total
    public final boolean checkTender;     // tendered - total == change
    public final TaxMode taxMode;
    /** When true, a document without a grand total can never be ACCEPTED. */
    public final boolean requireTotal;

    ValidationProfile(boolean checkSubtotal, boolean checkTotal, boolean checkLineMath,
                      boolean checkTender, TaxMode taxMode, boolean requireTotal) {
        this.checkSubtotal = checkSubtotal;
        this.checkTotal = checkTotal;
        this.checkLineMath = checkLineMath;
        this.checkTender = checkTender;
        this.taxMode = taxMode;
        this.requireTotal = requireTotal;
    }

    public static ValidationProfile forType(DocumentType type) {
        return switch (type) {
            case RECEIPT -> RECEIPT;
            case INVOICE -> INVOICE;
            case GENERIC -> GENERIC;
        };
    }
}
