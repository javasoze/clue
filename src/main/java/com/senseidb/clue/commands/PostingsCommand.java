package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

import com.senseidb.clue.ClueContext;
import com.senseidb.clue.api.BytesRefPrinter;

public class PostingsCommand extends ClueCommand {

  public PostingsCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "postings";
  }

  @Override
  public String help() {
    return "iterating postings given a term, e.g. <fieldname:fieldvalue>";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    String field = null;
    String termVal = null;
    try{
      field = args[0];
    }
    catch(Exception e){
      field = null;
    }
    
    if (field != null){
      String[] parts = field.split(":");
      if (parts.length > 1){
        field = parts[0];
        termVal = parts[1];
      }
    }
    
    if (field == null || termVal == null){
      out.println("usage: field:term");
      out.flush();
      return;
    }
    
    BytesRefPrinter payloadPrinter = ctx.getPayloadBytesRefDisplay().getBytesRefPrinter(field);
    
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    int docBase = 0;
    int numPerPage = 20;
    PostingsEnum postings = null;
    for (LeafReaderContext leaf : leaves){
      LeafReader atomicReader = leaf.reader();
      Terms terms = atomicReader.terms(field);
      if (terms == null){
        continue;
      }
      boolean hasPositions = terms.hasPositions();
      
      if (terms != null && termVal != null){
        TermsEnum te = terms.iterator();
        int count = 0;
        if (te.seekExact(new BytesRef(termVal))){
          
          if (hasPositions){
            postings = te.postings(postings, PostingsEnum.FREQS | 
                                             PostingsEnum.PAYLOADS | 
                                             PostingsEnum.POSITIONS | 
                                             PostingsEnum.OFFSETS);
            
            int docid;
            while((docid = postings.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS){
              count++;
              out.print("docid: "+(docid+docBase)+", freq: "+postings.freq()+", ");
              for (int i=0; i<postings.freq(); ++i){
                out.print("pos " + i + ": " + postings.nextPosition());
                out.print(", start offset: " + postings.startOffset());
                out.print(", end offset: "+ postings.endOffset());
                BytesRef payload = postings.getPayload();
                if (payload != null){
                  out.print(", payload: " + payloadPrinter.print(payload));
                }
                out.print(";");
              }
              out.println();
              if (ctx.isInteractiveMode() && count % numPerPage == 0){
                  out.println("Ctrl-D to break");
                  int ch = System.in.read();
                  if (ch == -1) {
                    out.flush();
                    return;
                  }
              }
            }
          }
          else{
            postings = te.postings(postings, PostingsEnum.FREQS);
          
            int docid;
            while((docid = postings.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS){
              count++;
              out.println("docid: "+(docid+docBase)+", freq: "+postings.freq());
              if (ctx.isInteractiveMode() && count % numPerPage == 0){
                  out.println("Ctrl-D to break");
                  int ch = System.in.read();
                  if (ch == -1) {
                    out.flush();
                    return;
                  }
              }
            }
          }
        }
      }
      docBase += atomicReader.maxDoc();
    }
  }

}
