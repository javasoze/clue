package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.Map;

import org.apache.lucene.index.IndexWriter;

import com.senseidb.clue.ClueContext;

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
			  Map<String, String> commitData = writer.getCommitData();
			  if (commitData != null && commitData.size() > 0) {
			  	if (commitData.remove(args[0]) != null) {
			      writer.setCommitData(commitData);
			      writer.commit();
			      ctx.refreshReader();
			      out.println("commit data: " + args[0] +" removed.");
			  	} else {
			  		out.println("no commit data with the key: " + args[0] +", no action taken");
			  	}
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
