package com.pos.ocr.model;

/**
 * Document class. The engine is general-purpose; the type only selects
 * pre-processing presets and the math-validation profile — it does not
 * change detection/recognition/structuring.
 */
public enum DocumentType {
    RECEIPT,
    INVOICE,
    GENERIC
}
