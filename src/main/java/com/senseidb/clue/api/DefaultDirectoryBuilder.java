package com.senseidb.clue.api;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.senseidb.clue.HdfsDirectory;

public class DefaultDirectoryBuilder implements DirectoryBuilder {

  private static final Set<String> SUPPORTED_SCHEMES = new HashSet<String>(Arrays.asList("file", "hdfs"));
  
  @Override
  public Directory build(URI idxUri) throws IOException {
    String scheme = idxUri.getScheme();
    if (scheme == null) {
      scheme = "file";
    }
    
    if (SUPPORTED_SCHEMES.contains(scheme)) {
      if ("file".equals(scheme)) {
        return FSDirectory.open(new File(idxUri.getPath()));
      }
      else if ("hdfs".equals(scheme)){        
        Path hdfsPath = new Path(idxUri.getPath());
        return new HdfsDirectory(hdfsPath, hdfsPath.getFileSystem(new Configuration()));
      }
      else {
        throw new IOException("unsupported protocol: " + scheme);
      }
    }
    else {
      throw new IOException("unsupported protocol: " + scheme);
    }
  }

}
