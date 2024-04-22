package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import java.io.PrintStream;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

@Readonly
public class ReadonlyCommand extends ClueCommand {

    private final LuceneContext ctx;

    public ReadonlyCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "readonly";
    }

    @Override
    public String help() {
        return "puts clue in readonly mode";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("readonly").type(Boolean.class).nargs("?").help("readonly true/false");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        boolean readonly = args.getBoolean("readonly");
        ctx.setReadOnlyMode(readonly);
        out.println("readonly mode is now: " + readonly);
    }
}
