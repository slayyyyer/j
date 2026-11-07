package j.verbs;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import j.JError;
import j.array.Complex;
import j.array.JArray;
import j.array.JType;
import j.array.Rational;

/** Shared numeric promotion, arithmetic, and comparison for atom verbs. */
final class Numeric {
    private static final double TOLERANCE = Math.scalb(1.0, -44);

    private Numeric() {
    }

    static void requireNumeric(JArray... arrays) {
        for (JArray array : arrays) {
            if (!array.type().isNumeric()) {
                throw new JError("domain error");
            }
        }
    }

    private enum Op {
        ADD,
        SUBTRACT,
        MULTIPLY
    }

    static JArray add(JArray x, JArray y) {
        return arithmetic(Op.ADD, x, y);
    }

    static JArray subtract(JArray x, JArray y) {
        return arithmetic(Op.SUBTRACT, x, y);
    }

    static JArray multiply(JArray x, JArray y) {
        return arithmetic(Op.MULTIPLY, x, y);
    }

    private static JArray arithmetic(Op op, JArray x, JArray y) {
        requireNumeric(x, y);
        if (x.type() == JType.COMPLEX || y.type() == JType.COMPLEX) {
            Complex left = x.complexAt(0);
            Complex right = y.complexAt(0);
            Complex result = switch (op) {
                case ADD -> left.add(right);
                case SUBTRACT -> left.subtract(right);
                case MULTIPLY -> left.multiply(right);
            };
            return JArray.scalar(result);
        }
        if (x.type() == JType.FLOAT || y.type() == JType.FLOAT) {
            double left = x.numericAt(0);
            double right = y.numericAt(0);
            double result = switch (op) {
                case ADD -> left + right;
                case SUBTRACT -> left - right;
                case MULTIPLY -> left * right;
            };
            return JArray.scalar(result);
        }
        if (x.type() == JType.RATIONAL || y.type() == JType.RATIONAL) {
            Rational left = x.rationalAt(0);
            Rational right = y.rationalAt(0);
            Rational result = switch (op) {
                case ADD -> left.add(right);
                case SUBTRACT -> left.subtract(right);
                case MULTIPLY -> left.multiply(right);
            };
            return rationalResult(result);
        }

        BigInteger left = x.bigAt(0);
        BigInteger right = y.bigAt(0);
        if (x.type() == JType.INT && y.type() == JType.INT) {
            long a = x.longAt(0);
            long b = y.longAt(0);
            try {
                long result = switch (op) {
                    case ADD -> Math.addExact(a, b);
                    case SUBTRACT -> Math.subtractExact(a, b);
                    case MULTIPLY -> Math.multiplyExact(a, b);
                };
                return JArray.scalar(result);
            } catch (ArithmeticException overflow) {
                // Promote to an extended integer instead of limiting.
            }
        }
        BigInteger result = switch (op) {
            case ADD -> left.add(right);
            case SUBTRACT -> left.subtract(right);
            case MULTIPLY -> left.multiply(right);
        };
        return exactResult(result);
    }

    static JArray negate(JArray y) {
        requireNumeric(y);
        return switch (y.type()) {
            case COMPLEX -> JArray.scalar(y.complexAt(0).negate());
            case FLOAT -> JArray.scalar(-y.doubleAt(0));
            case RATIONAL -> JArray.scalar(y.rationalAt(0).negate());
            case BIGINT -> exactResult(y.bigAt(0).negate());
            case INT -> {
                try {
                    yield JArray.scalar(Math.negateExact(y.longAt(0)));
                } catch (ArithmeticException overflow) {
                    yield exactResult(BigInteger.valueOf(y.longAt(0)).negate());
                }
            }
            default -> throw new JError("domain error");
        };
    }

    static JArray conjugate(JArray y) {
        requireNumeric(y);
        return y.type() == JType.COMPLEX
                ? JArray.scalar(y.complexAt(0).conjugate())
                : y;
    }

    static JArray signum(JArray y) {
        requireNumeric(y);
        return switch (y.type()) {
            case COMPLEX -> {
                Complex value = y.complexAt(0);
                double magnitude = value.magnitude();
                yield magnitude == 0.0
                        ? JArray.scalar(0L)
                        : JArray.scalar(new Complex(value.real() / magnitude, value.imaginary() / magnitude));
            }
            case FLOAT -> {
                double value = y.doubleAt(0);
                yield Double.isNaN(value)
                        ? JArray.scalar(Double.NaN)
                        : JArray.scalar((long) Math.signum(value));
            }
            case RATIONAL -> JArray.scalar(y.rationalAt(0).numerator().signum());
            case BIGINT -> JArray.scalar(y.bigAt(0).signum());
            case INT -> JArray.scalar(Long.signum(y.longAt(0)));
            default -> throw new JError("domain error");
        };
    }

