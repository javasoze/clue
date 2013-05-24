package com.senseidb.clue.commands;

import java.io.PrintStream;

import org.apache.lucene.index.IndexWriter;

import com.senseidb.clue.ClueContext;

public class MergeCommand extends ClueCommand {

  public MergeCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "merge";
  }

  @Override
  public String help() {
    return "force merges segments into given N segments, input: number of max segments";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    int count;
    try{
      count = Integer.parseInt(args[0]);
    }
    catch(Exception e){
      out.println("default target segment count = 1");
      count = 1;
    }
    
    IndexWriter writer = ctx.getIndexWriter();
    if (writer != null) {
      writer.forceMerge(count, true);
      writer.commit();
      ctx.refreshReader();
    }
    else {
      out.println("unable to open index writer, index is in readonly mode");
    }
  }

}
