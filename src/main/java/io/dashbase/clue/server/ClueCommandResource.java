package io.dashbase.clue.server;

import io.dashbase.clue.ClueContext;
import io.dashbase.clue.commands.ClueCommand;
import io.dashbase.clue.commands.HelpCommand;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Path("/clue")
public class ClueCommandResource {
    private final ClueContext ctx;
    public ClueCommandResource(ClueContext ctx) {
        this.ctx = ctx;
    }

    static String[] buildArgs(String param) {
        String[] args = new String[]{};
        if (param != null) {
            param = param.trim();
            if (!param.isEmpty()) {
                args = param.split("\\s+");
            }
        }
        return  args;
    }

    @GET
    @Path("command/{cmd}")
    public Response command(@PathParam("cmd") String cmd, @DefaultValue("") @QueryParam("args") String args) throws Exception {
        Optional<ClueCommand> clueCommand = ctx.getCommand(cmd);
        final AtomicBoolean cmdFound = new AtomicBoolean(false);
        if (clueCommand.isPresent()) {
            cmdFound.set(true);
        } else {
            clueCommand = ctx.getCommand(HelpCommand.CMD_NAME);
        }

        final ClueCommand command = clueCommand.isPresent() ? clueCommand.get() : null;
        final String[] commandArgs = buildArgs(args);

        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                try {
                    PrintStream ps = new PrintStream(os);
                    if (!cmdFound.get()) {
                        ps.println("command " + cmd + " not found");
                    }
                    if (command != null) {
                        command.execute(commandArgs, ps);
                    }
                    ps.flush();
                } catch (IOException ioe) {
                    throw ioe;
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }
        };

        return Response.ok(stream).build();
    }
}
