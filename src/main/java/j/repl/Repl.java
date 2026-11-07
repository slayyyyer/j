package j.repl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import j.JError;

/**
 * The command-line REPL. Three spaces prompt like jconsole; errors are printed in
 * J's {@code |error message} form and evaluation continues. End-of-input exits.
 */
public final class Repl {
    private Repl() {
    }

    public static void main(String[] args) {
        run(System.in, System.out);
    }

    public static void run(InputStream input, PrintStream output) {
        Session session = new Session();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));

        while (true) {
            output.print("   ");
            output.flush();

            String line;
            try {
                line = reader.readLine();
            } catch (IOException failure) {
                return;
            }
            if (line == null) {
                output.println();
                return;
            }

            try {
                session.evaluate(line).ifPresent(output::println);
            } catch (JError error) {
                output.println("|" + error.getMessage());
            }
            output.flush();
        }
    }
}
