package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;

import java.util.Collection;
import java.util.Collections;

public class TestCommandPlugin implements CommandPlugin {
  @Override
  public String getName() {
    return "test-plugin";
  }

  @Override
  public Collection<ClueCommand> createCommands(ClueContext ctx) {
    return Collections.singletonList(new TestPluginCommand(ctx));
  }
}