    static JArray reciprocal(JArray y) {
        requireNumeric(y);
        if (y.type() == JType.COMPLEX) {
            return JArray.scalar(Complex.ONE.divide(y.complexAt(0)));
        }
        return JArray.scalar(1.0 / y.numericAt(0));
    }

    static JArray divide(JArray x, JArray y) {
        requireNumeric(x, y);
        if (x.type() == JType.COMPLEX || y.type() == JType.COMPLEX) {
            return JArray.scalar(x.complexAt(0).divide(y.complexAt(0)));
        }
        return JArray.scalar(x.numericAt(0) / y.numericAt(0));
    }

    static JArray exp(JArray y) {
        requireNumeric(y);
        if (y.type() == JType.COMPLEX) {
            Complex value = y.complexAt(0);
            double magnitude = Math.exp(value.real());
            return JArray.scalar(new Complex(
                    magnitude * Math.cos(value.imaginary()),
                    magnitude * Math.sin(value.imaginary())));
        }
        return JArray.scalar(Math.exp(y.numericAt(0)));
    }

    static JArray power(JArray x, JArray y) {
        requireNumeric(x, y);
        if (x.type() == JType.COMPLEX || y.type() == JType.COMPLEX) {
            return JArray.scalar(complexPower(x.complexAt(0), y.complexAt(0)));
        }
        if (y.type().isExact() && isInteger(y)) {
            long exponent = exactLong(y);
            if (exponent < 0) {
                return JArray.scalar(Math.pow(x.numericAt(0), (double) exponent));
            }
            if (x.type().isExact()) {
                return exactPower(x, exponent);
            }
            return JArray.scalar(Math.pow(x.numericAt(0), (double) exponent));
        }
        return JArray.scalar(Math.pow(x.numericAt(0), y.numericAt(0)));
    }

    private static JArray exactPower(JArray base, long exponent) {
        Rational result = Rational.ONE;
        Rational factor = base.rationalAt(0);
        long power = exponent;
        while (power > 0) {
            if ((power & 1L) == 1L) {
                result = result.multiply(factor);
            }
            power >>= 1;
            if (power > 0) {
                factor = factor.multiply(factor);
            }
        }
        return rationalResult(result);
    }

    private static Complex complexPower(Complex base, Complex exponent) {
        double magnitude = base.magnitude();
        if (magnitude == 0.0) {
            return Complex.ZERO;
        }
        double angle = Math.atan2(base.imaginary(), base.real());
        double logMagnitude = Math.log(magnitude);
        double resultMagnitude = Math.exp(
                exponent.real() * logMagnitude - exponent.imaginary() * angle);
        double resultAngle = exponent.real() * angle + exponent.imaginary() * logMagnitude;
        return new Complex(
                resultMagnitude * Math.cos(resultAngle),
                resultMagnitude * Math.sin(resultAngle));
    }

    static JArray increment(JArray y) {
        requireNumeric(y);
        return add(y, one(y.type()));
    }

    static JArray decrement(JArray y) {
        requireNumeric(y);
        return subtract(y, one(y.type()));
    }

    static JArray zero(JType type) {
        return switch (type) {
            case INT -> JArray.scalar(0L);
            case BIGINT -> JArray.scalar(BigInteger.ZERO);
            case RATIONAL -> JArray.scalar(Rational.ZERO);
            case FLOAT -> JArray.scalar(0.0);
            case COMPLEX -> JArray.scalar(Complex.ZERO);
            case CHAR, BOX -> throw new JError("domain error");
        };
    }

    static JArray one(JType type) {
        return switch (type) {
            case INT -> JArray.scalar(1L);
            case BIGINT -> JArray.scalar(BigInteger.ONE);
            case RATIONAL -> JArray.scalar(Rational.ONE);
            case FLOAT -> JArray.scalar(1.0);
            case COMPLEX -> JArray.scalar(Complex.ONE);
            case CHAR, BOX -> throw new JError("domain error");
        };
    }

    static boolean isNaN(JArray y) {
        if (y.type() == JType.FLOAT) {
            return Double.isNaN(y.doubleAt(0));
        }
        if (y.type() == JType.COMPLEX) {
            Complex value = y.complexAt(0);
            return Double.isNaN(value.real()) || Double.isNaN(value.imaginary());
        }
        return false;
    }

    static int compare(JArray x, JArray y) {
        requireNumeric(x, y);
        if (x.type() == JType.COMPLEX || y.type() == JType.COMPLEX) {
            throw new JError("domain error");
        }
        if (x.type().isExact() && y.type().isExact()) {
            Rational left = x.rationalAt(0);
            Rational right = y.rationalAt(0);
            return left.numerator().multiply(right.denominator())
                    .compareTo(right.numerator().multiply(left.denominator()));
        }
        return Double.compare(x.numericAt(0), y.numericAt(0));
    }

