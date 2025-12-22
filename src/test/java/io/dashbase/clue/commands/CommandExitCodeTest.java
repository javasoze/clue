package io.dashbase.clue.commands;

import io.dashbase.clue.ClueApplication;
import io.dashbase.clue.LuceneContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandExitCodeTest {
  private Path baseIndexDir;

  @BeforeAll
  void buildIndex(@TempDir Path tempDir) throws Exception {
    baseIndexDir = CommandTestSupport.buildSampleIndex(tempDir);
  }

  @Test
  void unknownCommandReturnsNonZero() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      int code;
      try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
        code = ClueApplication.handleCommand(ctx, "notacommand", new String[]{}, out);
      }
      String output = buffer.toString(StandardCharsets.UTF_8);
      assertEquals(1, code);
      assertTrue(output.contains("notacommand is not supported"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  @Test
  void readonlyCommandReturnsNonZero() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      int code;
      try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
        code = ClueApplication.handleCommand(
            ctx,
            "delete",
            new String[]{"-q", "color_indexed:yellow"},
            out);
      }
      String output = buffer.toString(StandardCharsets.UTF_8);
      assertEquals(1, code);
      assertTrue(output.contains("read-only mode"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }
}
