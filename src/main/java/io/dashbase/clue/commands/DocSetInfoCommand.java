package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
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
import java.util.Arrays;
import java.util.List;

@Readonly
@Command(name = "docsetinfo", mixinStandardHelpOptions = true)
public class DocSetInfoCommand extends ClueCommand {

  private static final int DEFAULT_BUCKET_SIZE = 1000;

  private final LuceneContext ctx;
  public DocSetInfoCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-f", "--field"}, required = true, description = "field/term pair, e.g. field:term")
  private String field;

  @Option(names = {"-s", "--size"}, defaultValue = "" + DEFAULT_BUCKET_SIZE, description = "bucket size")
  private int size;

  @Override
  public String getName() {
    return "docsetinfo";
  }

  @Override
  public String help() {
    return "doc id set info and stats";
  }
  
  private static double[] PERCENTILES = new double[] {
    50.0, 75.0, 90.0, 95.0, 99.0
  };

  @Override
  protected void run(PrintStream out) throws Exception {
    String field = this.field;
    String termVal = null;
    int bucketSize = size;

    if (field != null){
      String[] parts = field.split(":");
      if (parts.length > 1){
        field = parts[0];
        termVal = parts[1];
      }
    }
    
    IndexReader reader = ctx.getIndexReader();
    List<LeafReaderContext> leaves = reader.leaves();
    

    PostingsEnum postingsEnum = null;
    for (LeafReaderContext leaf : leaves) {
      LeafReader atomicReader = leaf.reader();
      Terms terms = atomicReader.terms(field);
      if (terms == null){
        continue;
      }
      if (terms != null && termVal != null){        
        TermsEnum te = terms.iterator();
        
        if (te.seekExact(new BytesRef(termVal))){
          postingsEnum = te.postings(postingsEnum, PostingsEnum.FREQS);
          
          int docFreq = te.docFreq();
          
          int minDocId = -1, maxDocId = -1;
          int doc, count = 0;
          
          int[] percentDocs = new int[PERCENTILES.length];
          
          int percentileIdx = 0;
          
          while ((doc = postingsEnum.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            maxDocId = doc;
            if (minDocId == -1) {
              minDocId = doc;
            }
            count ++;
            
            double perDocs = (double) count / (double) docFreq * 100.0;
            while (percentileIdx < percentDocs.length) {
              if (perDocs > PERCENTILES[percentileIdx]) {
                percentDocs[percentileIdx] = doc;
                percentileIdx++;
              } else {
                break;
              }
            }
          }
          
          // calculate histogram          
          int[] buckets = null;
          if (maxDocId > 0) {
            buckets = new int[maxDocId / bucketSize + 1];
            
            postingsEnum = te.postings(postingsEnum, PostingsEnum.FREQS);
            while ((doc = postingsEnum.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
              int bucketIdx = doc / bucketSize;
              buckets[bucketIdx]++;
            }
          }
          
          double density = (double) docFreq / (double) (maxDocId - minDocId) ; 
          out.println(String.format("min: %d, max: %d, count: %d, density: %.2f", minDocId, maxDocId, docFreq, density));
          out.println("percentiles: " + Arrays.toString(PERCENTILES) + " => " + Arrays.toString(percentDocs));
          out.println("histogram: (bucketsize=" + bucketSize+")");
          out.println(Arrays.toString(buckets));
        }
      }
    }
  }
}
