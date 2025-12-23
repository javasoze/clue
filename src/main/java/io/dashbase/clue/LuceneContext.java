package io.dashbase.clue;

import io.dashbase.clue.api.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LuceneContext extends ClueContext {
    private final IndexReaderFactory readerFactory;

    private IndexWriter writer;
    private final Directory directory;
    private final IndexWriterConfig writerConfig;
    private final QueryBuilder queryBuilder;
    private final AnalyzerFactory analyzerFactory;
    private final BytesRefDisplay termBytesRefDisplay;
    private final BytesRefDisplay payloadBytesRefDisplay;
    private final Executor executor;

    public LuceneContext(String dir, ClueAppConfiguration config, boolean interactiveMode)
            throws Exception {
        super(config.commandRegistrar, interactiveMode);
        this.directory = config.dirBuilder.build(dir);
        this.analyzerFactory = config.analyzerFactory;
        this.readerFactory = config.indexReaderFactory;
        this.readerFactory.initialize(directory);
        this.queryBuilder = config.queryBuilder;
        this.queryBuilder.initialize("contents", analyzerFactory.forQuery());
        this.writerConfig = new IndexWriterConfig(new StandardAnalyzer());
        this.termBytesRefDisplay = new StringBytesRefDisplay();
        this.payloadBytesRefDisplay = new StringBytesRefDisplay();
        this.writer = null;
        this.executor = config.enableConcurrency ?  Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1) : null;
    }


    public IndexReader getIndexReader(){
        return readerFactory.getIndexReader();
    }

    private static final int MAX_DOCS_PER_SLICE = 250_000;

    private static final int MAX_SEGMENTS_PER_SLICE = 5;

    public IndexSearcher getIndexSearcher() {
        return new IndexSearcher(getIndexReader(), executor) {
            @Override
            protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, MAX_DOCS_PER_SLICE, MAX_SEGMENTS_PER_SLICE, false);
            }
        };
    }

    public IndexWriter getIndexWriter(){
        if (registry.isReadonly()) return null;
        if (writer == null) {
            try {
                writer = new IndexWriter(directory, writerConfig);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return writer;
    }

    Collection<String> fieldNames() {
        LinkedList<String> fieldNames = new LinkedList<>();
        for (LeafReaderContext context : getIndexReader().leaves()) {
            LeafReader reader = context.reader();
            for(FieldInfo info : reader.getFieldInfos()) {
                fieldNames.add(info.name);
            }
        }
        return fieldNames;
    }

    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    public Analyzer getAnalyzerQuery() {
        return analyzerFactory.forQuery();
    }

    public BytesRefDisplay getTermBytesRefDisplay() {
        return termBytesRefDisplay;
    }

    public BytesRefDisplay getPayloadBytesRefDisplay() {
        return payloadBytesRefDisplay;
    }

    public Directory getDirectory() {
        return directory;
    }

    public void setReadOnlyMode(boolean readOnlyMode) {
        this.registry.setReadonly(readOnlyMode);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            writer = null;
        }
    }

    public void refreshReader() throws Exception {
        readerFactory.refreshReader();
    }

    public boolean isCurrentSearch() {
      return this.executor != null;
    }

    @Override
    public void shutdown() throws Exception{
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        } finally {
            try {
                readerFactory.shutdown();
            } finally {
                directory.close();
            }
        }
    }
}
