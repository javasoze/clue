package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import picocli.CommandLine.Command;

import java.io.PrintStream;

@Readonly
@Command(name = "plugin-echo", mixinStandardHelpOptions = true)
public class TestPluginCommand extends ClueCommand {
  public TestPluginCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "plugin-echo";
  }

  @Override
  public String help() {
    return "plugin command echo";
  }

  @Override
  protected void run(PrintStream out) {
    out.println("plugin ready");
  }
}
