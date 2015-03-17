package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.BytesRef;

import com.senseidb.clue.ClueContext;

public class StoredFieldCommand extends ClueCommand {

  public StoredFieldCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "stored";
  }

  @Override
  public String help() {
    return "displays stored data for a given field";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length != 2) {
      out.println("usage: field doc");
      return;
    }
    
    String field = args[0];
    
    int doc = Integer.parseInt(args[1]);
    
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    
    boolean found = false;
    boolean stored = false;
    
    
    for (LeafReaderContext ctx : leaves) {
      LeafReader atomicReader = ctx.reader();
      FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);
      if (finfo == null) continue;
      
      stored = true;
      
      int docID = doc - ctx.docBase;
      
      if (docID >= atomicReader.maxDoc()) {
          continue;
      }
      
      if (docID >= 0) {
      
        Document storedData = atomicReader.document(docID, new HashSet<String>(Arrays.asList(field)));
        
        if (storedData == null) continue;
        
        String strData = storedData.get(field);
        
        if (strData != null) {
          out.println(strData);
          found = true;
          break;
        }
        
        BytesRef bytesRef = storedData.getBinaryValue(field);
        if (bytesRef != null) {
          out.println(bytesRef);
          found = true;
          break;
        }
        
        BytesRef[] dataArray = storedData.getBinaryValues(field);
        
        if (dataArray == null || dataArray.length == 0) {
          continue;
        }
      
        for (BytesRef data : dataArray){
          out.println(data);
        }
        found = true;
        break;
      }
    }
    
    if (!stored) {
      out.println("stored data is not available for field: "+field);
      return;
    }

    if (!found) {
      out.println(doc+" not found");
      return;
    }
  }

}
