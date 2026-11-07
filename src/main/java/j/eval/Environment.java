package j.eval;

import java.util.HashMap;
import java.util.Map;

import j.JError;
import j.parse.NameClassifier;

/**
 * The global name table. Assignment is not implemented yet, so every lookup of an
 * unbound name is a {@code value error}; the class still carries the noun/verb
 * classification the parser needs once assignment arrives.
 */
public final class Environment implements NameClassifier {
    private final Map<String, Value> names = new HashMap<>();

    @Override
    public boolean isVerb(String name) {
        return names.get(name) instanceof Value.VerbValue;
    }

    public Value lookup(String name) {
        Value value = names.get(name);
        if (value == null) {
            throw new JError("value error");
        }
        return value;
    }
}
