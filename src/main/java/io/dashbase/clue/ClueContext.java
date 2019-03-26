package io.dashbase.clue;

import io.dashbase.clue.commands.ClueCommand;
import io.dashbase.clue.commands.CommandRegistrar;
import io.dashbase.clue.commands.CommandRegistry;

import java.util.Optional;

public class ClueContext {
  protected final CommandRegistry registry = new CommandRegistry();

  private final boolean interactiveMode;

  public ClueContext(CommandRegistrar commandRegistrar, boolean interactiveMode) {
    commandRegistrar.registerCommands(this);
    this.interactiveMode = interactiveMode;
  }

  public void registerCommand(ClueCommand cmd){
    String cmdName = cmd.getName();
    if (registry.exists(cmdName)){
      throw new IllegalArgumentException(cmdName+" exists!");
    }
    registry.registerCommand(cmd);
  }
  
  public boolean isReadOnlyMode() {
    return registry.isReadonly();
  }

  public boolean isInteractiveMode(){
    return interactiveMode;
  }
  
  public Optional<ClueCommand> getCommand(String cmd){
    return registry.getCommand(cmd);
  }
  
  public CommandRegistry getCommandRegistry(){
    return registry;
  }

  public void shutdown() throws Exception{
  }
}
