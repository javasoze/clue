package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceConfigurationError;
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
      String pluginName = pluginName(plugin);
      try {
        Collection<ClueCommand> commands = plugin.createCommands(ctx);
        if (commands == null || commands.isEmpty()) {
          continue;
        }
        for (ClueCommand command : commands) {
          if (command == null) {
            continue;
          }
          try {
            ctx.registerCommand(command);
          } catch (RuntimeException e) {
            System.err.println("Skipping command from plugin '" + pluginName + "': " + e.getMessage());
          }
        }
      } catch (RuntimeException e) {
        System.err.println("Failed to register commands from plugin '" + pluginName + "': " + e.getMessage());
      }
    }
  }

  private static Map<String, CommandPlugin> loadPlugins() {
    Map<String, CommandPlugin> plugins = new LinkedHashMap<>();
    ServiceLoader<CommandPlugin> loader = ServiceLoader.load(CommandPlugin.class);
    Iterator<CommandPlugin> iterator = loader.iterator();
    while (iterator.hasNext()) {
      CommandPlugin plugin;
      try {
        plugin = iterator.next();
      } catch (ServiceConfigurationError e) {
        System.err.println("Failed to load command plugin: " + e.getMessage());
        continue;
      }
      String name;
      try {
        name = plugin.getName();
      } catch (RuntimeException e) {
        System.err.println("Skipping CommandPlugin with failing name: " + plugin.getClass().getName());
        continue;
      }
      if (name == null || name.isBlank()) {
        System.err.println("Skipping CommandPlugin with empty name: " + plugin.getClass().getName());
        continue;
      }
      if (plugins.containsKey(name)) {
        System.err.println("Skipping duplicate CommandPlugin name: " + name);
        continue;
      }
      plugins.put(name, plugin);
    }
    return Collections.unmodifiableMap(plugins);
  }

  private static String pluginName(CommandPlugin plugin) {
    try {
      String name = plugin.getName();
      if (name == null || name.isBlank()) {
        return plugin.getClass().getName();
      }
      return name;
    } catch (RuntimeException e) {
      return plugin.getClass().getName();
    }
  }
}
