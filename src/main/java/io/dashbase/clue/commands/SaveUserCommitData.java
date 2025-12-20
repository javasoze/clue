package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.IndexWriter;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

@Command(name = "savecommitdata", mixinStandardHelpOptions = true)
public class SaveUserCommitData extends ClueCommand {

	private final LuceneContext ctx;

	public SaveUserCommitData(LuceneContext ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	@Option(names = {"-k", "--key"}, required = true)
	private String key;

	@Option(names = {"-v", "--value"}, required = true)
	private String value;

	@Override
	public String getName() {
		return "savecommitdata";
	}

	@Override
	public String help() {
		return "Save user commit data";
	}

	@Override
	protected void run(PrintStream out) throws Exception {
		IndexWriter writer = ctx.getIndexWriter();
		String val = value;
		if (writer != null) {
			Iterable<Map.Entry<String, String>> commitData = writer.getLiveCommitData();
			HashMap<String, String> commitMap = new HashMap<>();
			if (commitData != null) {
				for (Map.Entry<String, String> entry : commitData) {
					commitMap.put(entry.getKey(), entry.getValue());
				}
			}
			commitMap.put(key, val);
			writer.setLiveCommitData(commitMap.entrySet());
			writer.commit();
			ctx.refreshReader();
			out.println(String.format("commit data key: %s, val: %s  saved.", key, val));
		} else {
			out.println("unable to open writer, index is in readonly mode");
		}
	}

}