    static boolean equal(JArray x, JArray y) {
        requireNumeric(x, y);
        if (x.type() == JType.COMPLEX || y.type() == JType.COMPLEX) {
            Complex left = x.complexAt(0);
            Complex right = y.complexAt(0);
            return tolerant(left.real(), right.real())
                    && tolerant(left.imaginary(), right.imaginary());
        }
        if (x.type().isExact() && y.type().isExact()) {
            return compare(x, y) == 0;
        }
        double left = x.numericAt(0);
        double right = y.numericAt(0);
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return false;
        }
        return tolerant(left, right);
    }

    static boolean equalAtoms(Object left, Object right) {
        if (left instanceof Character || right instanceof Character) {
            return left.equals(right);
        }
        if (left instanceof JArray || right instanceof JArray) {
            if (!(left instanceof JArray a) || !(right instanceof JArray b)) {
                return false;
            }
            return Items.equal(a, b);
        }
        if (!isNumericAtom(left) || !isNumericAtom(right)) {
            return false;
        }
        if (left instanceof Complex || right instanceof Complex) {
            Complex a = complexAtom(left);
            Complex b = complexAtom(right);
            return tolerant(a.real(), b.real()) && tolerant(a.imaginary(), b.imaginary());
        }
        if (left instanceof Double || right instanceof Double) {
            return tolerant(doubleAtom(left), doubleAtom(right));
        }
        Rational a = rationalAtom(left);
        Rational b = rationalAtom(right);
        return a.numerator().multiply(b.denominator())
                .compareTo(b.numerator().multiply(a.denominator())) == 0;
    }

    private static boolean tolerant(double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return false;
        }
        if (left == right) {
            return true;
        }
        if (Double.isInfinite(left) || Double.isInfinite(right)) {
            return false;
        }
        return Math.abs(left - right) <= TOLERANCE * Math.max(Math.abs(left), Math.abs(right));
    }

    private static boolean isNumericAtom(Object atom) {
        return atom instanceof Long
                || atom instanceof BigInteger
                || atom instanceof Rational
                || atom instanceof Double
                || atom instanceof Complex;
    }

    private static Complex complexAtom(Object atom) {
        if (atom instanceof Complex value) {
            return value;
        }
        return new Complex(doubleAtom(atom), 0.0);
    }

    private static double doubleAtom(Object atom) {
        if (atom instanceof Long value) {
            return value.doubleValue();
        }
        if (atom instanceof BigInteger value) {
            return value.doubleValue();
        }
        if (atom instanceof Rational value) {
            return value.toDouble();
        }
        if (atom instanceof Double value) {
            return value;
        }
        throw new IllegalArgumentException("not a real numeric atom");
    }

    private static Rational rationalAtom(Object atom) {
        if (atom instanceof Long value) {
            return new Rational(BigInteger.valueOf(value), BigInteger.ONE);
        }
        if (atom instanceof BigInteger value) {
            return new Rational(value, BigInteger.ONE);
        }
        if (atom instanceof Rational value) {
            return value;
        }
        throw new IllegalArgumentException("not an exact numeric atom");
    }

    static boolean isInteger(JArray y) {
        return switch (y.type()) {
            case INT, BIGINT -> true;
            case RATIONAL -> y.rationalAt(0).isInteger();
            default -> false;
        };
    }

    static long exactLong(JArray y) {
        try {
            return switch (y.type()) {
                case INT -> y.longAt(0);
                case BIGINT -> y.bigAt(0).longValueExact();
                case RATIONAL -> {
                    Rational value = y.rationalAt(0);
                    if (!value.isInteger()) {
                        throw new JError("domain error");
                    }
                    yield value.numerator().longValueExact();
                }
                default -> throw new JError("domain error");
            };
        } catch (ArithmeticException overflow) {
            throw new JError("limit error");
        }
    }

    static int exactInt(JArray y) {
        long value = exactLong(y);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new JError("limit error");
        }
        return (int) value;
    }

    static long exactLongAt(JArray array, int index) {
        try {
            return switch (array.type()) {
                case INT -> array.longAt(index);
                case BIGINT -> array.bigAt(index).longValueExact();
                case RATIONAL -> {
                    Rational value = array.rationalAt(index);
                    if (!value.isInteger()) {
                        throw new JError("domain error");
                    }
                    yield value.numerator().longValueExact();
                }
                default -> throw new JError("domain error");
            };
        } catch (ArithmeticException overflow) {
            throw new JError("limit error");
        }
    }

    static int exactIntAt(JArray array, int index) {
        long value = exactLongAt(array, index);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new JError("limit error");
        }
        return (int) value;
    }

    private static JArray exactResult(BigInteger value) {
        try {
            return JArray.scalar(value.longValueExact());
        } catch (ArithmeticException tooLarge) {
            return JArray.scalar(value);
        }
    }

    private static JArray rationalResult(Rational value) {
        return value.isInteger()
                ? exactResult(value.numerator())
                : JArray.scalar(value);
    }
}
