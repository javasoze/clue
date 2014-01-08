package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.List;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
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
    List<AtomicReaderContext> leaves = reader.leaves();
    int docBase = 0;
    int numPerPage = 20;
    for (AtomicReaderContext leaf : leaves){
      AtomicReader atomicReader = leaf.reader();
      Terms terms = atomicReader.terms(field);
      if (terms == null){
        continue;
      }
      boolean hasPositions = terms.hasPositions();
      
      if (terms != null && termVal != null){
        TermsEnum te = terms.iterator(null);
        int count = 0;
        if (te.seekExact(new BytesRef(termVal))){
          
          if (hasPositions){
            DocsAndPositionsEnum iter = te.docsAndPositions(atomicReader.getLiveDocs(), null);
            
            int docid;
            while((docid = iter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS){
              count++;
              out.print("docid: "+(docid+docBase)+", freq: "+iter.freq()+", ");
              for (int i=0;i<iter.freq();++i){
                out.print("pos "+i+": "+iter.nextPosition());
                out.print(", start offset: "+ iter.startOffset());
                out.print(", end offset: "+ iter.endOffset());
                BytesRef payload = iter.getPayload();
                if (payload != null){
                  out.print(", payload: " + payloadPrinter.print(payload));
                }
                out.print(";");
              }
              out.println();
              if (ctx.isInteractiveMode()){
                if (count % numPerPage == 0){
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
          else{
            DocsEnum iter = te.docs(atomicReader.getLiveDocs(), null);
          
            int docid;
            while((docid = iter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS){
              count++;
              out.println("docid: "+(docid+docBase)+", freq: "+iter.freq());
              if (ctx.isInteractiveMode()){
                if (count % numPerPage == 0){
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
      }
      docBase += atomicReader.maxDoc();
    }
  }

}
