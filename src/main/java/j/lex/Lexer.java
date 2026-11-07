package j.lex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.array.Complex;
import j.array.JArray;
import j.array.Rational;

/**
 * Tokenizes one J sentence.
 *
 * <p>J's word formation is simple: a word is a number, a name, a string, or a
 * primitive, and blanks (or the start/end of input) separate adjacent words.
 * Negative numeric literals are written with {@code _}, so there is no ambiguity
 * between a negative literal and the {@code -} verb. A {@code _} that is not
 * followed by a digit is the infinity literal.
 *
 * <p>The lexer records 1-based columns and reports malformed literals or unknown
 * characters as {@code spelling error}. It does not decide whether a sequence of
 * tokens forms a valid sentence; that is the parser's job.
 *
 * <p>Numeric suffixes select exact and complex types: {@code 123x} is an extended
 * integer, {@code 1r2} a rational, and {@code 3j4} a complex number. Numeric
 * literals that do not fit a Java long promote to extended integers.
 */
public final class Lexer {
    private static final String COMMENT = "NB.";

    /**
     * Primitive spellings, longest first. {@code /} and {@code \} are adverbs, not
     * part of a verb name, so {@code +/} intentionally becomes two tokens.
     */
    private static final String[] PRIMITIVES = {
            "{{", "}}",
            "=:", "=.", "{.", "}.", "|.", "|:", "i.", "~:", "<:", ">:", "@:",
            "+", "-", "*", "%", "^", "=", "<", ">", "~", "$.", "$", ",", "#",
            "{", ";", "@", "&", ".", ":", "\"", "/", "\\"
    };

    private final String source;
    private int position;

    private Lexer(String source) {
        this.source = source;
    }

    /** Tokenizes {@code source}, ending with an EOF token. */
    public static List<Token> lex(String source) {
        return new Lexer(source).tokenize();
    }

