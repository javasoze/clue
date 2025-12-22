package io.dashbase.clue.server;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("clue.web")
public class ClueWebConfiguration {
    private String dir;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }
}
