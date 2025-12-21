package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoCommandTest {

  @Test
  void infoHandlesInvalidUtf8Terms(@TempDir Path tempDir) throws Exception {
    Path indexDir = tempDir.resolve("index");
    Files.createDirectories(indexDir);
    createIndexWithInvalidTerm(indexDir);

    LuceneContext ctx = CommandTestSupport.newContext(indexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "info");
      assertTrue(output.contains("number of fields:"));
      assertFalse(output.contains("ArrayIndexOutOfBoundsException"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  private static void createIndexWithInvalidTerm(Path indexDir) throws Exception {
    byte[] bytes = new byte[16];
    Arrays.fill(bytes, 0, bytes.length - 1, (byte) 'a');
    bytes[bytes.length - 1] = (byte) 0xC3; // truncated UTF-8 sequence triggers fallback display

    FieldType type = new FieldType();
    type.setIndexOptions(IndexOptions.DOCS);
    type.setTokenized(false);
    type.setStored(true);
    type.freeze();

    try (FSDirectory directory = FSDirectory.open(indexDir);
         IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document doc = new Document();
      doc.add(new Field("binary_term", new BytesRef(bytes), type));
      writer.addDocument(doc);
      writer.commit();
    }
  }
}
