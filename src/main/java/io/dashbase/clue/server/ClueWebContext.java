package io.dashbase.clue.server;

import io.dashbase.clue.ClueAppConfiguration;
import io.dashbase.clue.ClueContext;
import io.dashbase.clue.LuceneContext;
import jakarta.annotation.PreDestroy;
import io.micronaut.context.annotation.Context;

@Context
public class ClueWebContext {
    private final LuceneContext ctx;

    public ClueWebContext(ClueWebConfiguration conf) throws Exception {
        System.setProperty("clue.web.mode", "true");
        String dir = conf.getDir();
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("Missing required configuration: clue.web.dir");
        }
        ClueAppConfiguration appConfiguration = ClueAppConfiguration.load();
        this.ctx = new LuceneContext(dir, appConfiguration, false);
        this.ctx.setReadOnlyMode(true);
    }

    public ClueContext context() {
        return ctx;
    }

    @PreDestroy
    public void shutdown() throws Exception {
        ctx.shutdown();
    }
}
