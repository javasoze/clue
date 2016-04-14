package com.senseidb.clue.util;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RandomAccessWeight;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.Bits;

public abstract class MatchSomeDocsQuery extends Query {
  
  protected abstract boolean match(int docId);
  
  @Override
  public Weight createWeight(IndexSearcher searcher, boolean needsScores) {
    return new RandomAccessWeight(this) {
      @Override
      protected Bits getMatchingDocs(LeafReaderContext context) throws IOException {
        final int maxDoc = context.reader().maxDoc();
        return new Bits() {

          @Override
          public boolean get(int index) {
            return match(index);
          }

          @Override
          public int length() {
            return maxDoc;
          }          
        };
      }   
    };
  }
}