package io.dashbase.clue.server;

import io.dashbase.clue.ClueApplication;
import io.dashbase.clue.ClueContext;
import io.dashbase.clue.commands.ClueCommand;
import io.dashbase.clue.commands.HelpCommand;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@Path("/clue")
public class ClueCommandResource {
    private final ClueContext ctx;

    public ClueCommandResource(ClueContext ctx) {
        this.ctx = ctx;
    }

    static String[] buildArgs(String param) {
        String[] args = new String[] {};
        if (param != null) {
            param = param.trim();
            if (!param.isEmpty()) {
                args = param.split("\\s+");
            }
        }
        return args;
    }

    @GET
    @Path("commands")
    @Produces({MediaType.APPLICATION_JSON})
    public Collection<String> commands() {
        Set<String> registeredCommands = ctx.getCommandRegistry().commandNames();
        return registeredCommands;
    }

    @GET
    @Path("command/{cmd}")
    public Response command(
            @PathParam("cmd") String cmd, @DefaultValue("") @QueryParam("args") String args)
            throws Exception {
        Optional<ClueCommand> clueCommand = ctx.getCommand(cmd);
        final AtomicBoolean cmdFound = new AtomicBoolean(false);
        if (clueCommand.isPresent()) {
            cmdFound.set(true);
        } else {
            clueCommand = ctx.getCommand(HelpCommand.CMD_NAME);
        }

        final ClueCommand command = clueCommand.isPresent() ? clueCommand.get() : null;
        final String[] commandArgs = buildArgs(args);

        StreamingOutput stream =
                new StreamingOutput() {
                    @Override
                    public void write(OutputStream os) throws IOException, WebApplicationException {
                        PrintStream ps = new PrintStream(os);
                        try {
                            ClueApplication.handleCommand(ctx, cmd, commandArgs, ps);
                            ps.flush();
                        } catch (Exception e) {
                            e.printStackTrace(ps);
                        }
                    }
                };

        return Response.ok(stream).build();
    }
}
