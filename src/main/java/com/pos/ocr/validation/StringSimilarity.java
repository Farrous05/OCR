package com.pos.ocr.validation;

/**
 * Pure-Java string similarity (no external deps). Levenshtein edit distance plus a
 * normalized similarity and Jaro-Winkler, used for fuzzy anchor-word matching where OCR
 * mangles keywords ("TOIAL", "5UBTOTAL").
 */
public final class StringSimilarity {

    private StringSimilarity() {
    }

    public static int levenshtein(String a, String b) {
        a = a == null ? "" : a;
        b = b == null ? "" : b;
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /** 1.0 == identical, 0.0 == maximally different. */
    public static double normalizedSimilarity(String a, String b) {
        a = a == null ? "" : a;
        b = b == null ? "" : b;
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        return 1.0 - ((double) levenshtein(a, b) / max);
    }

    public static double jaroWinkler(String a, String b) {
        double jaro = jaro(a, b);
        int prefix = 0;
        int maxPrefix = Math.min(4, Math.min(a.length(), b.length()));
        for (int i = 0; i < maxPrefix; i++) {
            if (a.charAt(i) == b.charAt(i)) prefix++;
            else break;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    private static double jaro(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int matchDistance = Math.max(a.length(), b.length()) / 2 - 1;
        boolean[] aMatched = new boolean[a.length()];
        boolean[] bMatched = new boolean[b.length()];
        int matches = 0;
        for (int i = 0; i < a.length(); i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, b.length());
            for (int j = start; j < end; j++) {
                if (bMatched[j] || a.charAt(i) != b.charAt(j)) continue;
                aMatched[i] = true;
                bMatched[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;
        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < a.length(); i++) {
            if (!aMatched[i]) continue;
            while (!bMatched[k]) k++;
            if (a.charAt(i) != b.charAt(k)) transpositions++;
            k++;
        }
        transpositions /= 2;
        double m = matches;
        return (m / a.length() + m / b.length() + (m - transpositions) / m) / 3.0;
    }
}
