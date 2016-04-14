package com.senseidb.clue.api;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;

public class DefaultIndexReaderFactory implements IndexReaderFactory {
  
  private Directory idxDir = null;
  private IndexReader reader = null;
  
  
  public DefaultIndexReaderFactory() {
  }

  @Override
  public void initialize(Directory idxDir) throws Exception {   
    this.idxDir = idxDir;    
    refreshReader();
  }
  
  @Override
  public void refreshReader() throws Exception {
    if (reader != null) {
      reader.close();
    }
    reader = DirectoryReader.open(idxDir);
  }

  @Override
  public IndexReader getIndexReader() {
    return reader;
  }

  @Override
  public void shutdown() throws Exception {
    if (reader != null) {
      reader.close();
      reader = null;
    }
  }
}
