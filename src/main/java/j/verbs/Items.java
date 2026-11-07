package j.verbs;

import java.util.ArrayList;
import java.util.List;

import j.array.JArray;
import j.array.JType;

/** Item-level equality and classification shared by structural and comparison verbs. */
final class Items {
    /** J's default comparison tolerance, 2^-44. */
    private static final double TOLERANCE = Math.scalb(1.0, -44);

    private Items() {
    }

    /** Whether two items have the same shape and equal atoms. */
    static boolean equal(JArray a, JArray b) {
        if (!a.sameShape(b)) {
            return false;
        }
        if (a.type() == JType.CHAR || b.type() == JType.CHAR) {
            if (a.type() != b.type()) {
                return false;
            }
            for (int atom = 0; atom < a.size(); atom++) {
                if (a.charAt(atom) != b.charAt(atom)) {
                    return false;
                }
            }
            return true;
        }
        if (a.type() == JType.INT && b.type() == JType.INT) {
            for (int atom = 0; atom < a.size(); atom++) {
                if (a.longAt(atom) != b.longAt(atom)) {
                    return false;
                }
            }
            return true;
        }
        for (int atom = 0; atom < a.size(); atom++) {
            if (!numericEqual(a.numericAt(atom), b.numericAt(atom))) {
                return false;
            }
        }
        return true;
    }

    private static boolean numericEqual(double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return false;
        }
        if (left == right) {
            return true;
        }
        if (Double.isInfinite(left) || Double.isInfinite(right)) {
            return false;
        }
        return Math.abs(left - right)
                <= TOLERANCE * Math.max(Math.abs(left), Math.abs(right));
    }

    /** The first-occurrence items of {@code y}, in order. */
    static List<JArray> uniqueItems(JArray y) {
        List<JArray> unique = new ArrayList<>();
        for (int item = 0; item < y.itemCount(); item++) {
            JArray candidate = y.item(item);
            boolean seen = false;
            for (JArray known : unique) {
                if (equal(known, candidate)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique.add(candidate);
            }
        }
        return unique;
    }

    /** {@code ~: y}: true at the first occurrence of each distinct item. */
    static JArray nubSieve(JArray y) {
        List<JArray> unique = new ArrayList<>();
        long[] flags = new long[y.itemCount()];
        for (int item = 0; item < flags.length; item++) {
            JArray candidate = y.item(item);
            boolean seen = false;
            for (JArray known : unique) {
                if (equal(known, candidate)) {
                    seen = true;
                    break;
                }
            }
            flags[item] = seen ? 0 : 1;
            if (!seen) {
                unique.add(candidate);
            }
        }
        return JArray.ints(flags);
    }

    /** {@code = y}: rows are nub items, columns are items of {@code y}. */
    static JArray selfClassify(JArray y) {
        List<JArray> unique = uniqueItems(y);
        int items = y.itemCount();
        long[] table = new long[unique.size() * items];
        int at = 0;
        for (JArray row : unique) {
            for (int item = 0; item < items; item++) {
                table[at++] = equal(row, y.item(item)) ? 1 : 0;
            }
        }
        return JArray.ints(new int[]{unique.size(), items}, table);
    }

    /** The index of the first matching item in {@code x}, or {@code #x}. */
    static int firstIndex(JArray x, JArray cell) {
        for (int item = 0; item < x.itemCount(); item++) {
            if (equal(x.item(item), cell)) {
                return item;
            }
        }
        return x.itemCount();
    }
}
