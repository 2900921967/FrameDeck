package com.ruih.framedeck;

/** Compares digit runs by numeric value without integer overflow. */
final class NaturalFileOrder {
    private NaturalFileOrder() { }

    static int compare(String left, String right) {
        String a = left.replace('\\', '/'), b = right.replace('\\', '/');
        int i = 0, j = 0, paddingTie = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (digit(x) && digit(y)) {
                int startA = i, startB = j;
                while (i < a.length() && digit(a.charAt(i))) i++;
                while (j < b.length() && digit(b.charAt(j))) j++;
                int significantA = startA, significantB = startB;
                while (significantA < i && a.charAt(significantA) == '0') significantA++;
                while (significantB < j && b.charAt(significantB) == '0') significantB++;
                int lengths = Integer.compare(i - significantA, j - significantB);
                if (lengths != 0) return lengths;
                while (significantA < i) {
                    int difference = a.charAt(significantA++) - b.charAt(significantB++);
                    if (difference != 0) return difference;
                }
                // Compare later text/numbers before using zero padding as a tie-breaker.
                if (paddingTie == 0) paddingTie = Integer.compare(i - startA, j - startB);
            } else {
                int difference = Character.toLowerCase(x) - Character.toLowerCase(y);
                if (difference != 0) return difference;
                i++; j++;
            }
        }
        int remaining = Integer.compare(a.length() - i, b.length() - j);
        if (remaining != 0) return remaining;
        return paddingTie != 0 ? paddingTie : left.compareTo(right);
    }

    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
}
