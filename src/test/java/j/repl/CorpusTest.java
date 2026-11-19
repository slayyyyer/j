package j.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import j.JError;

/**
 * Replays {@code web/test/corpus/j-corpus.tsv}, whose expected outputs were
 * captured from real J. The same file drives the TypeScript suite.
 *
 * <p>Run from the repository root so the corpus path resolves.
 */
class CorpusTest {
    private static final Path CORPUS = Path.of("web", "test", "corpus", "j-corpus.tsv");
    private static final Path KNOWN = Path.of("web", "test", "corpus", "known-divergences.tsv");

    private static final List<Case> CASES = readCases();
    private static final Map<String, String> KNOWN_DIVERGENCES = readKnownDivergences();

    private record Case(String id, String source, String expected) {
    }

    private static Stream<Arguments> corpusCases() {
        return CASES.stream()
                .map(item -> Arguments.of(item.id(), item.source(), item.expected()));
    }

    private static String evaluate(String source) {
        try {
            return new Session().evaluate(source).orElse("");
        } catch (JError error) {
            return "|" + error.getMessage();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusCases")
    void matchesJ(String id, String source, String expected) {
        String reason = KNOWN_DIVERGENCES.get(id);
        Assumptions.assumeTrue(reason == null, "known divergence: " + reason);
        assertEquals(expected, evaluate(source), id);
    }

    @Test
    void knownDivergencesAreStillReal() {
        for (Map.Entry<String, String> entry : KNOWN_DIVERGENCES.entrySet()) {
            String id = entry.getKey();
            Case item = CASES.stream()
                    .filter(candidate -> candidate.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(id + " is not a corpus case"));
            assertNotEquals(item.expected(), evaluate(item.source()),
                    id + " now matches J; remove it from known-divergences.tsv");
        }
    }

    private static List<Case> readCases() {
        List<Case> cases = new ArrayList<>();
        for (String line : readLines(CORPUS)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length != 3) {
                throw new IllegalStateException("corpus line needs three fields: " + line);
            }
            cases.add(new Case(fields[0], unescape(fields[1]), unescape(fields[2])));
        }
        return cases;
    }

    private static Map<String, String> readKnownDivergences() {
        Map<String, String> known = new LinkedHashMap<>();
        for (String line : readLines(KNOWN)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            known.put(fields[0], fields.length > 1 ? fields[1] : "known divergence");
        }
        return known;
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "cannot read " + path + "; run the tests from the repository root", failure);
        }
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                result.append(character);
                continue;
            }
            char escape = value.charAt(++index);
            switch (escape) {
                case 'n' -> result.append('\n');
                case 't' -> result.append('\t');
                case '\\' -> result.append('\\');
                default -> throw new IllegalStateException("unsupported escape \\" + escape);
            }
        }
        return result.toString();
    }
}
