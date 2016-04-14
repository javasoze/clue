package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import com.senseidb.clue.ClueContext;
import com.senseidb.clue.util.MatchSomeDocsQuery;

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
    return "trims the index, <TRIM PERCENTAGE> <OPTIONS>, options are: head, tail, random"; 
  }
  
  private static final Set<String> SUPPORTED_OPTIONS = new HashSet<String>(
      Arrays.asList("head", "tail", "random"));
  
  static int[] getDocsToDelete(int maxDoc, int numDocsToDelete, String option) {
    option = option.toLowerCase();
    if (SUPPORTED_OPTIONS.contains(option)) {
      int[] docs = new int[numDocsToDelete];
      if ("head".equals(option) || "tail".equals(option)) {        
        int start = "head".equals(option) ? 0 : maxDoc - numDocsToDelete;
        for (int i = 0; i < numDocsToDelete; ++i ) {
          docs[i] = i + start;
        }
      }
      else {
        // random case
        HashSet<Integer> docsToDelete = new HashSet<Integer>(numDocsToDelete);
        Random rand = new Random();
        while (docsToDelete.size() < numDocsToDelete) {
          int docid = rand.nextInt(maxDoc);
          docsToDelete.add(docid);
        }
        int i = 0;
        for (Integer docid : docsToDelete) {
          docs[i++] = docid;
        }
        Arrays.sort(docs);
      }
      return docs;
    }
    else {
      throw new IllegalArgumentException("trim option: " + option + " not supported");
    }
  }
  
  private static Query buildDeleteQuery(final int percentToDelete) {
    assert percentToDelete >= 0 && percentToDelete <= 100;
    final Random rand = new Random();
    return new MatchSomeDocsQuery() {
      
      @Override
      public String toString(String field) {
        return null;
      }
      
      @Override
      protected boolean match(int docId) {
        int guess = rand.nextInt(100);
        if (guess < percentToDelete) {
          return true;
        }
        return false;
      }
    };
    
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length < 2) {
      out.println("usage: <TRIM PERCENTAGE> <OPTIONS>");
      return;
    }
    
    int trimPercent = Integer.parseInt(args[0]);
    
    if (trimPercent < 0 || trimPercent > 100) {
      throw new IllegalArgumentException("invalid percent: " + trimPercent);
    }
    
    IndexWriter writer = ctx.getIndexWriter();    
    if (writer != null) {
      out.println("force merge, expunge all deletes");
      writer.forceMerge(1);
      writer.commit();
      out.println("force merge successful into 1 segment");
      
      IndexReader reader = ctx.getIndexReader();
      
      writer.deleteDocuments(buildDeleteQuery(trimPercent));
      writer.commit();
      writer.forceMerge(1);
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
