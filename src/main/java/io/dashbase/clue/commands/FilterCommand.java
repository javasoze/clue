package io.dashbase.clue.commands;

import java.io.PrintStream;

public class FilterCommand extends ClueCommand {
    protected final ClueCommand delegate;
    public FilterCommand(ClueCommand cmd) {
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
    public void execute(String[] args, PrintStream out) throws Exception {
        delegate.execute(args, out);
    }
}
