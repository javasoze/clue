package io.dashbase.clue.commands;

import java.io.PrintStream;
import java.util.List;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.client.CmdlineHelper;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

@Readonly
public class CountCommand extends ClueCommand {

	private final LuceneContext ctx;

	public CountCommand(LuceneContext ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	@Override
	public String getName() {
		return "count";
	}

	@Override
	public String help() {
		return "shows how many documents in index match the given query";
	}

	@Override
	protected ArgumentParser buildParser(ArgumentParser parser) {
		parser.addArgument("-q", "--query").nargs("*").setDefault(new String[]{"*"});
		return parser;
	}

	@Override
	public void execute(Namespace args, PrintStream out) throws Exception {
		IndexSearcher searcher = ctx.getIndexSearcher();

		List<String> qlist = args.getList("query");
		Query q;
		try{
			q = CmdlineHelper.toQuery(qlist, ctx.getQueryBuilder());
		}
		catch(Exception e){
			out.println("cannot parse query: "+e.getMessage());
			return;
		}

		out.println("parsed query: " + q);

		long start = System.currentTimeMillis();
		int count = searcher.count(q);
		long end = System.currentTimeMillis();

		out.println("count: " + count);
		out.println("time: " + (end-start) + "ms");
	}
}
