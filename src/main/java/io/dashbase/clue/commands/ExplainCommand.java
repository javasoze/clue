package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.client.CmdlineHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

@Readonly
@Command(name = "explain", mixinStandardHelpOptions = true)
public class ExplainCommand extends ClueCommand {

  private final LuceneContext ctx;

  public ExplainCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-q", "--query"}, arity = "1..*", required = true, description = "query")
  private String[] query;

  @Option(names = {"-d", "--docs"}, arity = "1..*", required = true, description = "doc ids, e.g. d1 d2 d3")
  private int[] docs;

  @Override
  public String getName() {
    return "explain";
  }

  @Override
  public String help() {
    return "shows score explanation of a doc";
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    List<String> qlist = Arrays.asList(query);

    IndexSearcher searcher = ctx.getIndexSearcher();
    Query q;

    try{
      q = CmdlineHelper.toQuery(qlist, ctx.getQueryBuilder());
    }
    catch(Exception e){
      out.println("cannot parse query: "+e.getMessage());
      return;
    }

    out.println("parsed query: "+q);
    
    for (int docid : docs) {
      Explanation expl = searcher.explain(q, docid);
      out.println(expl);
    }
    
    out.flush();
  }

}
