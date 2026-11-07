package j.parse;

/**
 * Tells the parser whether a name currently refers to a verb. J's grammar depends
 * on a name's part of speech, so the parser consults the environment as it reads.
 */
@FunctionalInterface
public interface NameClassifier {
    boolean isVerb(String name);
}
