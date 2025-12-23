package io.dashbase.clue.api;

import java.io.IOException;

import org.apache.lucene.store.Directory;

public interface DirectoryProvider {
  String getName();

  Directory build(String location, ParsedOptions options) throws IOException;
}
