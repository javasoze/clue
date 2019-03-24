package io.dashbase.clue.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = DefaultQueryBuilder.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "default", value = DefaultQueryBuilder.class)
})
public interface QueryBuilder {  
  void initialize(String defaultField, Analyzer analyzer) throws Exception;
  Query build(String rawQuery) throws Exception;
}
