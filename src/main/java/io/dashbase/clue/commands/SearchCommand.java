package io.dashbase.clue.commands;

import java.io.PrintStream;

import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import io.dashbase.clue.ClueContext;

@Readonly
public class SearchCommand extends ClueCommand {

  public SearchCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "search";
  }

  @Override
  public String help() {
    return "executes a query against the index, input: <query string>";
  }

  @Override
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-q", "--query").required(true);
    parser.addArgument("-n", "--num").type(Integer.class).setDefault(10);
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    IndexReader r = ctx.getIndexReader();
    IndexSearcher searcher = new IndexSearcher(r);
    String qstring = args.get("query");
    Query q = null;
    if (qstring == null || qstring.trim().isEmpty() || qstring.trim().equals("*")){
      q = new MatchAllDocsQuery();
    }
    else{
      try{
        q = ctx.getQueryBuilder().build(qstring);
      }
      catch(Exception e){
        out.println("cannot parse query: "+e.getMessage());
        return;
      }
    }
    
    out.println("parsed query: "+q);

    int count = args.getInt("num");
    
    long start = System.currentTimeMillis();
    TopDocs td = searcher.search(q, count);
    long end = System.currentTimeMillis();
    
    out.println("numhits: " + td.totalHits);
    out.println("time: "+(end-start)+"ms");
    ScoreDoc[] docs = td.scoreDocs;
    for (ScoreDoc doc : docs){
      out.println("doc: "+doc.doc+", score: "+doc.score);
    }
  }

}
