package io.dashbase.clue.server;

import io.dashbase.clue.ClueApplication;
import io.dashbase.clue.ClueContext;
import io.dashbase.clue.commands.ClueCommand;
import io.dashbase.clue.util.CommandLineParser;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

@Controller("/clue")
public class ClueCommandResource {
    private final ClueContext ctx;

    public ClueCommandResource(ClueWebContext ctxProvider) {
        this.ctx = ctxProvider.context();
    }

    static String[] buildArgs(String param) {
        return CommandLineParser.splitArgs(param);
    }

    @Get("/commands")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<String> commands() {
        return ctx.getCommandRegistry()
                .getAvailableCommands()
                .stream()
                .map(ClueCommand::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    @Get("/command/{cmd}")
    @Produces(MediaType.TEXT_PLAIN)
    public HttpResponse<String> command(@PathVariable String cmd,
                                        @QueryValue(defaultValue = "") String args) throws Exception {
        boolean cmdFound = ctx.getCommand(cmd).isPresent();
        final String[] commandArgs = buildArgs(args);
        HttpStatus status = cmdFound ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buffer)) {
            int code = ClueApplication.handleCommand(ctx, cmd, commandArgs, ps);
            if (cmdFound && code != 0) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
        }
        String output = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        return HttpResponse.status(status)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .body(output);
    }
}
