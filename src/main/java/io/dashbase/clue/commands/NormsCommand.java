package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;

import io.dashbase.clue.ClueContext;

@Readonly
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
      String val = null;

      if (docVals.advanceExact(subid)) {
        val = String.valueOf(docVals.longValue());
      }

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
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-f", "--field").required(true).help("field name");
    parser.addArgument("-d", "--docs").type(Integer.class).nargs("*").help("doc ids, e.g. d1 d2 d3");
    parser.addArgument("-n", "--num").type(Integer.class).setDefault(20).help("num per page");
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    String field = args.getString("field");
    
    IndexReader reader = getContext().getIndexReader();
    
    
    List<Integer> docidList = args.getList("docs");

    int numPerPage = args.getInt("num");

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
