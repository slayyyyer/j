package j.lex;

import j.array.JArray;

/**
 * One lexical token.
 *
 * @param type     its lexical class
 * @param text     the exact source spelling (for a string, including the quotes)
 * @param literal  the parsed value of a NUMBER or STRING; null for other tokens
 * @param column   1-based starting column in the source line
 */
public record Token(TokenType type, String text, JArray literal, int column) {
    public boolean is(TokenType expected) {
        return type == expected;
    }

    public boolean isPrimitive(String spelling) {
        return type == TokenType.PRIMITIVE && text.equals(spelling);
    }
}
