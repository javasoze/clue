package io.dashbase.clue.commands;

import java.io.PrintStream;
import javax.validation.constraints.NotNull;
import net.sourceforge.argparse4j.inf.Namespace;

public class FilterCommand extends ClueCommand {
    protected final ClueCommand delegate;

    public FilterCommand(@NotNull ClueCommand cmd) {
        super(cmd.ctx, true);
        this.delegate = cmd;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String help() {
        return delegate.help();
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        delegate.execute(args, out);
    }
}
