package io.dashbase.clue.server;

import io.dashbase.clue.ClueAppConfiguration;
import io.dropwizard.Configuration;

public class ClueWebConfiguration extends Configuration {
    public ClueAppConfiguration clue = new ClueAppConfiguration();
}
