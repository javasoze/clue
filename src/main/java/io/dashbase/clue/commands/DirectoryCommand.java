package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;

import java.io.PrintStream;

@Readonly
@Command(name = "directory", mixinStandardHelpOptions = true)
public class DirectoryCommand extends ClueCommand {

  private final LuceneContext ctx;
  public DirectoryCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
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
  protected void run(PrintStream out) throws Exception {
    out.println(ctx.getDirectory());
  }

}
