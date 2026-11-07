package j.parse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import j.JError;
import j.lex.Lexer;
import j.lex.Token;

/**
 * Parses the lines of a {@code 3 : 0} body into statements.
 *
 * <p>Control words are recognized at the start of a line. This keeps expression
 * parsing unchanged while allowing J's common {@code if. do. else. end.},
 * {@code while. do. end.} and {@code for_i. do. end.} forms.
 */
public final class StatementParser {
    private final List<String> lines;
    private final NameClassifier names;
    private final Set<String> locals = new HashSet<>();
    private int index;

    private StatementParser(List<String> lines, NameClassifier names) {
        this.lines = List.copyOf(lines);
        this.names = names;
    }

    public static List<Statement> parse(List<String> lines, NameClassifier names) {
        StatementParser parser = new StatementParser(lines, names);
        List<Statement> body = parser.parseBlock(Set.of());
        if (parser.index < parser.lines.size()) {
            throw new JError("syntax error");
        }
        return body;
    }

    private List<Statement> parseBlock(Set<String> terminators) {
        List<Statement> statements = new ArrayList<>();
        while (true) {
            String line = currentLine();
            if (line == null) {
                if (!terminators.isEmpty()) {
                    throw new JError("syntax error");
                }
                return statements;
            }
            if (isTerminator(line, terminators)) {
                return statements;
            }
            statements.add(parseStatement(line));
        }
    }

    private Statement parseStatement(String line) {
        if (line.startsWith("if.")) {
            return parseIf(line);
        }
        if (line.startsWith("while.")) {
            return parseWhile(line);
        }
        if (line.startsWith("for.") || line.startsWith("for_")) {
            return parseFor(line);
        }
        if (line.startsWith("return.")) {
            index++;
            String value = line.substring("return.".length()).trim();
            return new Statement.Return(value.isEmpty() ? null : parseExpr(value));
        }
        if (line.startsWith("else.") || line.startsWith("end.")) {
            throw new JError("syntax error");
        }

        index++;
        Phrase phrase = new Parser(Lexer.lex(line), this::isVerb).parse();
        if (phrase instanceof Phrase.Verb) {
            throw new JError("syntax error");
        }
        return new Statement.Sentence(phrase);
    }

    private Statement parseIf(String line) {
        index++;
        String condition = controlExpression(line, "if.");
        List<Statement> thenBody = parseBlock(Set.of("else.", "end."));
        String terminator = currentLine();
        if (terminator != null && terminator.startsWith("else.")) {
            index++;
            List<Statement> elseBody = parseBlock(Set.of("end."));
            expectEnd();
            return new Statement.If(parseExpr(condition), thenBody, elseBody);
        }
        expectEnd();
        return new Statement.If(parseExpr(condition), thenBody, List.of());
    }

    private Statement parseWhile(String line) {
        index++;
        String condition = controlExpression(line, "while.");
        List<Statement> body = parseBlock(Set.of("end."));
        expectEnd();
        return new Statement.While(parseExpr(condition), body);
    }

    private Statement parseFor(String line) {
        index++;
        int dot = line.indexOf('.');
        if (dot < 0) {
            throw new JError("syntax error");
        }
        String header = line.substring(3, dot).trim();
        String variable = header.startsWith("_") && header.length() > 1
                ? header.substring(1)
                : "i";
        if (!variable.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new JError("syntax error");
        }

        String rest = line.substring(dot + 1).trim();
        if (!rest.endsWith("do.")) {
            throw new JError("syntax error");
        }
        String valuesText = rest.substring(0, rest.length() - "do.".length()).trim();
        Expr values = parseExpr(valuesText);

        locals.add(variable);
        locals.add(variable + "_index");
        try {
            List<Statement> body = parseBlock(Set.of("end."));
            expectEnd();
            return new Statement.For(variable, values, body);
        } finally {
            locals.remove(variable);
            locals.remove(variable + "_index");
        }
    }

    private void expectEnd() {
        String line = currentLine();
        if (line == null || !line.startsWith("end.")) {
            throw new JError("syntax error");
        }
        index++;
    }

    private String controlExpression(String line, String keyword) {
        String rest = line.substring(keyword.length()).trim();
        if (!rest.endsWith("do.")) {
            throw new JError("syntax error");
        }
        return rest.substring(0, rest.length() - "do.".length()).trim();
    }

    private Expr parseExpr(String source) {
        if (source.isEmpty()) {
            throw new JError("syntax error");
        }
        Phrase phrase = Parser.parse(source, this::isVerb);
        if (!(phrase instanceof Phrase.Noun noun)) {
            throw new JError("syntax error");
        }
        return noun.expr();
    }

    private boolean isVerb(String name) {
        return !name.equals("x")
                && !name.equals("y")
                && !locals.contains(name)
                && names.isVerb(name);
    }

    private String currentLine() {
        while (index < lines.size()) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("NB.")) {
                index++;
                continue;
            }
            return line;
        }
        return null;
    }

    private static boolean isTerminator(String line, Set<String> terminators) {
        for (String terminator : terminators) {
            if (line.startsWith(terminator)) {
                return true;
            }
        }
        return false;
    }
}
