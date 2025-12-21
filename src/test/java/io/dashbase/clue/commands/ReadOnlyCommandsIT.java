package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReadOnlyCommandsIT {

  private Path baseIndexDir;

  @BeforeAll
  void buildIndex(@TempDir Path tempDir) throws Exception {
    baseIndexDir = CommandTestSupport.buildSampleIndex(tempDir);
  }

  @Test
  void helpListsReadonlyCommands() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "help");
      assertTrue(output.contains("search - executes a query against the index"));
      assertTrue(output.contains("help - displays help"));
      assertFalse(output.contains("delete - deletes a list of documents"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void unknownCommandFallsBackToHelp() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "notacommand");
      assertTrue(output.contains("notacommand is not supported"));
      assertTrue(output.contains("help - displays help"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void infoShowsSummaryAndSegmentDetails() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "info");
      assertTrue(output.contains("numdocs:"));
      assertTrue(output.contains("segment count:"));

      String segmentOutput = CommandTestSupport.runCommand(ctx, "info", "-s", "0");
      assertTrue(segmentOutput.contains("segment 0:"));
      assertTrue(segmentOutput.contains("doc base:"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void directoryPrintsIndexLocation() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "directory");
      assertTrue(output.contains(baseIndexDir.toString()));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void countDefaultsToMatchAll() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "count");
      int count = CommandTestSupport.extractIntAfter(output, "count:");
      assertEquals(ctx.getIndexReader().numDocs(), count);
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void searchShowsHits() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "search", "-q", "color_indexed:yellow", "-n", "2");
      int hits = CommandTestSupport.extractIntAfter(output, "numhits:");
      assertTrue(hits > 0);
      assertTrue(output.contains("parsed query:"));
      assertTrue(output.contains("doc:"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void termsSupportsExactAndPrefixLookups() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String exactOutput = CommandTestSupport.runCommand(ctx, "terms", "-f", "color_indexed:yellow");
      assertTrue(exactOutput.contains("yellow"));

      String prefixOutput = CommandTestSupport.runCommand(ctx, "terms", "-f", "color_indexed:ye*");
      assertTrue(prefixOutput.contains("yellow"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void termsWithoutTermListsTerms() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "terms", "-f", "color_indexed");
      assertTrue(output.contains("yellow"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void postingsShowsDocIds() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "postings", "-f", "color_indexed:yellow", "-n", "5");
      assertTrue(output.contains("docid:"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void postingsWithoutTermShowsUsage() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "postings", "-f", "color_indexed");
      assertTrue(output.contains("usage: field:term"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void docsetinfoShowsHistogram() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "docsetinfo", "-f", "color_indexed:yellow", "-s", "1000");
      assertTrue(output.contains("min:"));
      assertTrue(output.contains("histogram:"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void explainOutputsParsedQuery() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "explain", "-q", "color_indexed:yellow", "-d", "0");
      assertTrue(output.contains("parsed query:"));
      assertNotEquals("", output.trim());
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void normsShowsValuesForDocs() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "norm", "-f", "contents", "-d", "0");
      assertTrue(output.contains("norm:"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void normsRejectsUnknownField() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "norm", "-f", "nope", "-d", "0");
      assertTrue(output.contains("norm does not exist for field"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void termVectorUsesPayloadField() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "tv", "-f", "tags_payload", "-d", "0");
      assertTrue(output.contains("hybrid"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void termVectorReportsMissingVectors() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "tv", "-f", "contents", "-d", "0");
      assertTrue(output.contains("term vector is not available for field"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void storedFieldReturnsStoredValue() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "stored", "-f", "color_indexed", "-d", "0");
      assertTrue(output.contains("yellow"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void storedFieldRejectsNonStoredField() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "stored", "-f", "contents", "-d", "0");
      assertTrue(output.contains("0 not found"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void reconstructBuildsFieldText() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "reconstruct", "-f", "contents", "-d", "0");
      assertTrue(output.contains("("));
      assertTrue(output.contains("compact"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void docvalShowsNumericType() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "docval", "-f", "year", "-d", "0");
      assertTrue(output.contains("type: NUMERIC"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void docvalRejectsUnknownField() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "docval", "-f", "nope", "-d", "0");
      assertTrue(output.contains("docvalue does not exist for field"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void dumpdocShowsStoredFields() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "dumpdoc", "-d", "0");
      assertTrue(output.contains("color_indexed:"));
      assertTrue(output.contains("category_indexed:"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void exportWritesOutput(@TempDir Path tempDir) throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    Path outputDir = tempDir.resolve("export");
    Files.createDirectories(outputDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "export", "-o", outputDir.toString(), "-t", "true");
      assertTrue(output.contains("exporting index to text"));
      try (var files = Files.list(outputDir)) {
        assertTrue(files.findAny().isPresent());
      }
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void pointsWithoutPointFieldProducesNoOutput() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String output = CommandTestSupport.runCommand(ctx, "points", "-f", "price:100");
      assertEquals("", output.trim());
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }
}
