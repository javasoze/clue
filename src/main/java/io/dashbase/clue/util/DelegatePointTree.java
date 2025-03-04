package io.dashbase.clue.util;

import org.apache.lucene.index.PointValues;

import java.io.IOException;

public class DelegatePointTree implements PointValues.PointTree {

    private final PointValues.PointTree delegate;

    public DelegatePointTree(PointValues.PointTree delegate) {
        this.delegate = delegate;
    }

    @Override
    public PointValues.PointTree clone() {
        return new DelegatePointTree(delegate.clone());
    }

    @Override
    public boolean moveToChild()
            throws IOException {
        return delegate.moveToChild();
    }

    @Override
    public boolean moveToSibling()
            throws IOException {
        return delegate.moveToSibling();
    }

    @Override
    public boolean moveToParent()
            throws IOException {
        return delegate.moveToParent();
    }

    @Override
    public byte[] getMinPackedValue() {
        return delegate.getMinPackedValue();
    }

    @Override
    public byte[] getMaxPackedValue() {
        return delegate.getMaxPackedValue();
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public void visitDocIDs(final PointValues.IntersectVisitor intersectVisitor)
            throws IOException {
        delegate.visitDocIDs(intersectVisitor);
    }

    @Override
    public void visitDocValues(final PointValues.IntersectVisitor intersectVisitor)
            throws IOException {
        delegate.visitDocValues(intersectVisitor);
    }
}
