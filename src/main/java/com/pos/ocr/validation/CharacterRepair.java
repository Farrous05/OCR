package com.pos.ocr.validation;

/**
 * Context-constrained character repair for numeric fields. OCR confuses letters and digits
 * ('B'/'8', 'l'/'1', 'O'/'0', 'S'/'5'). These substitutions are applied ONLY inside fields we
 * know must be numeric — never globally, which would corrupt item names.
 */
public final class CharacterRepair {

    private CharacterRepair() {
    }

    /** Map common letter misreads onto their digit counterparts. */
    public static String repairNumeric(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            char repaired = switch (c) {
                case 'O', 'o', 'Q', 'D' -> '0';
                case 'l', 'I', '|', 'i' -> '1';
                case 'Z', 'z' -> '2';
                case 'B' -> '8';
                case 'S', 's' -> '5';
                case 'g', 'q' -> '9';
                case 'G' -> '6';
                case 'T' -> '7';
                default -> c;
            };
            sb.append(repaired);
        }
        return sb.toString();
    }

    /** True if, after stripping currency/separators, the token is plausibly a number. */
    public static boolean looksNumeric(String raw) {
        if (raw == null) return false;
        String s = raw.replaceAll("[^0-9.,\\-]", "");
        return s.matches("-?\\d{1,3}([.,]\\d{3})*([.,]\\d+)?") || s.matches("-?\\d+([.,]\\d+)?");
    }
}
