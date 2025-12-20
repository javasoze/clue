package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.client.CmdlineHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Readonly
@Command(name = "count", mixinStandardHelpOptions = true)
public class CountCommand extends ClueCommand {

	private final LuceneContext ctx;

	public CountCommand(LuceneContext ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	@Option(names = {"-q", "--query"}, arity = "0..*", description = "query")
	private String[] query;

	@Override
	public String getName() {
		return "count";
	}

	@Override
	public String help() {
		return "shows how many documents in index match the given query";
	}

	@Override
	protected void run(PrintStream out) throws Exception {
		IndexSearcher searcher = ctx.getIndexSearcher();

		List<String> qlist;
		if (query == null || query.length == 0) {
			qlist = Collections.singletonList("*");
		} else {
			qlist = Arrays.asList(query);
		}
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
