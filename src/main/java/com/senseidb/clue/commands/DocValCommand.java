package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.List;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.DocValuesType;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;

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

  private void showDocId(int docid, int docBase, NumericDocValues docVals, DocValuesType docValType, 
      PrintStream out, int segmentid) throws Exception {
    int subid = docid - docBase;
    if (docVals != null) {

      String val;

      switch (docValType) {
      case NUMERIC:
        val = String.valueOf(docVals.get(subid));
        break;
      default:
        val = null;
      }

      if (val == null) {
        out.println("cannot read doc value type: " + docValType);
      } else {
        out.println("type: " + docValType + ", val: " + val + ", segment: "
            + segmentid + ", docid: " + docid + ", subid: " + subid);
      }
    } else {
      out.println("doc value unavailable");
    }
  }

  private void showDocId(int docid, int docBase, String field,
      AtomicReader atomicReader, PrintStream out, int segmentid)
      throws Exception {
    FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);

    if (finfo == null || !finfo.hasDocValues()) {
      out.println("docvalue does not exist for field: " + field);
      return;
    }

    NumericDocValues docVals = atomicReader.getNumericDocValues(field);

    showDocId(docid, docBase, docVals, finfo.getDocValuesType(), out, segmentid);
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    String field = args[0];
    int docid;

    int numPerPage = 20;
    
    try {
      docid = Integer.parseInt(args[1]);
    } catch (Exception e) {
      out.println("invalid docid, all docs are shown");
      docid = -1;
    }

    IndexReader reader = ctx.getIndexReader();
    List<AtomicReaderContext> leaves = reader.leaves();
    if (docid >= 0) {
      for (int i = leaves.size() - 1; i >= 0; --i) {
        AtomicReaderContext ctx = leaves.get(i);
        if (ctx.docBase <= docid) {
          AtomicReader atomicReader = ctx.reader();
          showDocId(docid, ctx.docBase, field, atomicReader, out, i);
          out.flush();
          return;
        }
      }
    } else {
      for (int i = 0; i < leaves.size(); ++i) {
        AtomicReaderContext ctx = leaves.get(i);
        AtomicReader atomicReader = ctx.reader();
        FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);

        if (finfo == null || !finfo.hasDocValues()) {
          out.println("docvalue does not exist for field: " + field);
          break;
        }

        NumericDocValues docVals = atomicReader.getNumericDocValues(field);
        
        int maxDoc = atomicReader.maxDoc();
        
        for (int k = 0; k < maxDoc; ++k) {
          
          showDocId(k + ctx.docBase, ctx.docBase, docVals, finfo.getDocValuesType(), out, i);
          if (getContext().isInteractiveMode()){
            if ((k+1) % numPerPage == 0){
              out.println("Ctrl-D to break");
              int ch = System.in.read();
              if (ch == -1) {
                out.flush();
                return;
              }
            }
          }
        }
        out.flush();
      }
      return;
    }
  }

}
