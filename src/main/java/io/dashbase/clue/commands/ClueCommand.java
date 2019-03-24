package io.dashbase.clue.commands;

import java.io.PrintStream;

import io.dashbase.clue.ClueContext;

public abstract class ClueCommand {

  protected ClueContext ctx;
  
  public ClueCommand(ClueContext ctx){
    this.ctx = ctx;
    this.ctx.registerCommand(this);
  }
  
  public ClueContext getContext(){
    return ctx;
  }
  
  public abstract String getName();
  public abstract String help();
  public abstract void execute(String[] args, PrintStream out) throws Exception; 
}
