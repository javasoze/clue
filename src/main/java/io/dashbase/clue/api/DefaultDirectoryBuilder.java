package io.dashbase.clue.api;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;

public class DefaultDirectoryBuilder implements DirectoryBuilder {

  public String dir = null;

  @Override
  public Directory build(String location) throws IOException {
    String idxDir = dir == null ? location : dir;
    if (idxDir == null) {
      throw new IllegalArgumentException("null directory specified");
    }
    File directory = new File(idxDir);
    return FSDirectory.open(FileSystems.getDefault().getPath(directory.getPath()));
  }
}
