package j.eval;

import java.util.HashMap;
import java.util.Map;

import j.JError;
import j.parse.NameClassifier;

/**
 * The name table. Names hold either nouns or verbs, and that class is what the
 * parser consults while reading later sentences. An environment may have a parent,
 * so an explicit verb can bind local names while falling back to session globals.
 */
public final class Environment implements NameClassifier {
    private final Map<String, Value> names = new HashMap<>();
    private final Environment parent;

    public Environment() {
        this(null);
    }

    /** Creates a local scope whose unbound names fall back to {@code parent}. */
    public Environment(Environment parent) {
        this.parent = parent;
    }

    @Override
    public boolean isVerb(String name) {
        Value local = names.get(name);
        if (local != null) {
            return local instanceof Value.VerbValue;
        }
        return parent != null && parent.isVerb(name);
    }

    public Value lookup(String name) {
        Value value = names.get(name);
        if (value != null) {
            return value;
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        throw new JError("value error");
    }

    /** Stores a noun or verb. A later sentence sees the new part of speech. */
    public void assign(String name, Value value) {
        names.put(name, value);
    }

    /** The outermost scope, where {@code =:} stores names inside an explicit body. */
    public Environment root() {
        return parent == null ? this : parent.root();
    }
}
