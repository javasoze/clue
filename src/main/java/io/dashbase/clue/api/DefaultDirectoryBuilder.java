package io.dashbase.clue.api;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.store.Directory;

public class DefaultDirectoryBuilder implements DirectoryBuilder {

  public String dir = null;
  public String provider = DirectoryProviders.DEFAULT_PROVIDER_NAME;
  public Map<String, Object> options;

  @Override
  public Directory build(String location) throws IOException {
    String idxDir = dir == null ? location : dir;
    if (idxDir == null) {
      throw new IllegalArgumentException("null directory specified");
    }
    String overrideProvider = System.getProperty("clue.dir.provider");
    String providerName = provider;
    if (providerName == null || providerName.isBlank()) {
      providerName = DirectoryProviders.DEFAULT_PROVIDER_NAME;
    }
    if (overrideProvider != null && !overrideProvider.isBlank()) {
      providerName = overrideProvider;
    }
    DirectoryProvider directoryProvider = DirectoryProviders.getProvider(providerName);
    ParsedOptions providerOptions = ParsedOptions.parse(options);
    return directoryProvider.build(idxDir, providerOptions);
  }
}
