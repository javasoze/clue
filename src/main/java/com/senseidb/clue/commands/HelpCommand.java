package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;

import com.senseidb.clue.ClueContext;

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
    Map<String, ClueCommand> cmdMap = ctx.getCommandMap();
    Collection<ClueCommand> commands = cmdMap.values();
    
    for (ClueCommand cmd : commands){
      out.println(cmd.getName()+" - " + cmd.help());
    }
    out.flush();
  }

}
