package j.parse;

/** An adverb applied to a verb. */
public enum Adverb {
    INSERT("/"),
    PREFIX("\\"),
    REFLEX("~");

    private final String spelling;

    Adverb(String spelling) {
        this.spelling = spelling;
    }

    public String spelling() {
        return spelling;
    }

    public static Adverb forSpelling(String spelling) {
        return switch (spelling) {
            case "/" -> INSERT;
            case "\\" -> PREFIX;
            case "~" -> REFLEX;
            default -> null;
        };
    }
}
