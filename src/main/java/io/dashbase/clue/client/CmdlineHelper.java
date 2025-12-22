package io.dashbase.clue.client;

import io.dashbase.clue.api.QueryBuilder;
import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class CmdlineHelper {
    private final ConsoleReader consoleReader;

    public CmdlineHelper(Supplier<Collection<String>> commandNameSupplier,
                         Supplier<Collection<String>> fieldNameSupplier) throws IOException {
        consoleReader = new ConsoleReader();
        consoleReader.setBellEnabled(false);

        Supplier<Collection<String>> commandSupplier = commandNameSupplier != null
                ? commandNameSupplier : Collections::emptyList;
        Supplier<Collection<String>> fieldSupplier = fieldNameSupplier != null
                ? fieldNameSupplier : Collections::emptyList;

        LinkedList<Completer> completors = new LinkedList<Completer>();
        completors.add(new DynamicStringsCompleter(commandSupplier));
        completors.add(new DynamicStringsCompleter(fieldSupplier));
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
        if (qstring == null || qstring.isEmpty() || qstring.equals("*")){
            q = new MatchAllDocsQuery();
        }
        else{
            q = queryBuilder.build(qstring);
        }
        return q;
    }

    private static final class DynamicStringsCompleter implements Completer {
        private final Supplier<Collection<String>> valuesSupplier;

        private DynamicStringsCompleter(Supplier<Collection<String>> valuesSupplier) {
            this.valuesSupplier = valuesSupplier;
        }

        @Override
        public int complete(String buffer, int cursor, List<CharSequence> candidates) {
            String prefix = buffer == null ? "" : buffer;
            Collection<String> values = valuesSupplier.get();
            if (values == null || values.isEmpty()) {
                return -1;
            }
            for (String value : values) {
                if (value != null && value.startsWith(prefix)) {
                    candidates.add(value);
                }
            }
            if (candidates.size() == 1) {
                String value = candidates.get(0).toString();
                candidates.set(0, value + " ");
            }
            return candidates.isEmpty() ? -1 : 0;
        }
    }
}
