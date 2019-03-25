package io.dashbase.clue.client;

import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Supplier;

public class CmdlineHelper {
    private final ConsoleReader consoleReader;

    public CmdlineHelper(Supplier<Collection<String>> commandNameSupplier,
                         Supplier<Collection<String>> fieldNameSupplier) throws IOException {
        consoleReader = new ConsoleReader();
        consoleReader.setBellEnabled(false);

        Collection<String> commands = commandNameSupplier != null
                ? commandNameSupplier.get() : Collections.emptyList();

        Collection<String> fields = fieldNameSupplier != null
                ? fieldNameSupplier.get() : Collections.emptyList();

        LinkedList<Completer> completors = new LinkedList<Completer>();
        completors.add(new StringsCompleter(commands));
        completors.add(new StringsCompleter(fields));
        completors.add(new FileNameCompleter());
        consoleReader.addCompleter(new ArgumentCompleter(completors));
    }

    public String readCommand() {
        try {
            return consoleReader.readLine("> ");
        } catch (IOException e) {
            System.err.println("Error! Clue is unable to read line from stdin: " + e.getMessage());
            throw new IllegalStateException("Unable to read command line!", e);
        }
    }
}
