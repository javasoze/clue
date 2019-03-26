package io.dashbase.clue.server;

import io.dashbase.clue.LuceneContext;
import io.dropwizard.Application;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;

public class ClueWebApplication extends Application<ClueWebConfiguration> {

    public static void main(String[] args) throws Exception {
        new ClueWebApplication().run(args);
    }

    @Override
    public void run(ClueWebConfiguration conf, Environment environment) throws Exception {
        final LuceneContext ctx = new LuceneContext(conf.dir, conf.clue, true);
        ctx.setReadOnlyMode(true);
        environment.jersey().register(new ClueCommandResource(ctx));
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() throws Exception {
            }

            @Override
            public void stop() throws Exception {
                ctx.shutdown();
            }
        });
    }
}
