package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;

import io.dashbase.clue.ClueContext;

@Readonly
public class HelpCommand extends ClueCommand {

  public static final String CMD_NAME = "help";
  public HelpCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return CMD_NAME;
  }

  @Override
  public String help() {
    return "displays help";
  }

  @Override
  public void execute(String[] args, PrintStream out) {
    Collection<ClueCommand> commands = ctx.getCommandRegistry().getAvailableCommands();
    
    for (ClueCommand cmd : commands){
      out.println(cmd.getName()+" - " + cmd.help());
    }
    out.flush();
  }

}
