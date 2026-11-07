package j.array;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * J's display representation of a noun.
 *
 * <p>Numeric display uses J's five significant digits for floating point. Boxed
 * nouns are drawn as bordered cells; nested boxes are rendered recursively.
 */
public final class JFormat {
    private static final int PRINT_PRECISION = 5;
    private static final MathContext ROUNDING =
            new MathContext(PRINT_PRECISION, RoundingMode.HALF_UP);

    private JFormat() {
    }

    public static String format(JArray array) {
        if (array.isSparse()) {
            return formatSparse(array);
        }
        if (array.type() == JType.BOX) {
            return formatBoxes(array);
        }
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

    /**
     * Sparse display: aligned flat-index coordinates, a vertical bar, and the
     * stored values. This follows J's sparse display layout.
     */
    private static String formatSparse(JArray array) {
        int[] indices = array.sparseIndices();
        Object[] values = array.sparseValues();
        int rank = array.rank();
        if (indices.length == 0) {
            return rank == 0 ? formatAtom(array.type(), array.sparseFill()) : "";
        }
        if (rank == 0) {
            return formatAtom(array.type(), values.length == 0 ? array.sparseFill() : values[0]);
        }

        int[] shape = array.shape();
        int[] strides = Shapes.strides(shape);
        int[][] coordinates = new int[indices.length][rank];
        int[] widths = new int[rank];
        for (int entry = 0; entry < indices.length; entry++) {
            int remainder = indices[entry];
            for (int axis = 0; axis < rank; axis++) {
                int coordinate = remainder / strides[axis];
                remainder %= strides[axis];
                coordinates[entry][axis] = coordinate;
                widths[axis] = Math.max(widths[axis], Integer.toString(coordinate).length());
            }
        }

        String[] renderedValues = new String[values.length];
        int valueWidth = 0;
        for (int entry = 0; entry < values.length; entry++) {
            renderedValues[entry] = formatAtom(array.type(), values[entry]);
            valueWidth = Math.max(valueWidth, renderedValues[entry].length());
        }

        List<String> lines = new ArrayList<>(indices.length);
        for (int entry = 0; entry < indices.length; entry++) {
            StringBuilder line = new StringBuilder();
            for (int axis = 0; axis < rank; axis++) {
                if (axis > 0) {
                    line.append(' ');
                }
                line.append(padLeft(Integer.toString(coordinates[entry][axis]), widths[axis]));
            }
            line.append(" | ").append(padLeft(renderedValues[entry], valueWidth));
            lines.add(line.toString());
        }
        return String.join("\n", lines);
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
        return formatAtom(array.type(), array.atomAt(index));
    }

    private static String formatAtom(JType type, Object atom) {
        return switch (type) {
            case INT -> formatLong((Long) atom);
            case BIGINT -> formatBig((BigInteger) atom);
            case RATIONAL -> formatRational((Rational) atom);
            case FLOAT -> formatDouble((Double) atom);
            case COMPLEX -> formatComplex((Complex) atom);
            case CHAR -> String.valueOf((Character) atom);
            case BOX -> throw new IllegalStateException(
                    "a box cell is rendered by the box formatter");
        };
    }

    private static String padLeft(String text, int width) {
        return " ".repeat(Math.max(0, width - text.length())) + text;
    }

    private static String formatLong(long value) {
        return value < 0
                ? "_" + Long.toString(value).substring(1)
                : Long.toString(value);
    }

    private static String formatBig(BigInteger value) {
        return value.signum() < 0
                ? "_" + value.negate()
                : value.toString();
    }

    private static String formatRational(Rational value) {
        return formatBig(value.numerator()) + "r" + value.denominator();
    }

    private static String formatComplex(Complex value) {
        return formatDouble(value.real()) + "j" + formatDouble(value.imaginary());
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
        int exponent = rounded.precision() - rounded.scale() - 1;
        boolean negative = rounded.signum() < 0;
        String digits = rounded.unscaledValue().abs().toString();
        String magnitude;

        if (exponent >= 6 || exponent < -4) {
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

    // ---------------------------------------------------------------------- boxes

    private static String formatBoxes(JArray array) {
        if (array.rank() == 0) {
            return String.join("\n", borderedCells(List.of(array.boxAt(0)), 1, 1));
        }
        if (array.rank() == 1) {
            return String.join("\n", borderedCells(boxAtoms(array), 1, array.size()));
        }
        if (array.rank() > 2) {
            int columns = array.shape()[array.rank() - 1];
            int rows = array.shape()[array.rank() - 2];
            int planeSize = rows * columns;
            int planes = array.size() / planeSize;
            List<String> result = new ArrayList<>();
            for (int plane = 0; plane < planes; plane++) {
                if (plane > 0) {
                    result.add("");
                }
                JArray planeArray = array.slice(plane * planeSize, new int[]{rows, columns});
                result.add(String.join("\n", borderedCells(boxAtoms(planeArray), rows, columns)));
            }
            return String.join("\n", result);
        }
        return String.join("\n", borderedCells(boxAtoms(array), array.shape()[0], array.shape()[1]));
    }

    private static List<JArray> boxAtoms(JArray array) {
        List<JArray> atoms = new ArrayList<>(array.size());
        for (int atom = 0; atom < array.size(); atom++) {
            atoms.add(array.boxAt(atom));
        }
        return atoms;
    }

    private static List<String> borderedCells(List<JArray> cells, int rows, int columns) {
        List<String> lines = new ArrayList<>();
        int[][] widths = new int[rows][columns];
        List<List<String>> cellLines = new ArrayList<>(cells.size());
        for (int cell = 0; cell < cells.size(); cell++) {
            String text = format(cells.get(cell));
            List<String> split = List.of(text.split("\\n", -1));
            cellLines.add(split);
            int width = 0;
            for (String line : split) {
                width = Math.max(width, line.length());
            }
            widths[cell / columns][cell % columns] = width;
        }

        String top = border(widths[0]);
        lines.add(top);
        for (int row = 0; row < rows; row++) {
            int height = 1;
            for (int column = 0; column < columns; column++) {
                height = Math.max(height, cellLines.get(row * columns + column).size());
            }
            for (int line = 0; line < height; line++) {
                StringBuilder rendered = new StringBuilder("|");
                for (int column = 0; column < columns; column++) {
                    List<String> content = cellLines.get(row * columns + column);
                    String text = line < content.size() ? content.get(line) : "";
                    int width = widths[row][column];
                    rendered.append(text).append(" ".repeat(width - text.length())).append('|');
                }
                lines.add(rendered.toString());
            }
            lines.add(border(widths[row]));
        }
        return lines;
    }

    private static String border(int[] widths) {
        StringBuilder line = new StringBuilder("+");
        for (int width : widths) {
            line.append("-".repeat(width)).append('+');
        }
        return line.toString();
    }
}
