package io.dashbase.clue.util;

import java.io.IOException;
import org.apache.lucene.search.DocIdSetIterator;

public class MatcherDocIdSetIterator extends DocIdSetIterator {

    private final DocIdMatcher matcher;
    private final int maxDoc;
    private int doc = -1;

    public MatcherDocIdSetIterator(DocIdMatcher matcher, int maxDoc) {
        this.matcher = matcher;
        this.maxDoc = maxDoc;
    }

    @Override
    public int docID() {
        return doc;
    }

    @Override
    public int nextDoc() throws IOException {
        doc++;

        while (doc++ < maxDoc) {
            if (matcher.match(doc)) {
                return doc;
            }
        }
        if (doc >= maxDoc) {
            doc = DocIdSetIterator.NO_MORE_DOCS;
        }
        return doc;
    }

    @Override
    public int advance(int i) throws IOException {
        doc = i;
        return nextDoc();
    }

    @Override
    public long cost() {
        return maxDoc;
    }
}
