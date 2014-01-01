package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

import com.senseidb.clue.ClueContext;

public class DocSetInfoCommand extends ClueCommand {

  private static final int DEFAULT_BUCKET_SIZE = 1000;
  public DocSetInfoCommand(ClueContext ctx) {
    super(ctx);
  }

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
  public void execute(String[] args, PrintStream out) throws Exception {
    String field = null;
    String termVal = null;
    int bucketSize = DEFAULT_BUCKET_SIZE;
    
    try{
      field = args[0];
    }
    catch(Exception e){
      field = null;
    }
    
    try {
      bucketSize = Integer.parseInt(args[1]);
    }
    catch(Exception e){
    }
    
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
    
    IndexReader reader = ctx.getIndexReader();
    List<AtomicReaderContext> leaves = reader.leaves();
    

    for (AtomicReaderContext leaf : leaves) {
      AtomicReader atomicReader = leaf.reader();
      Terms terms = atomicReader.terms(field);
      if (terms == null){
        continue;
      }
      if (terms != null && termVal != null){
        TermsEnum te = terms.iterator(null);
        
        if (te.seekExact(new BytesRef(termVal))){
          DocsEnum iter = te.docs(atomicReader.getLiveDocs(), null);
          
          int docFreq = te.docFreq();
          
          int minDocId = -1, maxDocId = -1;
          int doc, count = 0;
          
          int[] percentDocs = new int[PERCENTILES.length];
          
          int percentileIdx = 0;
          while ((doc = iter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
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
            
            iter = te.docs(atomicReader.getLiveDocs(), null);
            while ((doc = iter.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
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
