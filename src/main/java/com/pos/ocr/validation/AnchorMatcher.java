package com.pos.ocr.validation;

import java.util.Optional;

/**
 * Fuzzy binds an OCR'd label token to a canonical {@link Anchor} using normalized Levenshtein
 * similarity. Tolerates OCR mangling ("TOIAL" -> TOTAL, "5UBTOTAL" -> SUBTOTAL) while rejecting
 * unrelated words.
 */
public final class AnchorMatcher {

    private final double threshold;

    public AnchorMatcher() {
        this(0.8);
    }

    public AnchorMatcher(double threshold) {
        this.threshold = threshold;
    }

    public record Match(Anchor anchor, double similarity) {
    }

    public Optional<Match> match(String rawLabel) {
        if (rawLabel == null) return Optional.empty();
        String norm = rawLabel.toUpperCase().replaceAll("[^A-Z]", "");
        if (norm.isEmpty()) return Optional.empty();

        Anchor best = null;
        double bestSim = -1;
        for (Anchor anchor : Anchor.values()) {
            for (String form : anchor.forms()) {
                double sim = StringSimilarity.normalizedSimilarity(norm, form);
                if (sim > bestSim) {
                    bestSim = sim;
                    best = anchor;
                }
            }
        }
        if (best != null && bestSim >= threshold) {
            return Optional.of(new Match(best, bestSim));
        }
        return Optional.empty();
    }
}
