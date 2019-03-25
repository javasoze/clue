package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import java.io.PrintStream;
import java.util.List;

@Readonly
public class ExplainCommand extends ClueCommand {

 
  public ExplainCommand(ClueContext ctx) {
    super(ctx);
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
    parser.addArgument("-q", "--query").required(true).help("query");
    parser.addArgument("-d", "--docs").type(Integer.class).nargs("*").help("doc ids, e.g. d1 d2 d3");
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    String qstring = args.getString("query");

    List<Integer> docidList = args.getList("docs");

    IndexReader r = ctx.getIndexReader();
    IndexSearcher searcher = new IndexSearcher(r);
    Query q = null;

    try{
      q = ctx.getQueryBuilder().build(qstring);
    }
    catch(Exception e){
      out.println("cannot parse query: "+e.getMessage());
      return;
    }
    
    for (Integer docid : docidList) {
      Explanation expl = searcher.explain(q, docid);
      out.println(expl);
    }
    
    out.flush();
  }

}
