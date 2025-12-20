package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MutatingCommandsIT {

  private Path baseIndexDir;
  private Path workingIndexDir;

  @BeforeAll
  void buildIndex(@TempDir Path tempDir) throws Exception {
    baseIndexDir = CommandTestSupport.buildSampleIndex(tempDir);
  }

  @BeforeEach
  void copyIndex(@TempDir Path tempDir) throws Exception {
    workingIndexDir = tempDir.resolve("index");
    CommandTestSupport.copyDirectory(baseIndexDir, workingIndexDir);
  }

  @Test
  void readonlyCommandTogglesMode() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "readonly", "false");
      assertTrue(output.contains("readonly mode is now: false"));
      assertFalse(ctx.isReadOnlyMode());

      output = CommandTestSupport.runCommand(ctx, "readonly", "true");
      assertTrue(output.contains("readonly mode is now: true"));
      assertTrue(ctx.isReadOnlyMode());
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void deleteCommandRemovesDocuments() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      ctx.setReadOnlyMode(false);
      int before = ctx.getIndexReader().numDocs();
      String output = CommandTestSupport.runCommand(ctx, "delete", "-q", "color_indexed:yellow");
      int after = ctx.getIndexReader().numDocs();
      assertTrue(output.contains("parsed query:"));
      assertTrue(after < before);
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void mergeCommandReducesSegments() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      ctx.setReadOnlyMode(false);
      CommandTestSupport.runCommand(ctx, "merge", "-n", "1");
      assertTrue(ctx.getIndexReader().leaves().size() <= 1);
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void trimCommandRemovesDocuments() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      ctx.setReadOnlyMode(false);
      int before = ctx.getIndexReader().numDocs();
      String output = CommandTestSupport.runCommand(ctx, "trim", "-p", "10");
      int after = ctx.getIndexReader().numDocs();
      assertTrue(output.contains("trim successful"));
      assertTrue(after < before);
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void trimRejectsInvalidPercent() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      ctx.setReadOnlyMode(false);
      String output = CommandTestSupport.runCommand(ctx, "trim", "-p", "101");
      assertTrue(output.contains("invalid percent"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void saveAndDeleteCommitData() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      ctx.setReadOnlyMode(false);
      String output = CommandTestSupport.runCommand(ctx, "savecommitdata", "-k", "foo", "-v", "bar");
      assertTrue(output.contains("saved"));

      output = CommandTestSupport.runCommand(ctx, "showcommitdata");
      assertTrue(output.contains("key: foo"));
      assertTrue(output.contains("value: bar"));

      output = CommandTestSupport.runCommand(ctx, "deletecommitdata", "-k", "foo");
      assertTrue(output.contains("removed"));

      output = CommandTestSupport.runCommand(ctx, "showcommitdata");
      assertFalse(output.contains("foo"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void deleteCommandBlockedInReadOnlyMode() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      int before = ctx.getIndexReader().numDocs();
      String output = CommandTestSupport.runCommand(ctx, "delete", "-q", "color_indexed:yellow");
      int after = ctx.getIndexReader().numDocs();
      assertTrue(output.contains("read-only mode"));
      assertEquals(before, after);
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void pointsCommandWorksWithPointField() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(workingIndexDir);
    try {
      ctx.setReadOnlyMode(false);
      IndexWriter writer = ctx.getIndexWriter();
      Document doc = new Document();
      doc.add(new LongPoint("price_point", 123));
      writer.addDocument(doc);
      writer.commit();
      ctx.refreshReader();

      String output = CommandTestSupport.runCommand(ctx, "points", "-f", "price_point:123");
      assertTrue(output.contains("123 (1)"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }
}
