package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.dashbase.clue.ClueContext;
import org.apache.lucene.index.IndexWriter;

public class DeleteUserCommitData extends ClueCommand {

	public DeleteUserCommitData(ClueContext ctx) {
		super(ctx);
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
	public void execute(String[] args, PrintStream out) throws Exception {
		IndexWriter writer = ctx.getIndexWriter();
		if (writer != null) {
			if (args.length > 0) {
				Iterable<Map.Entry<String, String>> commitData = writer.getLiveCommitData();
				List<Map.Entry<String, String>> commitList = new LinkedList<>();
				for (Map.Entry<String, String> dataEntry : commitData) {
					if (!dataEntry.equals(args[0])) {
						commitList.add(dataEntry);
					}
				}
			  if (commitList.size() > 0) {
				  writer.setLiveCommitData(commitList);
				  writer.commit();
				  ctx.refreshReader();
				  out.println("commit data: " + args[0] +" removed.");
			  } else {
			  	out.println("no commit data found, no action taken");
			  }
				
			} else {
				out.println("no delete key given, no action taken");
			}			
		} else {
			out.println("unable to open writer, index is in readonly mode");
		}
	}

}
