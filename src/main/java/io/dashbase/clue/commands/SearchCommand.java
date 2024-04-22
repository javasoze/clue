package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.client.CmdlineHelper;
import java.io.PrintStream;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

@Readonly
public class SearchCommand extends ClueCommand {

    private final LuceneContext ctx;

    public SearchCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String help() {
        return "executes a query against the index";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-q", "--query").nargs("*").setDefault(new String[] {"*"});
        parser.addArgument("-n", "--num").type(Integer.class).setDefault(10);
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        IndexSearcher searcher = ctx.getIndexSearcher();
        List<String> qlist = args.getList("query");

        Query q;

        try {
            q = CmdlineHelper.toQuery(qlist, ctx.getQueryBuilder());
        } catch (Exception e) {
            out.println("cannot parse query: " + e.getMessage());
            return;
        }

        out.println("parsed query: " + q);

        int count = args.getInt("num");

        long start = System.currentTimeMillis();
        TopDocs td = searcher.search(q, count);
        long end = System.currentTimeMillis();

        out.println("numhits: " + td.totalHits);
        out.println("time: " + (end - start) + "ms");
        ScoreDoc[] docs = td.scoreDocs;
        for (ScoreDoc doc : docs) {
            out.println("doc: " + doc.doc + ", score: " + doc.score);
        }
    }
}
