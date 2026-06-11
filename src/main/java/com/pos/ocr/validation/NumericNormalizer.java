package com.pos.ocr.validation;

import com.pos.ocr.model.Money;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Locale-aware numeric parsing for OCR'd money/quantity strings. Handles currency glyphs,
 * thousands/decimal separator ambiguity ("1.234,56" vs "1,234.56"), and falls back to
 * constrained {@link CharacterRepair} when a direct parse fails.
 */
public final class NumericNormalizer {

    private NumericNormalizer() {
    }

    public static Optional<Money> parseMoney(String raw) {
        return canonical(raw).map(Money::of);
    }

    /** Parse, and if that fails apply numeric character repair and parse again. */
    public static Optional<Money> parseMoneyWithRepair(String raw) {
        Optional<Money> direct = parseMoney(raw);
        if (direct.isPresent()) return direct;
        return parseMoney(CharacterRepair.repairNumeric(raw));
    }

    public static Optional<BigDecimal> parseQuantity(String raw) {
        return canonical(raw).map(BigDecimal::new);
    }

    /** Normalize an OCR'd numeric string to a canonical "[-]digits.digits" BigDecimal string. */
    static Optional<String> canonical(String raw) {
        if (raw == null) return Optional.empty();
        // Letters inside a numeric token signal OCR confusion (e.g. 'l2.5O'). Fail here so the
        // caller's repair path engages, rather than silently dropping the misread characters.
        if (raw.matches(".*[A-Za-z].*")) return Optional.empty();
        boolean negative = raw.contains("-") || raw.contains("(");
        String s = raw.replaceAll("[^0-9.,]", "");
        if (s.isEmpty() || !s.matches(".*\\d.*")) return Optional.empty();

        boolean hasDot = s.indexOf('.') >= 0;
        boolean hasComma = s.indexOf(',') >= 0;

        String normalized;
        if (hasDot && hasComma) {
            // The rightmost separator is the decimal point; the other groups thousands.
            int lastDot = s.lastIndexOf('.');
            int lastComma = s.lastIndexOf(',');
            char decimal = lastDot > lastComma ? '.' : ',';
            char thousands = decimal == '.' ? ',' : '.';
            normalized = s.replace(String.valueOf(thousands), "")
                    .replace(decimal, '.');
        } else if (hasComma) {
            normalized = disambiguateSingle(s, ',');
        } else if (hasDot) {
            normalized = disambiguateSingle(s, '.');
        } else {
            normalized = s;
        }

        normalized = normalized.replaceAll("[^0-9.]", "");
        if (normalized.isEmpty() || normalized.equals(".")) return Optional.empty();
        try {
            BigDecimal bd = new BigDecimal(normalized);
            if (negative) bd = bd.negate();
            return Optional.of(bd.toPlainString());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** A single separator type: decide whether it is a decimal point or a thousands grouper. */
    private static String disambiguateSingle(String s, char sep) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == sep) count++;
        int last = s.lastIndexOf(sep);
        int trailing = s.length() - last - 1;
        // Multiple occurrences, or exactly-3 trailing digits -> thousands grouping (strip).
        if (count > 1 || trailing == 3) {
            return s.replace(String.valueOf(sep), "");
        }
        // Otherwise it is the decimal separator.
        return s.replace(sep, '.');
    }
}
