package io.dashbase.clue.api;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class DirectoryProviders {
  public static final String DEFAULT_PROVIDER_NAME = "fs";
  private static final Map<String, DirectoryProvider> PROVIDERS = loadProviders();

  private DirectoryProviders() {
  }

  public static DirectoryProvider getProvider(String name) {
    String providerName = name == null || name.isBlank() ? DEFAULT_PROVIDER_NAME : name;
    DirectoryProvider provider = PROVIDERS.get(providerName);
    if (provider == null) {
      throw new IllegalArgumentException("Unknown directory provider: " + providerName
          + ". Available providers: " + PROVIDERS.keySet());
    }
    return provider;
  }

  public static Map<String, DirectoryProvider> allProviders() {
    return PROVIDERS;
  }

  private static Map<String, DirectoryProvider> loadProviders() {
    Map<String, DirectoryProvider> providers = new LinkedHashMap<>();
    ServiceLoader<DirectoryProvider> loader = ServiceLoader.load(DirectoryProvider.class);
    Iterator<DirectoryProvider> iterator = loader.iterator();
    while (iterator.hasNext()) {
      DirectoryProvider provider;
      try {
        provider = iterator.next();
      } catch (ServiceConfigurationError e) {
        System.err.println("Failed to load directory provider: " + e.getMessage());
        continue;
      }
      String name = provider.getName();
      if (name == null || name.isBlank()) {
        System.err.println("Skipping DirectoryProvider with empty name: " + provider.getClass().getName());
        continue;
      }
      if (providers.containsKey(name)) {
        System.err.println("Skipping duplicate DirectoryProvider name: " + name);
        continue;
      }
      providers.put(name, provider);
    }
    if (!providers.containsKey(DEFAULT_PROVIDER_NAME)) {
      DirectoryProvider fallback = new FsDirectoryProvider();
      providers.put(fallback.getName(), fallback);
    }
    return Collections.unmodifiableMap(providers);
  }
}
