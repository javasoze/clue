package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.client.CmdlineHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Readonly
@Command(name = "search", mixinStandardHelpOptions = true)
public class SearchCommand extends ClueCommand {

  private final LuceneContext ctx;

  public SearchCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-q", "--query"}, arity = "0..*", description = "query")
  private String[] query;

  @Option(names = {"-n", "--num"}, defaultValue = "10")
  private int num;

  @Override
  public String getName() {
    return "search";
  }

  @Override
  public String help() {
    return "executes a query against the index";
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

    int count = num;

    long start = System.currentTimeMillis();
    TopDocs td = searcher.search(q, count);
    long end = System.currentTimeMillis();
    
    out.println("numhits: " + td.totalHits);
    out.println("time: " + (end-start) + "ms");
    ScoreDoc[] docs = td.scoreDocs;
    for (ScoreDoc doc : docs){
      out.println("doc: " + doc.doc + ", score: " + doc.score);
    }
  }

}
