package de.steffendingel.einvoicegenerator.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** {@link Console} with prepared answers; records prompts and output. Runs out of answers → aborted. */
public final class ScriptedConsole implements Console {

    private final Deque<String> answers;
    private final List<String> transcript = new ArrayList<>();

    public ScriptedConsole(String... answers) {
        this.answers = new ArrayDeque<>(List.of(answers));
    }

    @Override
    public String readLine(String prompt) {
        return answer(prompt);
    }

    @Override
    public String readPath(String prompt) {
        return answer(prompt);
    }

    private String answer(String prompt) {
        if (answers.isEmpty()) {
            transcript.add(prompt + "<Strg+D>");
            throw new DialogAbortedException();
        }
        String answer = answers.removeFirst();
        transcript.add(prompt + answer);
        return answer;
    }

    @Override
    public void println(String line) {
        transcript.add(line);
    }

    /** Everything the user would have seen, including their answers. */
    public String transcript() {
        return String.join("\n", transcript);
    }

    public boolean allAnswersUsed() {
        return answers.isEmpty();
    }
}
