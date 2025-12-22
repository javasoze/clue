package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandPluginsTest {
  private Path baseIndexDir;

  @BeforeAll
  void buildIndex(@TempDir Path tempDir) throws Exception {
    baseIndexDir = CommandTestSupport.buildSampleIndex(tempDir);
  }

  @Test
  void pluginCommandsRegisterAndAppearInHelp() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String helpOutput = CommandTestSupport.runCommand(ctx, "help");
      assertTrue(helpOutput.contains("plugin-echo - plugin command echo"));

      String pluginOutput = CommandTestSupport.runCommand(ctx, "plugin-echo");
      assertTrue(pluginOutput.contains("plugin ready"));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }
}
