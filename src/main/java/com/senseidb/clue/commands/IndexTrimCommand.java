package com.senseidb.clue.commands;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;

import com.senseidb.clue.ClueContext;
import com.senseidb.clue.util.IntArrayDocIdSetIterator;

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

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length < 2) {
      out.println("usage: <TRIM PERCENTAGE> <OPTIONS>");
      return;
    }
    
    double trimPercent = Double.parseDouble(args[0]);
    
    
    
    IndexWriter writer = ctx.getIndexWriter();    
    if (writer != null) {
      out.println("force merge, expunge all deletes");
      writer.forceMerge(1);
      writer.commit();
      out.println("force merge successful into 1 segment");
      
      IndexReader reader = ctx.getIndexReader();
      int maxDoc = reader.maxDoc();
      
      final int numDocsToDelete = (int) ((double) maxDoc * trimPercent / 100.0);
      
      final int[] docidsToDelete = getDocsToDelete(maxDoc, numDocsToDelete, args[1]);
      
      Filter f = new Filter() {
  
        @Override
        public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
          return new DocIdSet() {
  
            @Override
            public DocIdSetIterator iterator() throws IOException {
              return new IntArrayDocIdSetIterator(docidsToDelete);
            }
          };
        }
      };
  
      writer.deleteDocuments(new ConstantScoreQuery(f));
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
