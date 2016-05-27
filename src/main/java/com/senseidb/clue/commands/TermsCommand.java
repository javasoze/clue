package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import com.senseidb.clue.ClueContext;
import com.senseidb.clue.api.BytesRefPrinter;

public class TermsCommand extends ClueCommand {

  public TermsCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "terms";
  }

  @Override
  public String help() {
    return "gets terms from the index, <field:term>, term can be a prefix";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    String field;
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
    else{
      out.println("Usage: field:value");
      return;
    }
    
    BytesRefPrinter bytesRefPrinter = ctx.getTermBytesRefDisplay().getBytesRefPrinter(field);
    
    boolean isExact = false;
    
    if (termVal != null){
      if (termVal.endsWith("*")){
         termVal = termVal.substring(0, termVal.length()-1);
      }
      else{
        isExact = true; 
      }
    }
        
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    TreeMap<BytesRef,TermsEnum> termMap = null;
    HashMap<BytesRef,AtomicInteger> termCountMap = new HashMap<BytesRef,AtomicInteger>();

    int numCount = 0;
    int numPerPage = 20;
    
    for (LeafReaderContext leaf : leaves){
      LeafReader atomicReader = leaf.reader();
      
      Terms terms = atomicReader.fields().terms(field);
      
      if (terms == null) {
        continue;
      }
      
      if (termMap == null){
        termMap = new TreeMap<BytesRef,TermsEnum>();
      }
      
      
      TermsEnum te = terms.iterator();
      BytesRef termBytes;
      if (termVal != null){
        if (isExact){
          if (!te.seekExact(new BytesRef(termVal))){
            continue;
          }
        }
        else{
          te.seekCeil(new BytesRef(termVal));
        }
        termBytes = te.term();
      }
      else{
        termBytes = te.next();
      }
      
      while(true){
        if (termBytes == null) break;        
        AtomicInteger count = termCountMap.get(termBytes);
        if (count == null){
          termCountMap.put(termBytes, new AtomicInteger(te.docFreq()));
          termMap.put(termBytes, te);
          break;
        }
        count.getAndAdd(te.docFreq());
        if (isExact){
          termBytes = null; 
        }
        else{
          termBytes = te.next();
        }
      }
    }
    
    while(termMap != null && !termMap.isEmpty()){
      numCount++;
      Entry<BytesRef,TermsEnum> entry = termMap.pollFirstEntry();
      if (entry == null) break;
      BytesRef key = entry.getKey();
      AtomicInteger count = termCountMap.remove(key);
      out.println(bytesRefPrinter.print(key)+" ("+count+") ");
      if (ctx.isInteractiveMode() && numCount % numPerPage == 0){
          out.println("Press q to break");
          int ch = System.in.read();
          if (ch == 'q' || ch == 'Q') {
            out.flush();
            return;
          }
      }
      TermsEnum te = entry.getValue();
      BytesRef nextKey = null;
      if (!isExact){
        nextKey = te.next();
      }
      while(true){
        if (nextKey == null) break;
        count = termCountMap.get(nextKey);
        if (count == null){
          termCountMap.put(nextKey, new AtomicInteger(te.docFreq()));
          termMap.put(nextKey, te);
          break;
        }
        count.getAndAdd(te.docFreq());
        nextKey = te.next();
      }
    }
    out.flush();
  }

}

