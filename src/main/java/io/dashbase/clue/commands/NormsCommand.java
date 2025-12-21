package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.*;

import java.io.PrintStream;
import java.util.List;

@Readonly
@Command(name = "norm", mixinStandardHelpOptions = true)
public class NormsCommand extends ClueCommand {

  private final LuceneContext ctx;

  public NormsCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-f", "--field"}, required = true, description = "field name")
  private String field;

  @Option(names = {"-d", "--docs"}, arity = "0..*", description = "doc ids, e.g. d1 d2 d3")
  private int[] docs;

  @Option(names = {"-n", "--num"}, defaultValue = "20", description = "num per page")
  private int numPerPage;

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
  protected void run(PrintStream out) throws Exception {
    String field = this.field;
    
    IndexReader reader = ctx.getIndexReader();
    
    
    int[] docidList = docs;

    List<LeafReaderContext> leaves = reader.leaves();
    if (docidList != null && docidList.length > 0) {
      for (int i = leaves.size() - 1; i >= 0; --i) {
        LeafReaderContext ctx = leaves.get(i);
        for (int docid : docidList) {
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
