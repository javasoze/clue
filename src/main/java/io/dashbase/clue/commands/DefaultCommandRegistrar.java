package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import io.dashbase.clue.LuceneContext;

public class DefaultCommandRegistrar implements CommandRegistrar {
    @Override
    public void registerCommands(ClueContext clueCtx) {
        LuceneContext ctx = (LuceneContext)clueCtx;
        // registers all the commands we currently support
        register(clueCtx, new HelpCommand(ctx));
        register(clueCtx, new InfoCommand(ctx));
        register(clueCtx, new DocValCommand(ctx));
        register(clueCtx, new SearchCommand(ctx));
        register(clueCtx, new CountCommand(ctx));
        register(clueCtx, new TermsCommand(ctx));
        register(clueCtx, new PostingsCommand(ctx));
        register(clueCtx, new DocSetInfoCommand(ctx));
        register(clueCtx, new MergeCommand(ctx));
        register(clueCtx, new DeleteCommand(ctx));
        register(clueCtx, new ReadonlyCommand(ctx));
        register(clueCtx, new DirectoryCommand(ctx));
        register(clueCtx, new ExplainCommand(ctx));
        register(clueCtx, new NormsCommand(ctx));
        register(clueCtx, new TermVectorCommand(ctx));
        register(clueCtx, new StoredFieldCommand(ctx));
        register(clueCtx, new ReconstructCommand(ctx));
        register(clueCtx, new ExportCommand(ctx));
        register(clueCtx, new IndexTrimCommand(ctx));
        register(clueCtx, new GetUserCommitDataCommand(ctx));
        register(clueCtx, new SaveUserCommitData(ctx));
        register(clueCtx, new DeleteUserCommitData(ctx));
        register(clueCtx, new DumpDocCommand(ctx));
        register(clueCtx, new PointsCommand(ctx));
        CommandPlugins.registerAll(clueCtx);
    }

    private void register(ClueContext ctx, ClueCommand command) {
        ctx.registerCommand(command);
    }
}
