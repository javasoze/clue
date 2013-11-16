package com.senseidb.clue.api;

import java.io.IOException;
import java.net.URI;

import org.apache.lucene.store.Directory;

public interface DirectoryBuilder {
  Directory build(URI idxUri) throws IOException;
}
