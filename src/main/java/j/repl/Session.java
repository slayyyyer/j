package j.repl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import j.JError;
import j.array.JFormat;
import j.array.JType;
import j.eval.Environment;
import j.eval.Evaluator;
import j.eval.Value;
import j.lex.Lexer;
import j.lex.Token;
import j.lex.TokenType;
import j.parse.NameClassifier;
import j.parse.Parser;
import j.parse.Phrase;
import j.verbs.ExplicitVerb;

/**
 * One interpreter session: caches the environment and evaluates individual lines.
 * Returns the text to display, or empty for blank/comment-only input.
 */
public final class Session {
    private final Environment environment = new Environment();
    private final Evaluator evaluator = new Evaluator(environment);

    /** Whether a line starts a multi-line {@code name =: 3 : 0} definition. */
    public boolean isExplicitDefinitionStart(String line) {
        try {
            parseDefinitionHeader(Lexer.lex(line));
            return true;
        } catch (JError malformed) {
            return false;
        }
    }

    /**
     * Defines a verb from a {@code 3 : 0} header and body lines. Each non-empty line
     * is one sentence; the last sentence's noun is the result.
     */
    public void defineExplicit(String header, List<String> bodyLines) {
        DefinitionHeader definition = parseDefinitionHeader(Lexer.lex(header));
        NameClassifier bodyNames =
                name -> !name.equals("x") && !name.equals("y") && environment.isVerb(name);

        List<Phrase> body = new ArrayList<>();
        for (String line : bodyLines) {
            List<Token> tokens = Lexer.lex(line);
            if (tokens.size() == 1) {
                continue;
            }
            Phrase phrase = new Parser(tokens, bodyNames).parse();
            if (phrase instanceof Phrase.Verb) {
                throw new JError("syntax error");
            }
            body.add(phrase);
        }
        if (body.isEmpty()) {
            throw new JError("syntax error");
        }

        environment.assign(
                definition.name(),
                new Value.VerbValue(new ExplicitVerb(body, environment)));
    }

    public Optional<String> evaluate(String line) {
        List<Token> tokens = Lexer.lex(line);
        if (tokens.size() == 1) {
            return Optional.empty();
        }
        Phrase phrase = new Parser(tokens, environment).parse();
        if (phrase instanceof Phrase.Assign) {
            evaluator.evaluate(phrase);
            return Optional.empty();
        }
        Value value = evaluator.evaluate(phrase);
        return Optional.of(switch (value) {
            case Value.Noun noun -> JFormat.format(noun.array());
            case Value.VerbValue verb -> verb.verb().repr();
        });
    }

    private static DefinitionHeader parseDefinitionHeader(List<Token> tokens) {
        if (tokens.size() != 6) {
            throw new JError("syntax error");
        }
        Token name = tokens.get(0);
        Token assignment = tokens.get(1);
        Token three = tokens.get(2);
        Token colon = tokens.get(3);
        Token zero = tokens.get(4);
        Token end = tokens.get(5);

        boolean assignmentToken = assignment.type() == TokenType.ASSIGN_GLOBAL
                || assignment.type() == TokenType.ASSIGN_LOCAL;
        if (name.type() != TokenType.NAME
                || !assignmentToken
                || !isInteger(three, 3)
                || !colon.isPrimitive(":")
                || !isInteger(zero, 0)
                || end.type() != TokenType.EOF) {
            throw new JError("syntax error");
        }
        return new DefinitionHeader(
                name.text(),
                assignment.type() == TokenType.ASSIGN_GLOBAL);
    }

    private static boolean isInteger(Token token, long expected) {
        if (token.type() != TokenType.NUMBER) {
            return false;
        }
        var literal = token.literal();
        return literal != null
                && literal.type() == JType.INT
                && literal.isScalar()
                && literal.longAt(0) == expected;
    }

    private record DefinitionHeader(String name, boolean global) {
    }
}
