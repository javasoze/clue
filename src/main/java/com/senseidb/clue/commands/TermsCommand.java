package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import com.senseidb.clue.ClueContext;

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
    return "gets terms from the index";
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
    
    IndexReader reader = ctx.getIndexReader();
    List<AtomicReaderContext> leaves = reader.leaves();
    TreeMap<BytesRef,TermsEnum> termMap = null;
    HashMap<BytesRef,AtomicInteger> termCountMap = new HashMap<BytesRef,AtomicInteger>();
    for (AtomicReaderContext leaf : leaves){
      AtomicReader atomicReader = leaf.reader();
      
      Terms terms = atomicReader.fields().terms(field);
      
      if (terms == null) {
        continue;
      }
      
      if (termMap == null){
        termMap = new TreeMap<BytesRef,TermsEnum>(terms.getComparator());
      }
      
      
      TermsEnum te = terms.iterator(null);
      BytesRef termBytes;
      if (termVal != null){
        te.seekCeil(new BytesRef(termVal));
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
        termBytes = te.next();
      }
    }
    
    while(!termMap.isEmpty()){
      Entry<BytesRef,TermsEnum> entry = termMap.pollFirstEntry();
      if (entry == null) break;
      BytesRef key = entry.getKey();
      AtomicInteger count = termCountMap.remove(key);
      out.println(key.utf8ToString()+" ("+count+") ");
      TermsEnum te = entry.getValue();
      BytesRef nextKey = te.next();
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
