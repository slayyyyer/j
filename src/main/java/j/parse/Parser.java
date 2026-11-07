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
 * <p>Adverbs and the rank conjunction are applied while scanning, so they bind
 * tighter than application. A phrase containing only verbs is a verb value: one
 * verb alone, or a hook/fork train when two or more remain.
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
        Phrase phrase = parseEntry();
        Token trailing = peek();
        if (trailing.type() != TokenType.EOF) {
            throw syntax(trailing);
        }
        return phrase;
    }

    private Phrase parseEntry() {
        if (peek().type() == TokenType.NAME && isAssignment(peek(1))) {
            return parseAssignment();
        }
        return parsePhrase();
    }

    private Phrase parseAssignment() {
        Token name = next();
        Token operator = next();
        if (peek().type() == TokenType.EOF || peek().type() == TokenType.RPAREN) {
            throw syntax(peek());
        }
        Phrase value = parseEntry();
        return new Phrase.Assign(
                name.text(),
                operator.type() == TokenType.ASSIGN_GLOBAL,
                value);
    }

    private static boolean isAssignment(Token token) {
        return token.type() == TokenType.ASSIGN_GLOBAL
                || token.type() == TokenType.ASSIGN_LOCAL;
    }

    private Phrase parsePhrase() {
        if (peek().type() == TokenType.NAME && isAssignment(peek(1))) {
            return parseAssignment();
        }

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
                case LBRACE2 ->
                        terms.add(new Term.VerbTerm(parseExplicitVerb(token), token.column()));
                case PRIMITIVE -> {
                    String spelling = token.text();
                    Adverb adverb = Adverb.forSpelling(spelling);
                    if (adverb != null) {
                        applyAdverb(terms, adverb, token);
                    } else if (spelling.equals("\"")) {
                        applyRank(terms, token);
                    } else {
                        Verb verb = Primitives.lookup(spelling);
                        if (verb == null) {
                            throw syntax(token);
                        }
                        terms.add(new Term.VerbTerm(new VerbExpr.Lit(verb), token.column()));
                    }
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
            List<VerbExpr> verbs = terms.stream()
                    .map(term -> ((Term.VerbTerm) term).verb())
                    .toList();
            VerbExpr verb = verbs.size() == 1 ? verbs.get(0) : new VerbExpr.Train(verbs);
            return new Phrase.Verb(verb);
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

    private VerbExpr.Explicit parseExplicitVerb(Token open) {
        int depth = 1;
        int cursor = index;
        while (cursor < tokens.size()) {
            Token token = tokens.get(cursor);
            if (token.type() == TokenType.LBRACE2) {
                depth++;
            } else if (token.type() == TokenType.RBRACE2) {
                depth--;
                if (depth == 0) {
                    break;
                }
            } else if (token.type() == TokenType.EOF) {
                break;
            }
            cursor++;
        }

        if (cursor >= tokens.size() || tokens.get(cursor).type() != TokenType.RBRACE2) {
            throw syntax(open);
        }

        List<Token> bodyTokens = new ArrayList<>(tokens.subList(index, cursor));
        bodyTokens.add(new Token(TokenType.EOF, "", null, open.column()));
        index = cursor + 1;

        NameClassifier bodyNames =
                name -> !name.equals("x") && !name.equals("y") && names.isVerb(name);
        Phrase body = new Parser(bodyTokens, bodyNames).parse();
        if (body instanceof Phrase.Verb) {
            throw syntax(open);
        }
        return new VerbExpr.Explicit(List.of(new Statement.Sentence(body)));
    }

    private void applyAdverb(List<Term> terms, Adverb adverb, Token token) {
        if (terms.isEmpty()
                || !(terms.get(terms.size() - 1) instanceof Term.VerbTerm operand)) {
            throw syntax(token);
        }
        terms.set(
                terms.size() - 1,
                new Term.VerbTerm(
                        new VerbExpr.WithAdverb(operand.verb(), adverb),
                        operand.column()));
    }

    private void applyRank(List<Term> terms, Token token) {
        if (terms.isEmpty()
                || !(terms.get(terms.size() - 1) instanceof Term.VerbTerm operand)) {
            throw syntax(token);
        }
        Expr ranks = parseRankSpec();
        terms.set(
                terms.size() - 1,
                new Term.VerbTerm(
                        new VerbExpr.WithRank(operand.verb(), ranks),
                        operand.column()));
    }

    private Expr parseRankSpec() {
        List<Expr> parts = new ArrayList<>();

        rankSpec:
        while (true) {
            Token token = peek();
            switch (token.type()) {
                case NUMBER, STRING -> {
                    next();
                    parts.add(new Expr.Lit(token.literal()));
                }
                case NAME -> {
                    if (names.isVerb(token.text())) {
                        break rankSpec;
                    }
                    next();
                    parts.add(new Expr.Ref(token.text()));
                }
                case LPAREN -> {
                    next();
                    Phrase inner = parsePhrase();
                    Token close = peek();
                    if (close.type() != TokenType.RPAREN) {
                        throw syntax(close);
                    }
                    next();
                    if (!(inner instanceof Phrase.Noun noun)) {
                        throw syntax(token);
                    }
                    parts.add(noun.expr());
                }
                default -> {
                    break rankSpec;
                }
            }
        }

        if (parts.isEmpty()) {
            throw syntax(peek());
        }
        return parts.size() == 1 ? parts.get(0) : new Expr.Strand(parts);
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
            case Phrase.Assign assign -> new Term.NounTerm(
                    new Expr.Assign(assign.name(), assign.global(), assign.value()), column);
        };
    }

    private Token next() {
        return tokens.get(index++);
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token peek(int offset) {
        return tokens.get(Math.min(index + offset, tokens.size() - 1));
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
