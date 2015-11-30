package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.List;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import com.senseidb.clue.ClueContext;

public class TermVectorCommand extends ClueCommand {

  public TermVectorCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "tv";
  }

  @Override
  public String help() {
    return "shows term vector of a field for a doc";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length != 2) {
      out.println("usage: field doc1,doc2...");
      return;
    }
    
    String field = args[0];
    
    int doc = Integer.parseInt(args[1]);
    
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    
    boolean found = false;
    boolean tvFound = false;
    for (LeafReaderContext ctx : leaves) {
      LeafReader atomicReader = ctx.reader();
      FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);
      if (finfo == null || !finfo.hasVectors()) continue;
      
      tvFound = true;
      
      int docID = doc - ctx.docBase;
      if (docID >= 0) {
      
        Terms terms = atomicReader.getTermVector(docID, field);
        if (terms == null) continue;
        
        TermsEnum te = terms.iterator();      
      
        BytesRef text = null;
        
        while ((text = te.next()) != null) {
          long tf = te.totalTermFreq();
          out.println(text.utf8ToString()+" ("+tf+")");
        }
        found = true;
        break;
      }
    }
    
    if (!tvFound) {
      out.println("term vector is not available for field: "+field);
      return;
    }

    if (!found) {
      out.println(doc+" not found");
      return;
    }
    
    
  }

}
