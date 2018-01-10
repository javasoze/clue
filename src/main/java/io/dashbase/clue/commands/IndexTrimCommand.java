package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.Random;

import io.dashbase.clue.util.DocIdMatcher;
import io.dashbase.clue.util.MatcherDocIdSetIterator;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import io.dashbase.clue.ClueContext;
import io.dashbase.clue.util.MatchSomeDocsQuery;

public class IndexTrimCommand extends ClueCommand {

  public IndexTrimCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "trim";
  }

  @Override
  public String help() {
    return "trims the index, <TRIM PERCENTAGE>"; 
  }
  
  private static Query buildDeleteQuery(final int percentToDelete) {
    assert percentToDelete >= 0 && percentToDelete <= 100;

    return new MatchSomeDocsQuery(new MatcherDocIdSetIterator(DocIdMatcher.newRandomMatcher(percentToDelete)));
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length < 1) {
      out.println("usage: <TRIM PERCENTAGE>");
      return;
    }
    
    int trimPercent = Integer.parseInt(args[0]);
    
    if (trimPercent < 0 || trimPercent > 100) {
      throw new IllegalArgumentException("invalid percent: " + trimPercent);
    }
    
    IndexWriter writer = ctx.getIndexWriter();    
    if (writer != null) {      
      IndexReader reader = ctx.getIndexReader();
      
      writer.deleteDocuments(buildDeleteQuery(trimPercent));
      writer.commit();      
      ctx.refreshReader();
      reader = ctx.getIndexReader();
      out.println("trim successful, index now contains: " + reader.numDocs() + " docs.");
    }
    else {
      out.println("unable to open writer, index is in readonly mode");
    }
  }

}
