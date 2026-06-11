package com.pos.ocr.model;

/** A single tax/VAT/GST line. */
public record TaxLine(String label, Money amount) {
}
