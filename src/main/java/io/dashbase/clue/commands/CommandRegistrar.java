package io.dashbase.clue.commands;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dashbase.clue.ClueContext;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = DefaultCommandRegistrar.class)
@JsonSubTypes({@JsonSubTypes.Type(name = "default", value = DefaultCommandRegistrar.class)})
public interface CommandRegistrar {
    void registerCommands(ClueContext ctx);
}
