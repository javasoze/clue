package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import io.dashbase.clue.LuceneContext;

public class DefaultCommandRegistrar implements CommandRegistrar {
    @Override
    public void registerCommands(ClueContext clueCtx) {
        LuceneContext ctx = (LuceneContext)clueCtx;
        // registers all the commands we currently support
        new HelpCommand(ctx);
        new InfoCommand(ctx);
        new DocValCommand(ctx);
        new SearchCommand(ctx);
        new CountCommand(ctx);
        new TermsCommand(ctx);
        new PostingsCommand(ctx);
        new DocSetInfoCommand(ctx);
        new MergeCommand(ctx);
        new DeleteCommand(ctx);
        new ReadonlyCommand(ctx);
        new DirectoryCommand(ctx);
        new ExplainCommand(ctx);
        new NormsCommand(ctx);
        new TermVectorCommand(ctx);
        new StoredFieldCommand(ctx);
        new ReconstructCommand(ctx);
        new ExportCommand(ctx);
        new IndexTrimCommand(ctx);
        new GetUserCommitDataCommand(ctx);
        new SaveUserCommitData(ctx);
        new DeleteUserCommitData(ctx);
        new DumpDocCommand(ctx);
    }
}
