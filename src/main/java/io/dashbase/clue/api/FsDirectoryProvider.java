package io.dashbase.clue.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class FsDirectoryProvider implements DirectoryProvider {
  @Override
  public String getName() {
    return DirectoryProviders.DEFAULT_PROVIDER_NAME;
  }

  @Override
  public Directory build(String location, ParsedOptions options) throws IOException {
    File directory = new File(location);
    return FSDirectory.open(FileSystems.getDefault().getPath(directory.getPath()));
  }
}
