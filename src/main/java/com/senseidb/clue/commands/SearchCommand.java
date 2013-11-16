package com.senseidb.clue.commands;

import java.io.PrintStream;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import com.senseidb.clue.ClueContext;

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
  public void execute(String[] args, PrintStream out) throws Exception {
    IndexReader r = ctx.getIndexReader();
    IndexSearcher searcher = new IndexSearcher(r);
    Query q = null;
    if (args.length == 0){
      q = new MatchAllDocsQuery();
    }
    else{
      StringBuilder buf = new StringBuilder();
      for (String s : args){
        buf.append(s).append(" ");
      }
      String qstring = buf.toString();
      try{
        q = ctx.getQueryBuilder().build(qstring);
      }
      catch(Exception e){
        out.println("cannot parse query: "+e.getMessage());
        return;
      }
    }
    
    out.println("parsed query: "+q);
    
    long start = System.currentTimeMillis();
    TopDocs td = searcher.search(q, 10);
    long end = System.currentTimeMillis();
    
    out.println("numhits: " + td.totalHits);
    out.println("time: "+(end-start)+"ms");
    ScoreDoc[] docs = td.scoreDocs;
    for (ScoreDoc doc : docs){
      out.println("doc: "+doc.doc+", score: "+doc.score);
    }
  }

}
