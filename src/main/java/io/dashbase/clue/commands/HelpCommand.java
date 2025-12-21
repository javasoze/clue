package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import picocli.CommandLine.Command;

import java.io.PrintStream;
import java.util.Collection;

@Readonly
@Command(name = HelpCommand.CMD_NAME, mixinStandardHelpOptions = true)
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
  protected void run(PrintStream out) {
    Collection<ClueCommand> commands = ctx.getCommandRegistry().getAvailableCommands();
    
    for (ClueCommand cmd : commands){
      out.println(cmd.getName()+" - " + cmd.help());
    }
    out.flush();
  }

}
