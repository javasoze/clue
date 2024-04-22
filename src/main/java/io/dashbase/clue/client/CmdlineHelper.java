package io.dashbase.clue.client;

import io.dashbase.clue.api.QueryBuilder;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

public class CmdlineHelper {
    private final ConsoleReader consoleReader;

    public CmdlineHelper(
            Supplier<Collection<String>> commandNameSupplier,
            Supplier<Collection<String>> fieldNameSupplier)
            throws IOException {
        consoleReader = new ConsoleReader();
        consoleReader.setBellEnabled(false);

        Collection<String> commands =
                commandNameSupplier != null ? commandNameSupplier.get() : Collections.emptyList();

        Collection<String> fields =
                fieldNameSupplier != null ? fieldNameSupplier.get() : Collections.emptyList();

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

    public static String toString(List<String> list) {
        StringBuilder buf = new StringBuilder();
        for (String s : list) {
            buf.append(s).append(" ");
        }
        return buf.toString().trim();
    }

    public static Query toQuery(List<String> list, QueryBuilder queryBuilder) throws Exception {
        String qstring = toString(list);
        Query q = null;
        if (qstring == null || qstring.isEmpty() || qstring.equals("*")) {
            q = new MatchAllDocsQuery();
        } else {
            q = queryBuilder.build(qstring);
        }
        return q;
    }
}
