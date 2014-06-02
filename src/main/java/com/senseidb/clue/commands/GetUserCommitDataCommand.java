package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;

import com.senseidb.clue.ClueContext;

public class GetUserCommitDataCommand extends ClueCommand {

	public GetUserCommitDataCommand(ClueContext ctx) {
		super(ctx);
	}

	@Override
	public String getName() {
		return "showcommitdata";
	}

	@Override
	public String help() {
		return "Shows user commit data";
	}

	@Override
	public void execute(String[] args, PrintStream out) throws Exception {
		IndexReader reader = ctx.getIndexReader();
		if (reader instanceof DirectoryReader) {
			DirectoryReader dirReader = (DirectoryReader) reader;
			Map<String, String> userData = dirReader.getIndexCommit().getUserData();
			if (userData == null || userData.size() == 0) {
				out.println("Empty user commit data");				
			} else {
			  for (Entry<String, String> entry : userData.entrySet()) {
				  out.println("key: " + entry.getKey()+"\tvalue: " + entry.getValue());
			  }
			}
			out.flush();
		} else {
			throw new IllegalArgumentException("can only read user commit data from instances of " + DirectoryReader.class);
		}
	}
}
