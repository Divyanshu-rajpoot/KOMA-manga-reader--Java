package app.koma.archive;

import java.util.Comparator;

/** "page2" before "page10"; case-insensitive; folder paths compare segment by segment. */
public final class NaturalSort implements Comparator<String> {
    public static final NaturalSort INSTANCE = new NaturalSort();

    private NaturalSort() {}

    @Override
    public int compare(String a, String b) {
        int i = 0, j = 0;
        int na = a.length(), nb = b.length();
        while (i < na && j < nb) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (si < na && a.charAt(si) == '0') si++;
                while (sj < nb && b.charAt(sj) == '0') sj++;
                int ei = si, ej = sj;
                while (ei < na && Character.isDigit(a.charAt(ei))) ei++;
                while (ej < nb && Character.isDigit(b.charAt(ej))) ej++;
                int lenA = ei - si, lenB = ej - sj;
                if (lenA != lenB) return lenA - lenB;
                for (int k = 0; k < lenA; k++) {
                    int d = a.charAt(si + k) - b.charAt(sj + k);
                    if (d != 0) return d;
                }
                // equal value: fewer leading zeros first ("1" < "01")
                int za = si - i, zb = sj - j;
                if (za != zb) return za - zb;
                i = ei;
                j = ej;
            } else {
                // a path separator sorts before any other character so "a/1" < "a b/1"
                if (ca != cb) {
                    if (isSep(ca) && !isSep(cb)) return -1;
                    if (isSep(cb) && !isSep(ca)) return 1;
                    int d = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                    if (d != 0) return d;
                }
                i++;
                j++;
            }
        }
        int d = (na - i) - (nb - j);
        return d != 0 ? d : a.compareTo(b);
    }

    private static boolean isSep(char c) {
        return c == '/' || c == '\\';
    }
}
