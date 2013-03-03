package com.senseidb.clue.commands;

import java.io.PrintStream;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

import com.senseidb.clue.ClueContext;

public class DeleteCommand extends ClueCommand {

  public DeleteCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "delete";
  }

  @Override
  public String help() {
    return "deletes a list of documents from searching via a query, input: query";
  }
  
  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    QueryParser qparser = new QueryParser(Version.LUCENE_41, "contents", new StandardAnalyzer(Version.LUCENE_41));
    Query q = null;
    try{
      q = qparser.parse(args[0]);
    }
    catch(Exception e){
      out.println("cannot parse query");
      return;
    }
    
    if (q != null){
      IndexWriter writer = ctx.getIndexWriter();
      writer.deleteDocuments(q);
      writer.commit();
      ctx.refreshReader();
    }
  }

}
