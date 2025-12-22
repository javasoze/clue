package io.dashbase.clue.server;

import io.dashbase.clue.ClueApplication;
import io.dashbase.clue.ClueContext;
import io.dashbase.clue.util.CommandLineParser;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Set;

@Path("/clue")
public class ClueCommandResource {
    private final ClueContext ctx;
    public ClueCommandResource(ClueContext ctx) {
        this.ctx = ctx;
    }

    static String[] buildArgs(String param) {
        return CommandLineParser.splitArgs(param);
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
    public Response command(@PathParam("cmd") String cmd, @DefaultValue("") @QueryParam("args") String args) throws Exception {
        boolean cmdFound = ctx.getCommand(cmd).isPresent();
        final String[] commandArgs = buildArgs(args);
        int status = cmdFound ? 200 : 404;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buffer)) {
            int code = ClueApplication.handleCommand(ctx, cmd, commandArgs, ps);
            if (cmdFound && code != 0) {
                status = 500;
            }
        }
        return Response.status(status)
                .type(MediaType.TEXT_PLAIN)
                .entity(buffer.toString())
                .build();
    }
}
