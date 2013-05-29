package com.senseidb.clue.commands;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfo.DocValuesType;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;

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

  private void showDocId(int docid, int docBase, 
      Object docVals,
      DocValuesType docValType,
      BytesRef bytesRef,
      PrintStream out, int segmentid) throws Exception {
    int subid = docid - docBase;
    if (docVals != null) {

      String val;

      switch (docValType) {
      case NUMERIC:
        NumericDocValues dv = (NumericDocValues)docVals;
        val = String.valueOf(dv.get(subid));
        break;
      case BINARY:
        BinaryDocValues bv = (BinaryDocValues)docVals;
        bv.get(subid, bytesRef);
        val = bytesRef.utf8ToString();
        break;
      case SORTED: {
        SortedDocValues sv = (SortedDocValues)docVals;
        sv.get(subid, bytesRef);
        StringBuffer sb = new StringBuffer();
        sb.append("numTerms in field: ").append(sv.getValueCount()).append(", ");
        sb.append("value: [");
        sb.append(bytesRef.utf8ToString());
        sb.append("]");
        val = sb.toString();
        break;
      }
      case SORTED_SET: {
        SortedSetDocValues sv = (SortedSetDocValues)docVals;
        sv.setDocument(subid);
        long nextOrd;
        long count = sv.getValueCount();
        StringBuffer sb = new StringBuffer();
        sb.append("numTerms in field: ").append(count).append(", ");
        sb.append("values: [");
        boolean firstPass = true;
        while ((nextOrd = sv.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
          sv.lookupOrd(nextOrd, bytesRef);
          if (!firstPass) {
            sb.append(", ");
          }
          sb.append(bytesRef.utf8ToString());
          firstPass = false;
        }
        sb.append("]");
        val = sb.toString();
        break;
      }
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
  
  private Object readDocValues(String field, DocValuesType docValType, AtomicReader atomicReader) throws IOException{
    Object docVals = null;
    if (docValType == DocValuesType.NUMERIC) {
      docVals = atomicReader.getNumericDocValues(field);
    }
    else if (docValType == DocValuesType.BINARY) {
      docVals = atomicReader.getBinaryDocValues(field);
    }
    else if (docValType == DocValuesType.SORTED) {
      docVals = atomicReader.getSortedDocValues(field);
    }
    else if (docValType == DocValuesType.SORTED_SET) {
      docVals = atomicReader.getSortedSetDocValues(field);
    }
    return docVals;
  }

  private void showDocId(int docid, int docBase, String field,
      AtomicReader atomicReader, PrintStream out, int segmentid)
      throws Exception {
    FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);

    if (finfo == null || !finfo.hasDocValues()) {
      out.println("docvalue does not exist for field: " + field);
      return;
    }

    
    DocValuesType docValType = finfo.getDocValuesType();
    BytesRef bref = new BytesRef();
    
    showDocId(docid, docBase, readDocValues(field, docValType, atomicReader), docValType, bref, out, segmentid);
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
        
        DocValuesType docValType = finfo.getDocValuesType();
        BytesRef bref = new BytesRef();
        
        
        int maxDoc = atomicReader.maxDoc();
        
        for (int k = 0; k < maxDoc; ++k) {
          
          showDocId(k + ctx.docBase, ctx.docBase, readDocValues(field, docValType, atomicReader), docValType, bref, out, i);
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
