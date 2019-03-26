package io.dashbase.clue;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import io.dashbase.clue.client.CmdlineHelper;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;

import io.dashbase.clue.commands.ClueCommand;
import io.dashbase.clue.commands.HelpCommand;

public class ClueApplication {
  
  private final LuceneContext ctx;

  private static ClueAppConfiguration config;

  private CmdlineHelper cmdlineHelper;
  
  static {
    try {
      config = ClueAppConfiguration.load();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public ClueContext getContext() {
    return ctx;
  }
  
  public ClueAppConfiguration getConfiguration() {
    return config;
  }
  
  public LuceneContext newContext(String dir, ClueAppConfiguration config, boolean interactiveMode) throws Exception {
    return new LuceneContext(dir, config, interactiveMode);
  }
  
  public ClueApplication(String idxLocation, boolean interactiveMode) throws Exception{
    ctx = newContext(idxLocation, config, interactiveMode);

    if (!DirectoryReader.indexExists(ctx.getDirectory())){
      System.out.println("lucene index does not exist at: "+idxLocation);
      System.exit(1);
    }
  }
  
  public static void handleCommand(ClueContext ctx, String cmdName, String[] args, PrintStream out){
    Optional<ClueCommand> helpCommand = ctx.getCommand(HelpCommand.CMD_NAME);
    Optional<ClueCommand> cmd = ctx.getCommand(cmdName);
    if (!cmd.isPresent()){
      out.println(cmdName+" is not supported:");
      cmd = helpCommand;
    }

    ClueCommand clueCommand = cmd.get();
    Namespace ns = null;

    try {
      ns = clueCommand.parseArgs(args);
    } catch(HelpScreenException he) {
        out.println(he.getMessage());
        return;
    } catch (ArgumentParserException ape) {
        out.println(ape.getMessage());
        PrintWriter writer = new PrintWriter(out);
        ape.getParser().printHelp(writer);
        writer.flush();
        return;
    }

    try{
      cmd.get().execute(ns, out);
    }
    catch(Exception e){
      e.printStackTrace(out);
    }
  }

  public void run() throws IOException {
    CmdlineHelper helper = new CmdlineHelper(new Supplier<Collection<String>>() {
      @Override
      public Collection<String> get() {
        return ctx.getCommandRegistry().commandNames();
      }
    }, new Supplier<Collection<String>>() {
      @Override
      public Collection<String> get() {
        return ctx.fieldNames();
      }
    });

    while(true){
      String line = helper.readCommand();
      if (line == null || line.isEmpty()) continue;
      line = line.trim();
      if ("exit".equals(line)) {
        return;
      }
      String[] parts = line.split("\\s");
      if (parts.length > 0){
        String cmd = parts[0];
        String[] cmdArgs = new String[parts.length - 1];
        System.arraycopy(parts, 1, cmdArgs, 0, cmdArgs.length);
        handleCommand(ctx, cmd, cmdArgs, System.out);
      }
    }
  }
  
  public void shutdown() throws Exception {
    ctx.shutdown();
  }
  
  
  public static void main(String[] args) throws Exception {
    if (args.length < 1){
      System.out.println("usage: <index location> <command> <command args>");
      System.exit(1);
    }
        
    String idxLocation = args[0];
    
    final ClueApplication app;
    
    if (args.length > 1){
      String cmd = args[1];
      if ("readonly".equalsIgnoreCase(cmd)) {
        if (args.length > 2) {
          cmd = args[2];
          app = new ClueApplication(idxLocation, false);
          String[] cmdArgs;
          cmdArgs = new String[args.length - 3];
          System.arraycopy(args, 3, cmdArgs, 0, cmdArgs.length);
          app.ctx.setReadOnlyMode(true);
          app.handleCommand(app.ctx, cmd, cmdArgs, System.out);
          app.shutdown();
        }
      }
      else {
        app = new ClueApplication(idxLocation, false);
        String[] cmdArgs;
        cmdArgs = new String[args.length - 2];
        System.arraycopy(args, 2, cmdArgs, 0, cmdArgs.length);
        app.handleCommand(app.ctx, cmd, cmdArgs, System.out);
        app.shutdown();
      }
      return;
    }
    app = new ClueApplication(idxLocation, true);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        try {
          app.shutdown();
        } catch (Exception e) {
          e.printStackTrace();
        } 
      }
    });
    app.run();
  }
}
