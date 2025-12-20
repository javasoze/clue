package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.api.BytesRefPrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

import java.io.PrintStream;
import java.util.List;

@Readonly
@Command(name = "postings", mixinStandardHelpOptions = true)
public class PostingsCommand extends ClueCommand {

  private final LuceneContext ctx;

  public PostingsCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-f", "--field"}, required = true, description = "field and term, e.g. field:term")
  private String field;

  @Option(names = {"-n", "--num"}, defaultValue = "20", description = "num per page, default 20")
  private int num;

  @Override
  public String getName() {
    return "postings";
  }

  @Override
  public String help() {
    return "iterating postings given a term, e.g. <fieldname:fieldvalue>";
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    String field = this.field;
    String termVal = null;

    if (field != null){
      String[] parts = field.split(":");
      if (parts.length > 1){
        field = parts[0];
        termVal = parts[1];
      }
    }
    
    if (field == null || termVal == null){
      out.println("usage: field:term");
      out.flush();
      return;
    }
    
    BytesRefPrinter payloadPrinter = ctx.getPayloadBytesRefDisplay().getBytesRefPrinter(field);
    
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    int docBase = 0;
    int numPerPage = num;
    PostingsEnum postings = null;
    for (LeafReaderContext leaf : leaves){
      LeafReader atomicReader = leaf.reader();
      Terms terms = atomicReader.terms(field);
      if (terms == null){
        continue;
      }
      boolean hasPositions = terms.hasPositions();
      
      if (terms != null && termVal != null){
        TermsEnum te = terms.iterator();
        int count = 0;
        if (te.seekExact(new BytesRef(termVal))){
          
          if (hasPositions){
            postings = te.postings(postings, PostingsEnum.FREQS | 
                                             PostingsEnum.PAYLOADS | 
                                             PostingsEnum.POSITIONS | 
                                             PostingsEnum.OFFSETS);
            
            int docid;
            while((docid = postings.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS){
              count++;
              out.print("docid: "+(docid+docBase)+", freq: "+postings.freq()+", ");
              for (int i=0; i<postings.freq(); ++i){
                out.print("pos " + i + ": " + postings.nextPosition());
                out.print(", start offset: " + postings.startOffset());
                out.print(", end offset: "+ postings.endOffset());
                BytesRef payload = postings.getPayload();
                if (payload != null){
                  out.print(", payload: " + payloadPrinter.print(payload));
                }
                out.print(";");
              }
              out.println();
              if (ctx.isInteractiveMode() && count % numPerPage == 0){
                  out.println("Ctrl-D to break");
                  int ch = System.in.read();
                  if (ch == -1) {
                    out.flush();
                    return;
                  }
              }
            }
          }
          else{
            postings = te.postings(postings, PostingsEnum.FREQS);
          
            int docid;
            while((docid = postings.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS){
              count++;
              out.println("docid: "+(docid+docBase)+", freq: "+postings.freq());
              if (ctx.isInteractiveMode() && count % numPerPage == 0){
                  out.println("Ctrl-D to break");
                  int ch = System.in.read();
                  if (ch == -1) {
                    out.flush();
                    return;
                  }
              }
            }
          }
        }
      }
      docBase += atomicReader.maxDoc();
    }
  }

}
