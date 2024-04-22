package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.client.CmdlineHelper;
import java.io.PrintStream;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

@Readonly
public class ExplainCommand extends ClueCommand {

    private final LuceneContext ctx;

    public ExplainCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "explain";
    }

    @Override
    public String help() {
        return "shows score explanation of a doc";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-q", "--query").nargs("*").required(true).help("query");
        parser.addArgument("-d", "--docs")
                .type(Integer.class)
                .nargs("*")
                .help("doc ids, e.g. d1 d2 d3");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        List<String> qlist = args.getList("query");

        List<Integer> docidList = args.getList("docs");

        IndexSearcher searcher = ctx.getIndexSearcher();
        Query q;

        try {
            q = CmdlineHelper.toQuery(qlist, ctx.getQueryBuilder());
        } catch (Exception e) {
            out.println("cannot parse query: " + e.getMessage());
            return;
        }

        out.println("parsed query: " + q);

        for (Integer docid : docidList) {
            Explanation expl = searcher.explain(q, docid);
            out.println(expl);
        }

        out.flush();
    }
}
