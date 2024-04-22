package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexWriter;

public class DeleteUserCommitData extends ClueCommand {

    private final LuceneContext luceneContext;

    public DeleteUserCommitData(LuceneContext ctx) {
        super(ctx);
        this.luceneContext = ctx;
    }

    @Override
    public String getName() {
        return "deletecommitdata";
    }

    @Override
    public String help() {
        return "Deletes user commit data by key";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-k", "--key").required(true).help("commit data key");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        IndexWriter writer = luceneContext.getIndexWriter();
        if (writer != null) {
            String key = args.get("key");
            Iterable<Map.Entry<String, String>> commitData = writer.getLiveCommitData();
            List<Map.Entry<String, String>> commitList = new LinkedList<>();
            for (Map.Entry<String, String> dataEntry : commitData) {
                if (!dataEntry.equals(key)) {
                    commitList.add(dataEntry);
                }
            }
            if (commitList.size() > 0) {
                writer.setLiveCommitData(commitList);
                writer.commit();
                luceneContext.refreshReader();
                out.println("commit data: " + key + " removed.");
            } else {
                out.println("no commit data found, no action taken");
            }
        } else {
            out.println("unable to open writer, index is in readonly mode");
        }
    }
}
