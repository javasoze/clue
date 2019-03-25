package io.dashbase.clue.commands;

import java.io.PrintStream;

import io.dashbase.clue.ClueContext;
import net.sourceforge.argparse4j.inf.Namespace;

@Readonly
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
  public void execute(Namespace args, PrintStream out) throws Exception {
    out.println(getContext().getDirectory());
  }

}
