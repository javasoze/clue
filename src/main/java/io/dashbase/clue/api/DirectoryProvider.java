package io.dashbase.clue.api;

import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.Map;

public interface DirectoryProvider {
  String getName();

  Directory build(String location, Map<String, String> options) throws IOException;
}
