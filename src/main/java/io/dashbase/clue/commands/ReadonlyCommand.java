package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;

@Readonly
@Command(name = "readonly", mixinStandardHelpOptions = true)
public class ReadonlyCommand extends ClueCommand {

  private final LuceneContext ctx;

  public ReadonlyCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Parameters(paramLabel = "readonly", description = "readonly true/false")
  private boolean readonly;

  @Override
  public String getName() {
    return "readonly";
  }

  @Override
  public String help() {
    return "puts clue in readonly mode";
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    ctx.setReadOnlyMode(readonly);
    out.println("readonly mode is now: "+readonly);
  }
}
