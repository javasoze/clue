package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.*;

import org.apache.lucene.index.IndexWriter;

import io.dashbase.clue.ClueContext;

public class SaveUserCommitData extends ClueCommand {

	public SaveUserCommitData(ClueContext ctx) {
		super(ctx);
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
	public void execute(String[] args, PrintStream out) throws Exception {
		IndexWriter writer = ctx.getIndexWriter();
		if (writer != null) {
			if (args.length != 2) {
				throw new IllegalArgumentException("expected 2 arguments indicating key and value");
			}
			Iterable<Map.Entry<String, String>> commitData = writer.getLiveCommitData();
			HashMap<String, String> commitMap = new HashMap<>();
			if (commitData != null) {
				for (Map.Entry<String, String> entry : commitData) {
					commitMap.put(entry.getKey(), entry.getValue());
				}
			}
			writer.setLiveCommitData(commitData);
			writer.commit();
			ctx.refreshReader();
			out.println("commit data: " + Arrays.toString(args) +" saved.");
		} else {
			out.println("unable to open writer, index is in readonly mode");
		}
	}

}
