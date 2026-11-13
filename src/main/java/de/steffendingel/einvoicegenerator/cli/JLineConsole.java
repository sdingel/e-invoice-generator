package de.steffendingel.einvoicegenerator.cli;

import org.jline.builtins.Completers.FileNameCompleter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * {@link Console} on the terminal with JLine: line editing, history within the session and Tab
 * completion for file paths.
 */
public final class JLineConsole implements Console, AutoCloseable {

    private final Terminal terminal;
    private final LineReader textReader;
    private final LineReader pathReader;

    public JLineConsole() throws IOException {
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.textReader = LineReaderBuilder.builder().terminal(terminal).build();
        this.pathReader = LineReaderBuilder.builder().terminal(terminal).completer(new FileNameCompleter()).build();
    }

    @Override
    public String readLine(String prompt) {
        return read(textReader, prompt);
    }

    @Override
    public String readPath(String prompt) {
        return read(pathReader, prompt);
    }

    private static String read(LineReader reader, String prompt) {
        try {
            return reader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            throw new DialogAbortedException();
        }
    }

    @Override
    public void println(String line) {
        terminal.writer().println(line);
        terminal.flush();
    }

    @Override
    public void close() throws IOException {
        terminal.close();
    }
}
