package com.senseidb.clue.api;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;

public interface IndexReaderFactory {
  void initialize(Directory dir) throws Exception;
  IndexReader getIndexReader();
  void refreshReader() throws Exception;
  void shutdown() throws Exception;
}
