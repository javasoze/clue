package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.List;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.DocValues.Source;
import org.apache.lucene.index.DocValues.Type;
import org.apache.lucene.index.IndexReader;

import com.senseidb.clue.ClueContext;

public class DocValCommand extends ClueCommand {

  public DocValCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "docval";
  }

  @Override
  public String help() {
    return "gets doc value for a given doc, <field> <docid>, if <docid> not specified, all docs are shown";
  }

  private void showDocId(int docid, int docBase, String field, AtomicReader atomicReader, PrintStream out, int segmentid) throws Exception{
    int subid = docid-docBase;
    DocValues docVals = atomicReader.docValues(field);
    Source src = null;
    if (docVals != null && (src = docVals.getSource()) != null){
      String val;
      Type docValType = src.getType();
      switch(docValType){
      case VAR_INTS:
      case FIXED_INTS_16:
      case FIXED_INTS_32:
      case FIXED_INTS_64:
      case FIXED_INTS_8:
        val = String.valueOf(src.getInt(subid));
        break;
      case FLOAT_32:
      case FLOAT_64:
        val = String.valueOf(src.getFloat(subid));
        break;
      case BYTES_FIXED_DEREF:
      case BYTES_FIXED_SORTED:
      case BYTES_FIXED_STRAIGHT:
      case BYTES_VAR_DEREF:
      case BYTES_VAR_SORTED:
      case BYTES_VAR_STRAIGHT:
        val = String.valueOf(src.getBytes(subid, null));
        break;
        default:
          val = null;
      }
      
      if (val == null){
        out.println("cannot read doc value type: "+docValType);
      }
      else{
        out.println("type: "+docValType+", val: "+val+", segment: "+segmentid+", docid: "+docid+", subid: "+subid); 
      }
    }
    else{
      out.println("doc value unavailable");
    }
  }
  
  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    String field = args[0];
    int docid;
    
    try{
      docid = Integer.parseInt(args[1]);
    }
    catch(Exception e){
      out.println("invalid docid, all docs are shown");
      docid = -1;
    }
    
    IndexReader reader = ctx.getIndexReader();
    List<AtomicReaderContext> leaves = reader.leaves();
    if (docid >= 0){
      for (int i=leaves.size()-1; i>=0; --i){
        AtomicReaderContext ctx = leaves.get(i);
        if (ctx.docBase <= docid){
          AtomicReader atomicReader = ctx.reader();          
          showDocId(docid, ctx.docBase, field, atomicReader, out, i);
          out.flush();
          return;
        }
      }
    }
    else{
      for (int i=0; i<leaves.size(); ++i){
        AtomicReaderContext ctx = leaves.get(i);
        AtomicReader atomicReader = ctx.reader();
        for (int k=0;k<atomicReader.maxDoc();++k){
          showDocId(k+ctx.docBase, ctx.docBase, field, atomicReader, out, i);
        }
        out.flush();
      }
      return;
    }
  }

}
