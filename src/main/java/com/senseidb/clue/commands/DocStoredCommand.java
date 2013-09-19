package com.senseidb.clue.commands;

import java.io.PrintStream;

import org.apache.lucene.index.IndexReader;

import com.senseidb.clue.ClueContext;

public class DocStoredCommand  extends ClueCommand{

	public DocStoredCommand(ClueContext ctx) {
		super(ctx);
	}

	@Override
	public String getName() {
		return "docstored";
	}

	@Override
	public String help() {
		 return "gets stored fields for a given docId";

	}

	@Override
	public void execute(String[] args, PrintStream out) throws Exception {
		IndexReader r = ctx.getIndexReader();
		for(String s : args) {
			int doc = Integer.parseInt(s);
			System.out.println("doc: " + doc + ", document: " + r.document(doc));
		}
	}
}
