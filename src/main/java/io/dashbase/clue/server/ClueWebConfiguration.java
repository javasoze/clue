package io.dashbase.clue.server;

import io.dashbase.clue.ClueAppConfiguration;
import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;

public class ClueWebConfiguration extends Configuration {
    @NotNull
    public String dir = null;
    public ClueAppConfiguration clue = new ClueAppConfiguration();
}
