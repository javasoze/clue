package io.dashbase.clue.util;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.*;

import java.io.IOException;

public final class MatchSomeDocsQuery extends Query {

  private final DocIdSetIterator docIdSetIterator;

  public MatchSomeDocsQuery(DocIdSetIterator docIdSetIterator) {
    this.docIdSetIterator = docIdSetIterator;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public String toString(String s) {
    return "matchsome: " + docIdSetIterator;
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
    return new ConstantScoreWeight(this, 1.0f) {
      @Override
      public ScorerSupplier scorerSupplier(LeafReaderContext leafReaderContext) throws IOException {
        Scorer scorer = new ConstantScoreScorer(1.0f, scoreMode, docIdSetIterator);

        return new ScorerSupplier() {
          @Override
          public Scorer get(long l) throws IOException {
            return scorer;
          }
          @Override
          public long cost() {
            return scorer.iterator().cost();
          }
        };
      }

      @Override
      public boolean isCacheable(LeafReaderContext leafReaderContext) {
        return false;
      }
    };
  }
  @Override
  public void visit(QueryVisitor visitor) {
    visitor.visitLeaf(this);
  }
}
