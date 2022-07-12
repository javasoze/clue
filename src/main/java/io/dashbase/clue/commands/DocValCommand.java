package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

@Readonly
public class DocValCommand extends ClueCommand {

  private static final String NUM_TERMS_IN_FIELD = "numTerms in field: ";

  private final LuceneContext ctx;

  public DocValCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
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

      String val = null;

      switch (docValType) {
      case NUMERIC:
        NumericDocValues dv = (NumericDocValues)docVals;
        if (dv.advanceExact(subid)) {
          val = String.valueOf(dv.longValue());
        }
        break;
      case BINARY:
        BinaryDocValues bv = (BinaryDocValues)docVals;
        if (bv.advanceExact(subid)) {
          bytesRef = bv.binaryValue();
          val = bytesRef.utf8ToString();
        }
        break;
      case SORTED: {
        SortedDocValues sv = (SortedDocValues)docVals;
        if (sv.advanceExact(subid)) {
          bytesRef = sv.lookupOrd(sv.ordValue());
          StringBuilder sb = new StringBuilder();
          sb.append(NUM_TERMS_IN_FIELD).append(sv.getValueCount()).append(", ");
          sb.append("value: [");
          sb.append(bytesRef.utf8ToString());
          sb.append("]");
          val = sb.toString();
        }
        break;
      }
      case SORTED_SET: {
        SortedSetDocValues sv = (SortedSetDocValues)docVals;
        if (sv.advanceExact(subid)) {
          long nextOrd;
          long count = sv.getValueCount();
          StringBuilder sb = new StringBuilder();
          sb.append(NUM_TERMS_IN_FIELD).append(count).append(", ");
          sb.append("values: [");
          boolean firstPass = true;
          while ((nextOrd = sv.nextOrd()) != SortedSetDocValues.NO_MORE_ORDS) {
            bytesRef = sv.lookupOrd(nextOrd);
            if (!firstPass) {
              sb.append(", ");
            }
            sb.append(bytesRef.utf8ToString());
            firstPass = false;
          }
          sb.append("]");
          val = sb.toString();
        }
        break;
      }
      case SORTED_NUMERIC: {
        SortedNumericDocValues sv = (SortedNumericDocValues)docVals;
        if (sv.advanceExact(subid)) {
          int count = sv.docValueCount();
          StringBuilder sb = new StringBuilder();
          sb.append(NUM_TERMS_IN_FIELD).append(count).append(", ");
          sb.append("values: [");
          boolean firstPass = true;
          for (int i = 0; i < count; ++i) {
            long nextVal = sv.nextValue();
            if (!firstPass) {
              sb.append(", ");
            }
            sb.append(String.valueOf(nextVal));
            firstPass = false;
          }
          sb.append("]");
          val = sb.toString();
        }
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
  
  private Object readDocValues(String field, DocValuesType docValType, LeafReader atomicReader) throws IOException{
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
    else if (docValType == DocValuesType.SORTED_NUMERIC) {
      docVals = atomicReader.getSortedNumericDocValues(field);
    }
    else if (docValType == DocValuesType.SORTED_SET) {
      docVals = atomicReader.getSortedSetDocValues(field);
    }
    return docVals;
  }

  private void showDocId(int docid, int docBase, String field,
      LeafReader atomicReader, PrintStream out, int segmentid)
      throws Exception {
    FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);

    if (finfo == null || finfo.getDocValuesType() == DocValuesType.NONE) {
      out.println("docvalue does not exist for field: " + field);
      return;
    }

    
    DocValuesType docValType = finfo.getDocValuesType();
    BytesRef bref = new BytesRef();
    
    showDocId(docid, docBase, readDocValues(field, docValType, atomicReader), docValType, bref, out, segmentid);
  }

  @Override
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-f", "--field").required(true).help("field name");
    parser.addArgument("-d", "--docs").type(Integer.class).nargs("*").help("doc ids, e.g. d1 d2 d3");
    parser.addArgument("-n", "--num").type(Integer.class).setDefault(20).help("num per page");
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    String field = args.getString("field");
    
    List<Integer> docidList = args.getList("docs");

    int numPerPage = args.getInt("num");

    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    if (docidList != null && !docidList.isEmpty()) {
      for (int i = leaves.size() - 1; i >= 0; --i) {
        LeafReaderContext ctx = leaves.get(i);
        for (Integer docid : docidList) {
          if (ctx.docBase <= docid) {
            LeafReader atomicReader = ctx.reader();
            showDocId(docid, ctx.docBase, field, atomicReader, out, i);
          }
        }
      }
      out.flush();
      return;
    } else {
      for (int i = 0; i < leaves.size(); ++i) {
        LeafReaderContext ctx = leaves.get(i);
        LeafReader atomicReader = ctx.reader();
        FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);

        if (finfo == null || finfo.getDocValuesType() == DocValuesType.NONE) {
          out.println("docvalue does not exist for field: " + field);
          break;
        }
        
        DocValuesType docValType = finfo.getDocValuesType();
        BytesRef bref = new BytesRef();

        int maxDoc = atomicReader.maxDoc();
        
        for (int k = 0; k < maxDoc; ++k) {
          
          showDocId(k + ctx.docBase, ctx.docBase, readDocValues(field, docValType, atomicReader), docValType, bref, out, i);
          if (this.ctx.isInteractiveMode() && (k+1) % numPerPage == 0){
              out.println("Ctrl-D to break");
              int ch = System.in.read();
              if (ch == -1) {
                out.flush();
                return;
              }
          }
        }
        out.flush();
      }
      return;
    }
  }

}
