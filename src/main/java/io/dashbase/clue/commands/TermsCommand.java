package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.api.BytesRefPrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

@Readonly
@Command(name = "terms", mixinStandardHelpOptions = true)
public class TermsCommand extends ClueCommand {

  private final LuceneContext ctx;

  public TermsCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-f", "--field"}, required = true, description = "field and term, e.g. field:term")
  private String field;

  @Override
  public String getName() {
    return "terms";
  }

  @Override
  public String help() {
    return "gets terms from the index, <field:term>, term can be a prefix";
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
    else{
      out.println("Usage: field:value");
      return;
    }
    
    BytesRefPrinter bytesRefPrinter = ctx.getTermBytesRefDisplay().getBytesRefPrinter(field);
    
    boolean isExact = false;
    
    if (termVal != null){
      if (termVal.endsWith("*")){
         termVal = termVal.substring(0, termVal.length()-1);
      }
      else{
        isExact = true; 
      }
    }
        
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    TreeMap<BytesRef,TermsEnum> termMap = null;
    HashMap<BytesRef,AtomicInteger> termCountMap = new HashMap<BytesRef,AtomicInteger>();

    int numCount = 0;
    int numPerPage = 20;
    int webLimit = -1;
    if (Boolean.getBoolean("clue.web.mode")) {
      webLimit = 100;
    }
    
    for (LeafReaderContext leaf : leaves){
      LeafReader atomicReader = leaf.reader();
      
      Terms terms = atomicReader.terms(field);
      
      if (terms != null) {
        if (termMap == null) {
          termMap = new TreeMap<BytesRef, TermsEnum>();
        }

        TermsEnum te = terms.iterator();
        BytesRef termBytes;
        if (termVal != null) {
          if (isExact) {
            if (!te.seekExact(new BytesRef(termVal))) {
              continue;
            }
          } else {
            te.seekCeil(new BytesRef(termVal));
          }
          termBytes = te.term();
        } else {
          termBytes = te.next();
        }

        while (true) {
          if (termBytes == null) break;
          AtomicInteger count = termCountMap.get(termBytes);
          if (count == null) {
            termCountMap.put(termBytes, new AtomicInteger(te.docFreq()));
            termMap.put(termBytes, te);
            break;
          }
          count.getAndAdd(te.docFreq());
          if (isExact) {
            termBytes = null;
          } else {
            termBytes = te.next();
          }
        }
      }
    }
    
    boolean truncated = false;
    while(termMap != null && !termMap.isEmpty()){
      numCount++;
      Entry<BytesRef,TermsEnum> entry = termMap.pollFirstEntry();
      if (entry == null) break;
      BytesRef key = entry.getKey();
      AtomicInteger count = termCountMap.remove(key);
      out.println(bytesRefPrinter.print(key)+" (" + count + ") ");
      if (webLimit > 0 && numCount >= webLimit) {
        truncated = true;
        break;
      }
      if (ctx.isInteractiveMode() && numCount % numPerPage == 0){
          out.println("Prtess q to break");
          int ch = System.in.read();
          if (ch == 'q' || ch == 'Q') {
            out.flush();
            return;
          }
      }
      TermsEnum te = entry.getValue();
      BytesRef nextKey = null;
      if (!isExact){
        nextKey = te.next();
      }
      while(true){
        if (nextKey == null) break;
        count = termCountMap.get(nextKey);
        if (count == null){
          termCountMap.put(nextKey, new AtomicInteger(te.docFreq()));
          termMap.put(nextKey, te);
          break;
        }
        count.getAndAdd(te.docFreq());
        nextKey = te.next();
      }
    }
    if (truncated) {
      out.println("truncated after " + webLimit + " terms (web mode)");
    }
    out.flush();
  }
}
