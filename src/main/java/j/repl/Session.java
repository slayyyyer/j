package j.repl;

import java.util.List;
import java.util.Optional;

import j.array.JFormat;
import j.eval.Environment;
import j.eval.Evaluator;
import j.eval.Value;
import j.lex.Lexer;
import j.lex.Token;
import j.parse.Parser;
import j.parse.Phrase;

/**
 * One interpreter session: caches the environment and evaluates individual lines.
 * Returns the text to display, or empty for blank/comment-only input.
 */
public final class Session {
    private final Environment environment = new Environment();
    private final Evaluator evaluator = new Evaluator(environment);

    public Optional<String> evaluate(String line) {
        List<Token> tokens = Lexer.lex(line);
        if (tokens.size() == 1) {
            return Optional.empty();
        }
        Phrase phrase = new Parser(tokens, environment).parse();
        Value value = evaluator.evaluate(phrase);
        return Optional.of(switch (value) {
            case Value.Noun noun -> JFormat.format(noun.array());
            case Value.VerbValue verb -> verb.verb().repr();
        });
    }
}
