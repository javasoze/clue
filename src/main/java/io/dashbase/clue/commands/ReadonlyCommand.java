package io.dashbase.clue.commands;

import java.io.PrintStream;

import io.dashbase.clue.ClueContext;

@Readonly
public class ReadonlyCommand extends ClueCommand {

  public ReadonlyCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "readonly";
  }

  @Override
  public String help() {
    return "puts clue in readonly mode";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    
    boolean readonly = true;
    if (args.length > 0) { 
      readonly = Boolean.parseBoolean(args[0]);
    }
    getContext().setReadOnlyMode(readonly);
    out.println("readonly mode is now: "+readonly);
  }

}
