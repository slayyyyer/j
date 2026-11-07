package j.array;

/** A complex J atom. */
public record Complex(double real, double imaginary) {
    public static final Complex ZERO = new Complex(0.0, 0.0);
    public static final Complex ONE = new Complex(1.0, 0.0);

    public Complex add(Complex other) {
        return new Complex(real + other.real, imaginary + other.imaginary);
    }

    public Complex subtract(Complex other) {
        return new Complex(real - other.real, imaginary - other.imaginary);
    }

    public Complex multiply(Complex other) {
        return new Complex(
                real * other.real - imaginary * other.imaginary,
                real * other.imaginary + imaginary * other.real);
    }

    public Complex divide(Complex other) {
        double denominator = other.real * other.real + other.imaginary * other.imaginary;
        return new Complex(
                (real * other.real + imaginary * other.imaginary) / denominator,
                (imaginary * other.real - real * other.imaginary) / denominator);
    }

    public Complex negate() {
        return new Complex(-real, -imaginary);
    }

    public Complex conjugate() {
        return new Complex(real, -imaginary);
    }

    public double magnitude() {
        return Math.hypot(real, imaginary);
    }

    public boolean isReal() {
        return imaginary == 0.0;
    }
}
