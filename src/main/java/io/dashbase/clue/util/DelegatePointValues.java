package io.dashbase.clue.util;

import org.apache.lucene.index.PointValues;

import java.io.IOException;
import java.util.function.Function;

public class DelegatePointValues extends PointValues {
    private final PointValues delegate;

    public DelegatePointValues(PointValues delegate) {
        this.delegate = delegate;
    }

    @Override
    public PointTree getPointTree()
            throws IOException {
        return new DelegatePointTree(delegate.getPointTree());
    }

    @Override
    public byte[] getMinPackedValue() throws IOException {
        return delegate.getMinPackedValue();
    }

    @Override
    public byte[] getMaxPackedValue() throws IOException {
        return delegate.getMaxPackedValue();
    }

    @Override
    public int getNumDimensions()
            throws IOException {
        return delegate.getNumDimensions();
    }

    @Override
    public int getNumIndexDimensions()
            throws IOException {
        return delegate.getNumIndexDimensions();
    }

    @Override
    public int getBytesPerDimension()
            throws IOException {
        return delegate.getBytesPerDimension();
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public int getDocCount() {
        return delegate.getDocCount();
    }
}