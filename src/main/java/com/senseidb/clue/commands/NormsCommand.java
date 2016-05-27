package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;

import com.senseidb.clue.ClueContext;

public class NormsCommand extends ClueCommand {

  public NormsCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "norm";
  }

  @Override
  public String help() {
    return "displays norm values for a field for a list of documents";
  }
  
  private void showDocId(int docid, int docBase, 
      NumericDocValues docVals,
      PrintStream out, int segmentid) throws Exception {
    int subid = docid - docBase;
    if (docVals != null) {
      String val = String.valueOf(docVals.get(subid));

      if (val == null) {
        out.println("cannot read norm for docid: " + docid + ", subid: " + subid);
      } else {
        out.println("norm: "+val + ", segment: " + segmentid + ", docid: " + docid + ", subid: " + subid);
      }
    } else {
      out.println("doc value unavailable");
    }
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length < 1) {
      out.println("usage: field doc1,doc2...");
      return;
    }
    String field = args[0];
    
    IndexReader reader = getContext().getIndexReader();
    
    
    List<Integer> docidList = new ArrayList<Integer>();

    int numPerPage = 20;
    
    try {
      String[] docListStrings = args[1].split(",");
      for (String s : docListStrings) {
        docidList.add(Integer.parseInt(s));
      }
    } catch (Exception e) {
      out.println("invalid docid, all docs are shown");
      docidList = null;
    }

    List<LeafReaderContext> leaves = reader.leaves();
    if (docidList != null && !docidList.isEmpty()) {
      for (int i = leaves.size() - 1; i >= 0; --i) {
        LeafReaderContext ctx = leaves.get(i);
        for (Integer docid : docidList) {
          if (ctx.docBase <= docid) {
            LeafReader atomicReader = ctx.reader();
            FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);
            
            if (finfo == null || !finfo.hasNorms()) {
              out.println("norm does not exist for field: " + field);
              return;
            }
            
            showDocId(docid, ctx.docBase, atomicReader.getNormValues(field), out, i);
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

        if (finfo == null || !finfo.hasNorms()) {
          out.println("norm does not exist for field: " + field);
          break;
        }
        
        int maxDoc = atomicReader.maxDoc();
        
        for (int k = 0; k < maxDoc; ++k) {
          
          showDocId(k + ctx.docBase, ctx.docBase, atomicReader.getNormValues(field), out, i);
          if (getContext().isInteractiveMode() && (k+1) % numPerPage == 0){
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
