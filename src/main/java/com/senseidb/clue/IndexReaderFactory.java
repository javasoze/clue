package com.senseidb.clue;

import java.io.IOException;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;

public class IndexReaderFactory {

  private final Directory dir;
  private IndexReader reader;
  
  public IndexReaderFactory(Directory dir) throws IOException{
    this.dir = dir;
    reader = null;
    refreshReader();
  }
  
  public void refreshReader() throws IOException{
    if (reader != null){
      IndexReader tmp = reader;
      reader = DirectoryReader.open(dir);
      tmp.close();
    }
    else{
      reader = DirectoryReader.open(dir);
    }
  }
  
  public IndexReader getIndexReader(){
    return reader;
  }

  public void shutdown() throws IOException{
    if (reader != null){
      reader.close();
    }
  }
}
