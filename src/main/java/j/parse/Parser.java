package j.parse;

import java.util.ArrayList;
import java.util.List;

import j.JError;
import j.lex.Lexer;
import j.lex.Token;
import j.lex.TokenType;
import j.verbs.Primitives;
import j.verbs.Verb;

/**
 * Parses one J sentence.
 *
 * <p>J has no operator precedence. The parser gathers noun and verb phrases,
 * combines adjacent nouns into strands, then reduces applications from the right:
 * {@code verb noun} is a monad and {@code noun verb noun} is a dyad. This is what
 * makes {@code 2 * 3 + 4} mean {@code 2 * (3 + 4)}.
 *
 * <p>A phrase containing only verbs is a verb value. Longer trains are not built
 * yet; they belong to the trains milestone.
 */
public final class Parser {
    private final List<Token> tokens;
    private final NameClassifier names;
    private int index;

    public Parser(List<Token> tokens, NameClassifier names) {
        this.tokens = tokens;
        this.names = names;
    }

    public static Phrase parse(String source, NameClassifier names) {
        return new Parser(Lexer.lex(source), names).parse();
    }

    public Phrase parse() {
        Token first = peek();
        if (first.type() == TokenType.EOF) {
            throw syntax(first);
        }
        Phrase phrase = parsePhrase();
        Token trailing = peek();
        if (trailing.type() != TokenType.EOF) {
            throw syntax(trailing);
        }
        return phrase;
    }

    private Phrase parsePhrase() {
        List<Term> terms = new ArrayList<>();

        while (peek().type() != TokenType.EOF && peek().type() != TokenType.RPAREN) {
            Token token = next();
            switch (token.type()) {
                case LPAREN -> {
                    Phrase inner = parsePhrase();
                    Token close = peek();
                    if (close.type() != TokenType.RPAREN) {
                        throw syntax(close);
                    }
                    next();
                    terms.add(asTerm(inner, token.column()));
                }
                case NUMBER, STRING ->
                        terms.add(new Term.NounTerm(new Expr.Lit(token.literal()), token.column()));
                case NAME -> {
                    if (names.isVerb(token.text())) {
                        terms.add(new Term.VerbTerm(new VerbExpr.Ref(token.text()), token.column()));
                    } else {
                        terms.add(new Term.NounTerm(new Expr.Ref(token.text()), token.column()));
                    }
                }
                case PRIMITIVE -> {
                    Verb verb = Primitives.lookup(token.text());
                    if (verb == null) {
                        throw syntax(token);
                    }
                    terms.add(new Term.VerbTerm(new VerbExpr.Lit(verb), token.column()));
                }
                default -> throw syntax(token);
            }
        }

        if (terms.isEmpty()) {
            throw syntax(peek());
        }
        return reduce(terms);
    }

    private Phrase reduce(List<Term> rawTerms) {
        List<Term> terms = groupNouns(rawTerms);

        if (terms.stream().allMatch(term -> term instanceof Term.VerbTerm)) {
            if (terms.size() != 1) {
                throw syntaxAt(terms.get(1).column());
            }
            return new Phrase.Verb(((Term.VerbTerm) terms.get(0)).verb());
        }

        while (terms.size() > 1) {
            int verbIndex = lastVerbIndex(terms);
            if (verbIndex < 0) {
                throw syntaxAt(terms.get(terms.size() - 1).column());
            }
            if (verbIndex == terms.size() - 1) {
                throw syntaxAt(terms.get(verbIndex).column());
            }

            Term.VerbTerm verb = (Term.VerbTerm) terms.get(verbIndex);
            if (!(terms.get(verbIndex + 1) instanceof Term.NounTerm right)) {
                throw syntaxAt(terms.get(verbIndex + 1).column());
            }
            Term left = verbIndex > 0 ? terms.get(verbIndex - 1) : null;

            int start;
            Expr result;
            if (left instanceof Term.NounTerm noun) {
                result = new Expr.Dyad(noun.expr(), verb.verb(), right.expr());
                start = verbIndex - 1;
            } else {
                result = new Expr.Monad(verb.verb(), right.expr());
                start = verbIndex;
            }

            List<Term> reduced = new ArrayList<>(terms.subList(0, start));
            reduced.add(new Term.NounTerm(result, terms.get(start).column()));
            reduced.addAll(terms.subList(verbIndex + 2, terms.size()));
            terms = reduced;
        }

        Term only = terms.get(0);
        if (only instanceof Term.NounTerm noun) {
            return new Phrase.Noun(noun.expr());
        }
        return new Phrase.Verb(((Term.VerbTerm) only).verb());
    }

    private static List<Term> groupNouns(List<Term> terms) {
        List<Term> grouped = new ArrayList<>();
        List<Expr> run = new ArrayList<>();
        int runColumn = 0;

        for (Term term : terms) {
            if (term instanceof Term.NounTerm noun) {
                if (run.isEmpty()) {
                    runColumn = noun.column();
                }
                run.add(noun.expr());
            } else {
                flushNounRun(run, runColumn, grouped);
                grouped.add(term);
            }
        }
        flushNounRun(run, runColumn, grouped);
        return grouped;
    }

    private static void flushNounRun(List<Expr> run, int column, List<Term> grouped) {
        if (run.isEmpty()) {
            return;
        }
        Expr expr = run.size() == 1 ? run.get(0) : new Expr.Strand(List.copyOf(run));
        grouped.add(new Term.NounTerm(expr, column));
        run.clear();
    }

    private static int lastVerbIndex(List<Term> terms) {
        for (int i = terms.size() - 1; i >= 0; i--) {
            if (terms.get(i) instanceof Term.VerbTerm) {
                return i;
            }
        }
        return -1;
    }

    private static Term asTerm(Phrase phrase, int column) {
        return switch (phrase) {
            case Phrase.Noun noun -> new Term.NounTerm(noun.expr(), column);
            case Phrase.Verb verb -> new Term.VerbTerm(verb.verb(), column);
        };
    }

    private Token next() {
        return tokens.get(index++);
    }

    private Token peek() {
        return tokens.get(index);
    }

    private static JError syntax(Token token) {
        return new JError("syntax error", token.column());
    }

    private static JError syntaxAt(int column) {
        return new JError("syntax error", column);
    }

    private sealed interface Term permits Term.NounTerm, Term.VerbTerm {
        int column();

        record NounTerm(Expr expr, int column) implements Term {
        }

        record VerbTerm(VerbExpr verb, int column) implements Term {
        }
    }
}
