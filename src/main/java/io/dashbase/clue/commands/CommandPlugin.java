package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;

import java.util.Collection;

public interface CommandPlugin {
  String getName();

  Collection<ClueCommand> createCommands(ClueContext ctx);
}
