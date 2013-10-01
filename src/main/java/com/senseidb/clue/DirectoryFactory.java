package com.senseidb.clue;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class DirectoryFactory {

  public DirectoryFactory() {
  }
  
  public Directory buildDirectory(String idxPath) throws IOException {
    if (idxPath.contains("://")) {
      int idx = idxPath.indexOf("://");
      String protocol = idxPath.substring(0, idx);      
      String path = idxPath.substring(idx+"://".length());
      
      if ("file".equals(protocol)) {
        return FSDirectory.open(new File(path));
      }
      else if ("hdfs".equals(protocol)){        
        return new HdfsDirectory(path, new DistributedFileSystem());
      }
      else {
        throw new IOException("unsupported protocol: "+protocol);
      }
    }
    else {
      return FSDirectory.open(new File(idxPath));
    }
  }

}
