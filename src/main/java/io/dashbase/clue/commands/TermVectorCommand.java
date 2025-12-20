package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.PrintStream;
import java.util.List;

@Readonly
@Command(name = "tv", mixinStandardHelpOptions = true)
public class TermVectorCommand extends ClueCommand {

  private final LuceneContext ctx;

  public TermVectorCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-f", "--field"}, required = true, description = "field name")
  private String field;

  @Option(names = {"-d", "--doc"}, required = true, description = "docid")
  private int doc;

  @Override
  public String getName() {
    return "tv";
  }

  @Override
  public String help() {
    return "shows term vector of a field for a doc";
  }

  @Override
  protected void run(PrintStream out) throws Exception {

    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    
    boolean found = false;
    boolean tvFound = false;
    for (LeafReaderContext ctx : leaves) {
      LeafReader atomicReader = ctx.reader();
      FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);
      if (finfo == null || !finfo.hasTermVectors()) continue;
      
      tvFound = true;
      
      int docID = doc - ctx.docBase;
      if (docID >= 0) {
        var termVectors = atomicReader.termVectors();
        Terms terms = termVectors.get(docID, field);
        if (terms == null) continue;
        
        TermsEnum te = terms.iterator();      
      
        BytesRef text = null;
        
        while ((text = te.next()) != null) {
          long tf = te.totalTermFreq();
          out.println(text.utf8ToString()+" ("+tf+")");
        }
        found = true;
        break;
      }
    }
    
    if (!tvFound) {
      out.println("term vector is not available for field: "+field);
      return;
    }

    if (!found) {
      out.println(doc+" not found");
      return;
    }
  }
}
