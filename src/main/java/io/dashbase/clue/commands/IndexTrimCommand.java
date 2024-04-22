package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.util.DocIdMatcher;
import io.dashbase.clue.util.MatchSomeDocsQuery;
import io.dashbase.clue.util.MatcherDocIdSetIterator;
import java.io.PrintStream;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

public class IndexTrimCommand extends ClueCommand {

    private final LuceneContext ctx;

    public IndexTrimCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "trim";
    }

    @Override
    public String help() {
        return "trims the index, <TRIM PERCENTAGE>";
    }

    private static Query buildDeleteQuery(final int percentToDelete, int maxDoc) {
        assert percentToDelete >= 0 && percentToDelete <= 100;
        return new MatchSomeDocsQuery(
                new MatcherDocIdSetIterator(
                        DocIdMatcher.newRandomMatcher(percentToDelete), maxDoc));
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-p", "--percent")
                .type(Integer.class)
                .required(true)
                .help("percent to trim");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        int trimPercent = args.getInt("percent");

        if (trimPercent < 0 || trimPercent > 100) {
            throw new IllegalArgumentException("invalid percent: " + trimPercent);
        }

        IndexWriter writer = ctx.getIndexWriter();
        if (writer != null) {
            IndexReader reader = ctx.getIndexReader();

            writer.deleteDocuments(buildDeleteQuery(trimPercent, reader.maxDoc()));
            writer.commit();
            ctx.refreshReader();
            reader = ctx.getIndexReader();
            out.println("trim successful, index now contains: " + reader.numDocs() + " docs.");
        } else {
            out.println("unable to open writer, index is in readonly mode");
        }
    }
}
