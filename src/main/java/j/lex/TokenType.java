package j.lex;

/** The lexical classes of J words and punctuation. */
public enum TokenType {
    /** An integer or floating-point literal; its value is a rank-0 {@code JArray}. */
    NUMBER,
    /** A quoted character literal; its value is a rank-1 {@code JArray}. */
    STRING,
    NAME,
    PRIMITIVE,
    LPAREN,
    RPAREN,
    /** {@code =:}, global assignment. */
    ASSIGN_GLOBAL,
    /** {@code =.}, local assignment. */
    ASSIGN_LOCAL,
    /** {@code {{}, the start of a direct definition. */
    LBRACE2,
    /** The closing delimiter of a direct definition. */
    RBRACE2,
    EOF
}
