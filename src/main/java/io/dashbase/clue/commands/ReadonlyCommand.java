package io.dashbase.clue.commands;

import java.io.PrintStream;

import io.dashbase.clue.ClueContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

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
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("readonly").type(Boolean.class).nargs(1).help("readonly true/false");
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    boolean readonly = args.getBoolean("readonly");
    getContext().setReadOnlyMode(readonly);
    out.println("readonly mode is now: "+readonly);
  }

}
