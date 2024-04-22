package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;
import net.sourceforge.argparse4j.inf.Namespace;

public class CommandRegistry {
    private final SortedMap<String, ClueCommand> cmdMap = new TreeMap<String, ClueCommand>();
    private volatile boolean readonly = true;

    public void registerCommand(ClueCommand cmd) {
        String cmdName = cmd.getName();
        if (cmdMap.containsKey(cmdName)) {
            throw new IllegalArgumentException(cmdName + " exists!");
        }
        cmdMap.put(cmdName, cmd);
    }

    public Set<String> commandNames() {
        return cmdMap.keySet();
    }

    public boolean exists(String command) {
        return cmdMap.containsKey(command);
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public boolean isReadonly() {
        return this.readonly;
    }

    public Collection<ClueCommand> getAvailableCommands() {
        if (readonly) {
            return cmdMap.values().stream()
                    .filter(c -> c.getClass().isAnnotationPresent(Readonly.class))
                    .collect(Collectors.toList());
        } else {
            return cmdMap.values();
        }
    }

    public Optional<ClueCommand> getCommand(String cmd) {

        ClueCommand command = cmdMap.get(cmd);
        if (command != null) {
            if (readonly) {
                if (!command.getClass().isAnnotationPresent(Readonly.class)) {
                    command =
                            new FilterCommand(command) {
                                public void execute(Namespace args, PrintStream out)
                                        throws Exception {
                                    out.println(
                                            "read-only mode, command: "
                                                    + getName()
                                                    + " is not allowed");
                                }
                            };
                }
            }
            return Optional.of(command);
        } else {
            return Optional.empty();
        }
    }
}
