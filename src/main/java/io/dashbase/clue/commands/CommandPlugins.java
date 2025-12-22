package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class CommandPlugins {
  private static final Map<String, CommandPlugin> PLUGINS = loadPlugins();

  private CommandPlugins() {
  }

  public static Collection<CommandPlugin> allPlugins() {
    return PLUGINS.values();
  }

  public static void registerAll(ClueContext ctx) {
    for (CommandPlugin plugin : PLUGINS.values()) {
      Collection<ClueCommand> commands = plugin.createCommands(ctx);
      if (commands == null || commands.isEmpty()) {
        continue;
      }
      for (ClueCommand command : commands) {
        if (command == null) {
          continue;
        }
        ctx.registerCommand(command);
      }
    }
  }

  private static Map<String, CommandPlugin> loadPlugins() {
    Map<String, CommandPlugin> plugins = new LinkedHashMap<>();
    ServiceLoader<CommandPlugin> loader = ServiceLoader.load(CommandPlugin.class);
    for (CommandPlugin plugin : loader) {
      String name = plugin.getName();
      if (name == null || name.isBlank()) {
        throw new IllegalStateException("CommandPlugin has empty name: " + plugin.getClass().getName());
      }
      if (plugins.containsKey(name)) {
        throw new IllegalStateException("Duplicate CommandPlugin name: " + name);
      }
      plugins.put(name, plugin);
    }
    return Collections.unmodifiableMap(plugins);
  }
}
