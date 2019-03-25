package io.dashbase.clue.commands;

import java.io.PrintStream;

import io.dashbase.clue.ClueContext;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public abstract class ClueCommand {

  protected ClueContext ctx;
  private final ArgumentParser parser;

  public ClueCommand(ClueContext ctx){
    this(ctx, false);
  }

  public ClueCommand(ClueContext ctx, boolean skipRegistration){
    this.ctx = ctx;
    if (!skipRegistration) {
      this.ctx.registerCommand(this);
    }
    this.parser = buildParser(ArgumentParsers.newFor(getName())
            .build().defaultHelp(true).description(help()));
  }

  public final Namespace parseArgs(String[] args) throws ArgumentParserException {
     if (parser == null) {
       return null;
     }
     return parser.parseArgs(args);
  }
  
  public ClueContext getContext(){
    return ctx;
  }

  protected ArgumentParser buildParser(ArgumentParser parser) {
    return null;
  }
  
  public abstract String getName();
  public abstract String help();
  public abstract void execute(Namespace args, PrintStream out) throws Exception;
}
