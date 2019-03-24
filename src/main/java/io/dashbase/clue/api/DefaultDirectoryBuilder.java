package io.dashbase.clue.api;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DefaultDirectoryBuilder implements DirectoryBuilder {

  private static final Set<String> SUPPORTED_SCHEMES = new HashSet<String>(Arrays.asList("file"));

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
      else {
        throw new IOException("unsupported protocol: " + scheme);
      }
    }
    else {
      throw new IOException("unsupported protocol: " + scheme);
    }
  }

}
