package j.repl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ReplTest {
    @Test
    void evaluatesLinesAndContinuesAfterAnError() {
        String input = "2 + 3\n1 +\n- 1 2\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

        Repl.run(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                output);

        String shown = bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(shown.contains("5\n"));
        assertTrue(shown.contains("|syntax error"));
        assertTrue(shown.contains("_1 _2"));
    }
}
