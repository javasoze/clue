package io.dashbase.clue.util;

import org.apache.lucene.index.PointValues;

import java.io.IOException;

public class DelegateIntersectVisitor implements PointValues.IntersectVisitor {
    private final PointValues.IntersectVisitor delegate;

    public DelegateIntersectVisitor(PointValues.IntersectVisitor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void visit(int docID) throws IOException {
        delegate.visit(docID);
    }

    @Override
    public void visit(int docID, byte[] packedValue) throws IOException {
        delegate.visit(docID, packedValue);
    }

    @Override
    public PointValues.Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
        return delegate.compare(minPackedValue, maxPackedValue);
    }
}