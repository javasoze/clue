package com.senseidb.clue.api;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NoLockFactory;

import com.senseidb.clue.HdfsDirectory;

public class DefaultDirectoryBuilder implements DirectoryBuilder {

  private static final Set<String> SUPPORTED_SCHEMES = new HashSet<String>(Arrays.asList("file", "hdfs"));
  private static final String HADOOP_CONFIFG_DIR = "hadoop.conf.dir";
  @Override
  public Directory build(URI idxUri) throws IOException {
    String scheme = idxUri.getScheme();
    if (scheme == null) {
      scheme = "file";
    }
    
    if (SUPPORTED_SCHEMES.contains(scheme)) {
      if ("file".equals(scheme)) {
        return FSDirectory.open(FileSystems.getDefault().getPath(idxUri.getPath()));
      }
      else if ("hdfs".equals(scheme)){
        String hadoopConfDir = null;
        String filePath = idxUri.getPath();
        int delimIdx = filePath.indexOf("@");
        if (delimIdx >= 0) {
          hadoopConfDir = filePath.substring(delimIdx + 1);
          filePath = filePath.substring(0, delimIdx);
        }
        if (hadoopConfDir == null) {
          hadoopConfDir = System.getProperty(HADOOP_CONFIFG_DIR);
        }
        Configuration config = new Configuration();
        if (hadoopConfDir != null && hadoopConfDir.trim().length() > 0) {
          System.out.println("Hadoop configuration found at: " + hadoopConfDir);
          Path hadoopConfPath = new Path(hadoopConfDir);
          config.addResource(new Path(hadoopConfPath,"core-site.xml"));
          config.addResource(new Path(hadoopConfPath,"hdfs-site.xml"));
          config.addResource(new Path(hadoopConfPath,"mapred-site.xml"));
        }
        Path hdfsPath = new Path(filePath);        
        return new HdfsDirectory(NoLockFactory.INSTANCE, hdfsPath, config);
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
