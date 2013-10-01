package com.senseidb.clue;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.solr.store.hdfs.HdfsDirectory;

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
        return new HdfsDirectory(new Path(idxPath), new Configuration());
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
