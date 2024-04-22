package io.dashbase.clue.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.lucene.analysis.Analyzer;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = DefaultAnalyzerFactory.class)
@JsonSubTypes({@JsonSubTypes.Type(name = "default", value = DefaultAnalyzerFactory.class)})
public interface AnalyzerFactory {
    Analyzer forQuery() throws Exception;

    Analyzer forIndexing() throws Exception;
}
