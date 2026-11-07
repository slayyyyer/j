package j.array;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** An exact J rational atom, normalized with a positive denominator. */
public record Rational(BigInteger numerator, BigInteger denominator) {
    public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
    public static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);

    public Rational {
        if (denominator.signum() == 0) {
            throw new ArithmeticException("zero denominator");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (!gcd.equals(BigInteger.ONE)) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
    }

    public Rational add(Rational other) {
        return new Rational(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Rational subtract(Rational other) {
        return new Rational(
                numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Rational multiply(Rational other) {
        return new Rational(
                numerator.multiply(other.numerator),
                denominator.multiply(other.denominator));
    }

    public Rational divide(Rational other) {
        return new Rational(
                numerator.multiply(other.denominator),
                denominator.multiply(other.numerator));
    }

    public Rational negate() {
        return new Rational(numerator.negate(), denominator);
    }

    public boolean isInteger() {
        return denominator.equals(BigInteger.ONE);
    }

    public double toDouble() {
        return new BigDecimal(numerator)
                .divide(new BigDecimal(denominator), MathContext.DECIMAL128)
                .doubleValue();
    }
}
