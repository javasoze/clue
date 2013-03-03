package com.senseidb.clue.commands;

import java.io.PrintStream;

import com.senseidb.clue.ClueContext;

public class ExitCommand extends ClueCommand {

  public ExitCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "exit";
  }

  @Override
  public String help() {
    return "exits program";
  }

  @Override
  public void execute(String[] args, PrintStream out) {
    out.flush();
    try{
      ctx.shutdown();
    }
    catch(Exception e){
      e.printStackTrace();
    }
    System.exit(0);
  }

}
