package j.parse;

import java.util.List;

/** A statement in an explicit verb body. */
public sealed interface Statement
        permits Statement.Sentence, Statement.If, Statement.While, Statement.For,
                Statement.Return {
    /** An ordinary J sentence, including assignment. */
    record Sentence(Phrase phrase) implements Statement {
    }

    /** {@code if. condition do. ... [else. ...] end.} */
    record If(Expr condition, List<Statement> thenBody, List<Statement> elseBody)
            implements Statement {
        public If {
            thenBody = List.copyOf(thenBody);
            elseBody = List.copyOf(elseBody);
        }
    }

    /** {@code while. condition do. ... end.} */
    record While(Expr condition, List<Statement> body) implements Statement {
        public While {
            body = List.copyOf(body);
        }
    }

    /** {@code for_i. values do. ... end.} */
    record For(String variable, Expr values, List<Statement> body) implements Statement {
        public For {
            body = List.copyOf(body);
        }
    }

    /** {@code return. value}; a null value is a bare {@code return.}. */
    record Return(Expr value) implements Statement {
    }
}
