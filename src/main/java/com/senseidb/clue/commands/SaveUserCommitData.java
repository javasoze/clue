package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.IndexWriter;

import com.senseidb.clue.ClueContext;

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
			Map<String, String> commitData = writer.getCommitData();
			if (commitData == null) {
				commitData= new HashMap<String, String>();
			}
			commitData.put(args[0], args[1]);
			writer.setCommitData(commitData);
			writer.commit();
			ctx.refreshReader();
			out.println("commit data: " + Arrays.toString(args) +" saved.");
		} else {
			out.println("unable to open writer, index is in readonly mode");
		}
	}

}