    private List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (!atEnd()) {
            char current = peek();
            if (Character.isWhitespace(current)) {
                position++;
            } else if (startsWith(COMMENT)) {
                skipComment();
            } else if (current == '\'') {
                tokens.add(lexString());
            } else if (current == '(') {
                tokens.add(symbol(TokenType.LPAREN, "("));
            } else if (current == ')') {
                tokens.add(symbol(TokenType.RPAREN, ")"));
            } else if (current == '_' || isDigit(current)) {
                tokens.add(lexNumber());
            } else {
                // Try primitives before names: `i.` is iota, while `i` alone is a
                // valid name. A name cannot contain a dot, so the longest match wins.
                Token primitive = lexPrimitive();
                if (primitive != null) {
                    tokens.add(primitive);
                } else if (isNameStart(current)) {
                    tokens.add(lexName());
                } else {
                    throw new JError("spelling error", position + 1);
                }
            }
        }
        tokens.add(new Token(TokenType.EOF, "", null, source.length() + 1));
        return tokens;
    }

    // --------------------------------------------------------------- literals

    private Token lexNumber() {
        int start = position;

        if (peek() == '_') {
            if (position + 1 < source.length()) {
                char next = source.charAt(position + 1);
                if (next == '_') {
                    return numberToken(start, position + 2, Double.NEGATIVE_INFINITY);
                }
                if (next == '.') {
                    return numberToken(start, position + 2, Double.NaN);
                }
                if (!isDigit(next)) {
                    return numberToken(start, position + 1, Double.POSITIVE_INFINITY);
                }
            } else {
                return numberToken(start, position + 1, Double.POSITIVE_INFINITY);
            }
            position++; // leading underscore of a negative literal
        }

        int integerStart = position;
        while (!atEnd() && isDigit(peek())) {
            position++;
        }
        if (position == integerStart) {
            throw new JError("spelling error", start + 1);
        }

        boolean isFloat = false;
        if (!atEnd() && peek() == '.') {
            isFloat = true;
            position++;
            while (!atEnd() && isDigit(peek())) {
                position++;
            }
        }

        if (!atEnd() && peek() == 'e') {
            isFloat = true;
            int exponent = position;
            position++;
            if (!atEnd() && peek() == '_') {
                position++;
            }
            int exponentDigits = position;
            while (!atEnd() && isDigit(peek())) {
                position++;
            }
            if (position == exponentDigits) {
                throw new JError("spelling error", exponent + 1);
            }
        }

        int end = position;

        if (!atEnd() && peek() == 'x') {
            if (isFloat) {
                throw new JError("spelling error", start + 1);
            }
            position++;
            ensureNumberBoundary(position);
            String text = source.substring(start, position);
            JArray literal = JArray.scalar(
                    parseBigInteger(text.substring(0, text.length() - 1), start));
            return literalToken(start, position, literal);
        }

        if (!atEnd() && peek() == 'r') {
            if (isFloat) {
                throw new JError("spelling error", start + 1);
            }
            String numeratorText = source.substring(start, end);
            position++;
            int denominatorStart = position;
            while (!atEnd() && isDigit(peek())) {
                position++;
            }
            if (position == denominatorStart) {
                throw new JError("spelling error", start + 1);
            }
            ensureNumberBoundary(position);
            String text = source.substring(start, position);
            BigInteger numerator = parseBigInteger(numeratorText, start);
            BigInteger denominator = parseBigInteger(
                    source.substring(denominatorStart, position), denominatorStart);
            if (denominator.signum() == 0) {
                throw new JError("spelling error", denominatorStart + 1);
            }
            return literalToken(start, position, JArray.scalar(new Rational(numerator, denominator)));
        }

        if (!atEnd() && peek() == 'j') {
            String realText = source.substring(start, end);
            position++;
            int imaginaryStart = position;
            if (!atEnd() && peek() == '_') {
                position++;
            }
            int imaginaryDigits = position;
            while (!atEnd() && isDigit(peek())) {
                position++;
            }
            if (position == imaginaryDigits) {
                throw new JError("spelling error", start + 1);
            }
            if (!atEnd() && peek() == '.') {
                position++;
                while (!atEnd() && isDigit(peek())) {
                    position++;
                }
            }
            if (!atEnd() && peek() == 'e') {
                int exponent = position;
                position++;
                if (!atEnd() && peek() == '_') {
                    position++;
                }
                int exponentDigits = position;
                while (!atEnd() && isDigit(peek())) {
                    position++;
                }
                if (position == exponentDigits) {
                    throw new JError("spelling error", exponent + 1);
                }
            }
            ensureNumberBoundary(position);
            String text = source.substring(start, position);
            double real = parseDouble(realText, start);
            double imaginary = parseDouble(
                    source.substring(imaginaryStart, position), imaginaryStart);
            return literalToken(start, position, JArray.scalar(new Complex(real, imaginary)));
        }

        ensureNumberBoundary(end);
        String text = source.substring(start, end);
        JArray literal = isFloat
                ? JArray.scalar(parseDouble(text, start))
                : integerLiteral(text, start);
        return new Token(TokenType.NUMBER, text, literal, start + 1);
    }

    private Token numberToken(int start, int end, double value) {
        ensureNumberBoundary(end);
        position = end;
        return new Token(
                TokenType.NUMBER,
                source.substring(start, end),
                JArray.scalar(value),
                start + 1);
    }

    private Token literalToken(int start, int end, JArray literal) {
        position = end;
        return new Token(
                TokenType.NUMBER,
                source.substring(start, end),
                literal,
                start + 1);
    }

    /**
     * J requires a delimiter between a numeric literal and a following word.
     * Adjacent letters, digits, underscores, or dots are unsupported numeric forms
     * (for example {@code 1_2}), not two separate literals.
     */
    private void ensureNumberBoundary(int end) {
        if (end >= source.length()) {
            return;
        }
        char next = source.charAt(end);
        if (isNamePart(next) || next == '.') {
            throw new JError("spelling error", end + 1);
        }
    }

    private JArray integerLiteral(String text, int start) {
        try {
            return JArray.scalar(Long.parseLong(text.replace('_', '-')));
        } catch (NumberFormatException overflow) {
            return JArray.scalar(parseBigInteger(text, start));
        }
    }

    private BigInteger parseBigInteger(String text, int start) {
        try {
            return new BigInteger(text.replace('_', '-'));
        } catch (NumberFormatException malformed) {
            throw new JError("spelling error", start + 1);
        }
    }

    private double parseDouble(String text, int start) {
        try {
            return Double.parseDouble(text.replace('_', '-'));
        } catch (NumberFormatException malformed) {
            throw new JError("spelling error", start + 1);
        }
    }

    private Token lexString() {
        int start = position;
        position++; // opening quote
        StringBuilder value = new StringBuilder();

        while (!atEnd()) {
            char current = peek();
            if (current != '\'') {
                value.append(current);
                position++;
            } else if (position + 1 < source.length() && source.charAt(position + 1) == '\'') {
                value.append('\'');
                position += 2; // doubled quote is one literal quote
            } else {
                position++; // closing quote
                return new Token(
                        TokenType.STRING,
                        source.substring(start, position),
                        JArray.chars(value.toString()),
                        start + 1);
            }
        }

        throw new JError("spelling error", start + 1);
    }

    // ------------------------------------------------------------------ words

    private Token lexName() {
        int start = position;
        position++;
        while (!atEnd() && isNamePart(peek())) {
            position++;
        }
        return new Token(TokenType.NAME, source.substring(start, position), null, start + 1);
    }

    private Token lexPrimitive() {
        for (String spelling : PRIMITIVES) {
            if (!startsWith(spelling)) {
                continue;
            }
            TokenType type = switch (spelling) {
                case "=:" -> TokenType.ASSIGN_GLOBAL;
                case "=." -> TokenType.ASSIGN_LOCAL;
                case "{{" -> TokenType.LBRACE2;
                case "}}" -> TokenType.RBRACE2;
                default -> TokenType.PRIMITIVE;
            };
            return symbol(type, spelling);
        }
        return null;
    }

    private Token symbol(TokenType type, String text) {
        Token token = new Token(type, text, null, position + 1);
        position += text.length();
        return token;
    }

    private void skipComment() {
        while (!atEnd() && peek() != '\n') {
            position++;
        }
    }

    // ----------------------------------------------------------------- helpers

    private boolean atEnd() {
        return position >= source.length();
    }

    private char peek() {
        return source.charAt(position);
    }

    private boolean startsWith(String text) {
        return source.startsWith(text, position);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isNamePart(char c) {
        return isNameStart(c) || isDigit(c) || c == '_';
    }
}
