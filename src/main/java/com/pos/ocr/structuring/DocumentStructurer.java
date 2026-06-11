package com.pos.ocr.structuring;

import com.pos.ocr.model.DocumentType;
import com.pos.ocr.model.StructuredDocument;
import com.pos.ocr.ocr.OcrToken;

import java.util.List;

/**
 * Stage-B contract: turn positioned tokens into a {@link StructuredDocument}. The geometry
 * implementation generalizes across document classes; a Donut/VLM implementation could plug in
 * behind the same interface and be reconciled against this one in validation.
 */
public interface DocumentStructurer {

    StructuredDocument structure(List<OcrToken> tokens, DocumentType type);
}
