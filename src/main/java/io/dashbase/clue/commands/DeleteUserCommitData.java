package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.IndexWriter;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Command(name = "deletecommitdata", mixinStandardHelpOptions = true)
public class DeleteUserCommitData extends ClueCommand {

	private final LuceneContext luceneContext;
	public DeleteUserCommitData(LuceneContext ctx) {
		super(ctx);
		this.luceneContext = ctx;
	}

	@Option(names = {"-k", "--key"}, required = true, description = "commit data key")
	private String key;

	@Override
	public String getName() {
		return "deletecommitdata";
	}

	@Override
	public String help() {
		return "Deletes user commit data by key";
	}

	@Override
	protected void run(PrintStream out) throws Exception {
		IndexWriter writer = luceneContext.getIndexWriter();
		if (writer != null) {
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
			  out.println("commit data: " + key +" removed.");
		    } else {
			  out.println("no commit data found, no action taken");
		    }
		} else {
			out.println("unable to open writer, index is in readonly mode");
		}
	}
}
