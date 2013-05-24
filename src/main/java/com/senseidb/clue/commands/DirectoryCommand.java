package com.senseidb.clue.commands;

import java.io.PrintStream;

import com.senseidb.clue.ClueContext;

public class DirectoryCommand extends ClueCommand {

  public DirectoryCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "directory";
  }

  @Override
  public String help() {
    return "prints directory information";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    out.println(getContext().getDirectory());
  }

}
