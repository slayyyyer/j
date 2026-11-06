package j.array;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * J's display representation of a noun.
 *
 * <p>J's default display precision is five significant digits. Floats therefore print
 * as e.g. {@code 0.33333}, {@code 2.5}, {@code 1e6}, {@code 1e_5}, with negative
 * numbers spelled with {@code _}. Integers are exact. Matrices right-align each
 * column; rank 3 and higher arrays show their rank-2 planes separated by blank lines.
 *
 * <p>The fine details of J's numeric formatting (rounding mode at ties and the exact
 * exponent cutovers) are approximate until checked against a real interpreter.
 */
public final class JFormat {
    private static final int PRINT_PRECISION = 5;
    private static final MathContext ROUNDING =
            new MathContext(PRINT_PRECISION, RoundingMode.HALF_UP);

    private JFormat() {
    }

    public static String format(JArray array) {
        if (array.rank() == 0) {
            return atom(array, 0);
        }
        if (array.isEmpty()) {
            return "";
        }
        if (array.type() == JType.CHAR) {
            return formatCharacters(array);
        }
        return formatNumbers(array);
    }

    // ---------------------------------------------------------------- characters

    private static String formatCharacters(JArray array) {
        int rank = array.rank();
        int[] shape = array.shape();

        if (rank == 1) {
            return characterRow(array, 0, shape[0]);
        }

        int columns = shape[rank - 1];
        int rows = shape[rank - 2];
        int cellSize = rows * columns;
        int planes = array.size() / cellSize;
        List<String> lines = new ArrayList<>();

        for (int plane = 0; plane < planes; plane++) {
            if (plane > 0) {
                lines.add("");
            }
            for (int row = 0; row < rows; row++) {
                int start = (plane * rows + row) * columns;
                lines.add(characterRow(array, start, columns));
            }
        }
        return String.join("\n", lines);
    }

    private static String characterRow(JArray array, int start, int length) {
        StringBuilder row = new StringBuilder(length);
        for (int column = 0; column < length; column++) {
            row.append(array.charAt(start + column));
        }
        return row.toString();
    }

    // ------------------------------------------------------------------- numbers

    private static String formatNumbers(JArray array) {
        int rank = array.rank();
        int[] shape = array.shape();

        if (rank == 1) {
            List<String> atoms = new ArrayList<>(array.size());
            for (int atom = 0; atom < array.size(); atom++) {
                atoms.add(atom(array, atom));
            }
            return String.join(" ", atoms);
        }

        int columns = shape[rank - 1];
        int rows = shape[rank - 2];
        int cellSize = rows * columns;
        int planes = array.size() / cellSize;

        String[] tokens = new String[array.size()];
        int[] widths = new int[columns];
        for (int atom = 0; atom < array.size(); atom++) {
            String token = atom(array, atom);
            tokens[atom] = token;
            int column = atom % columns;
            widths[column] = Math.max(widths[column], token.length());
        }

        List<String> lines = new ArrayList<>();
        for (int plane = 0; plane < planes; plane++) {
            if (plane > 0) {
                lines.add("");
            }
            for (int row = 0; row < rows; row++) {
                StringBuilder line = new StringBuilder();
                for (int column = 0; column < columns; column++) {
                    if (column > 0) {
                        line.append(' ');
                    }
                    String token = tokens[(plane * rows + row) * columns + column];
                    line.append(" ".repeat(widths[column] - token.length())).append(token);
                }
                lines.add(line.toString());
            }
        }
        return String.join("\n", lines);
    }

    private static String atom(JArray array, int index) {
        return switch (array.type()) {
            case INT -> formatLong(array.longAt(index));
            case FLOAT -> formatDouble(array.doubleAt(index));
            case CHAR -> String.valueOf(array.charAt(index));
        };
    }

    private static String formatLong(long value) {
        return value < 0
                ? "_" + Long.toString(value).substring(1)
                : Long.toString(value);
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "_.";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "_";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "__";
        }
        if (value == 0.0) {
            return "0";
        }

        BigDecimal rounded = new BigDecimal(value).round(ROUNDING);
        // Floor of log10(|value|), e.g. 123.4 -> 2.
        int exponent = rounded.precision() - rounded.scale() - 1;
        boolean negative = rounded.signum() < 0;
        String digits = rounded.unscaledValue().abs().toString();
        String magnitude;

        if (exponent >= 6 || exponent < -4) {
            // J's power notation: 1e6, 1.2346e6, 1e_5 (no plus sign, _ for a negative exponent).
            StringBuilder mantissa = new StringBuilder();
            mantissa.append(digits.charAt(0));
            String fraction = digits.substring(1).replaceFirst("0+$", "");
            if (!fraction.isEmpty()) {
                mantissa.append('.').append(fraction);
            }
            mantissa.append('e').append(exponent < 0 ? "_" + (-exponent) : exponent);
            magnitude = mantissa.toString();
        } else {
            magnitude = rounded.abs().stripTrailingZeros().toPlainString();
        }

        return negative ? "_" + magnitude : magnitude;
    }
}
