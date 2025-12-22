package io.dashbase.clue.api;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryProvidersTest {

  @Test
  void serviceLoaderFindsDefaultProvider() {
    DirectoryProvider provider = DirectoryProviders.getProvider(DirectoryProviders.DEFAULT_PROVIDER_NAME);
    assertNotNull(provider);
  }

  @Test
  void defaultBuilderUsesProvider(@TempDir Path tempDir) throws Exception {
    Path indexDir = tempDir.resolve("index");
    Files.createDirectories(indexDir);

    DefaultDirectoryBuilder builder = new DefaultDirectoryBuilder();
    Directory directory = builder.build(indexDir.toString());
    try {
      assertTrue(directory instanceof FSDirectory);
    } finally {
      directory.close();
    }
  }
}
