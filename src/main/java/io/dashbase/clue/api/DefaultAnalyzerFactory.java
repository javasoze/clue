package io.dashbase.clue.api;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

public class DefaultAnalyzerFactory implements AnalyzerFactory {
    @Override
    public Analyzer forQuery() {
        return new StandardAnalyzer();
    }

    @Override
    public Analyzer forIndexing() {
        return new StandardAnalyzer();
    }
}
