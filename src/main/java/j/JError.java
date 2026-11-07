package j;

/**
 * An error reported to the J user.
 *
 * <p>The message is J's error phrase, such as {@code domain error},
 * {@code spelling error}, or {@code length error}. Lexical and syntax errors also
 * carry a 1-based column; semantic errors use column 0.
 */
public final class JError extends RuntimeException {
    private final int column;

    public JError(String message) {
        this(message, 0);
    }

    public JError(String message, int column) {
        super(message);
        this.column = column;
    }

    /** The 1-based column of a lexical or syntax error, or 0 when not applicable. */
    public int column() {
        return column;
    }
}
