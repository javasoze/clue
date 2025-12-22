package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import picocli.CommandLine;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;

public abstract class ClueCommand implements Callable<Integer> {

  protected final ClueContext ctx;
  private PrintStream out = System.out;

  public ClueCommand(ClueContext ctx){
    this(ctx, false);
  }

  public ClueCommand(ClueContext ctx, boolean skipRegistration){
    this.ctx = ctx;
  }

  public final int execute(String[] args, PrintStream out) {
    ClueCommand command = newInstance();
    command.out = out;
    CommandLine cmd = new CommandLine(command);
    cmd.setCommandName(command.getName());
    if (command.help() != null && !command.help().isEmpty()) {
      cmd.getCommandSpec().usageMessage().description(command.help());
    }
    PrintWriter writer = new PrintWriter(out, true);
    cmd.setOut(writer);
    cmd.setErr(writer);
    cmd.setParameterExceptionHandler((ex, argsList) -> {
      PrintWriter err = ex.getCommandLine().getErr();
      err.println(ex.getMessage());
      ex.getCommandLine().usage(err);
      return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
    });
    cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
      ex.printStackTrace(out);
      return commandLine.getCommandSpec().exitCodeOnExecutionException();
    });
    return cmd.execute(args);
  }

  public ClueContext getContext(){
    return ctx;
  }

  protected PrintStream getOut() {
    return out;
  }

  protected ClueCommand newInstance() {
    try {
      Constructor<? extends ClueCommand> ctor;
      try {
        ctor = getClass().getConstructor(ctx.getClass());
      } catch (NoSuchMethodException e) {
        ctor = getClass().getConstructor(ClueContext.class);
      }
      return ctor.newInstance(ctx);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to create command instance for: " + getName(), e);
    }
  }

  @Override
  public Integer call() throws Exception {
    if (ctx.isReadOnlyMode() && !getClass().isAnnotationPresent(Readonly.class)) {
      out.println("read-only mode, command: " + getName() + " is not allowed");
      return 1;
    }
    run(out);
    return 0;
  }

  public abstract String getName();
  public abstract String help();
  protected abstract void run(PrintStream out) throws Exception;
}
