package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.dashbase.clue.ClueContext;
import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.api.BytesRefPrinter;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

@Readonly
public class TermsCommand extends ClueCommand {

  private final LuceneContext ctx;

  public TermsCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Override
  public String getName() {
    return "terms";
  }

  @Override
  public String help() {
    return "gets terms from the index, <field:term>, term can be a prefix";
  }

  @Override
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-f", "--field").required(true).help("field and term, e.g. field:term");
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    String field = args.getString("field");
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
    
    for (LeafReaderContext leaf : leaves){
      LeafReader atomicReader = leaf.reader();
      
      Terms terms = atomicReader.terms(field);
      
      if (terms == null) {
        continue;
      }
      
      if (termMap == null){
        termMap = new TreeMap<BytesRef,TermsEnum>();
      }
      
      
      TermsEnum te = terms.iterator();
      BytesRef termBytes;
      if (termVal != null){
        if (isExact){
          if (!te.seekExact(new BytesRef(termVal))){
            continue;
          }
        }
        else{
          te.seekCeil(new BytesRef(termVal));
        }
        termBytes = te.term();
      }
      else{
        termBytes = te.next();
      }
      
      while(true){
        if (termBytes == null) break;        
        AtomicInteger count = termCountMap.get(termBytes);
        if (count == null){
          termCountMap.put(termBytes, new AtomicInteger(te.docFreq()));
          termMap.put(termBytes, te);
          break;
        }
        count.getAndAdd(te.docFreq());
        if (isExact){
          termBytes = null; 
        }
        else{
          termBytes = te.next();
        }
      }
    }
    
    while(termMap != null && !termMap.isEmpty()){
      numCount++;
      Entry<BytesRef,TermsEnum> entry = termMap.pollFirstEntry();
      if (entry == null) break;
      BytesRef key = entry.getKey();
      AtomicInteger count = termCountMap.remove(key);
      out.println(bytesRefPrinter.print(key)+" ("+count+") ");
      if (ctx.isInteractiveMode() && numCount % numPerPage == 0){
          out.println("Press q to break");
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
    out.flush();
  }

}

