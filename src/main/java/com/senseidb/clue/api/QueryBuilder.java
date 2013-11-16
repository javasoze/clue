package com.senseidb.clue.api;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;

public interface QueryBuilder {  
  void initialize(String defaultField, Analyzer analyzer) throws Exception;
  Query build(String rawQuery) throws Exception;
}
