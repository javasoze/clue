package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import io.dashbase.clue.LuceneContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexWriter;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class SaveUserCommitData extends ClueCommand {

	private final LuceneContext ctx;

	public SaveUserCommitData(LuceneContext ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	@Override
	public String getName() {
		return "savecommitdata";
	}

	@Override
	public String help() {
		return "Save user commit data";
	}

	@Override
	protected ArgumentParser buildParser(ArgumentParser parser) {
		parser.addArgument("-k", "--key").required(true);
		parser.addArgument("-v", "--value").required(true);
		return parser;
	}

	@Override
	public void execute(Namespace args, PrintStream out) throws Exception {
		IndexWriter writer = ctx.getIndexWriter();
		String key = args.getString("key");
		String val = args.getString("value");
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
