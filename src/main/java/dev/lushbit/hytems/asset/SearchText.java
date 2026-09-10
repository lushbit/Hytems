package dev.lushbit.hytems.asset;

import java.text.Normalizer;
import java.util.Locale;

/** Search normalization shared by item and mob browsers. */
public final class SearchText {
    private SearchText() {
    }

    public static boolean matches(String candidate, String query) {
        String haystack = normalize(candidate);
        String needle = normalize(query);
        if (needle.isEmpty() || haystack.contains(needle)) return true;

        String[] candidateWords = haystack.split(" ");
        for (String queryWord : needle.split(" ")) {
            if (queryWord.isEmpty()) continue;
            boolean matched = false;
            for (String candidateWord : candidateWords) {
                if (candidateWord.contains(queryWord)
                        || (queryWord.length() >= 4 && oneEditApart(candidateWord, queryWord))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    static boolean oneEditApart(String left, String right) {
        if (left.equals(right)) return true;
        int difference = Math.abs(left.length() - right.length());
        if (difference > 1) return false;

        if (left.length() == right.length()) {
            int first = -1;
            int mismatches = 0;
            for (int i = 0; i < left.length(); i++) {
                if (left.charAt(i) == right.charAt(i)) continue;
                if (first < 0) first = i;
                mismatches++;
                if (mismatches > 2) return false;
            }
            return mismatches <= 1 || (mismatches == 2 && first + 1 < left.length()
                    && left.charAt(first) == right.charAt(first + 1)
                    && left.charAt(first + 1) == right.charAt(first));
        }

        String shorter = left.length() < right.length() ? left : right;
        String longer = left.length() < right.length() ? right : left;
        int shortIndex = 0;
        int longIndex = 0;
        boolean skipped = false;
        while (shortIndex < shorter.length() && longIndex < longer.length()) {
            if (shorter.charAt(shortIndex) == longer.charAt(longIndex)) {
                shortIndex++;
                longIndex++;
            } else if (skipped) {
                return false;
            } else {
                skipped = true;
                longIndex++;
            }
        }
        return true;
    }
}
