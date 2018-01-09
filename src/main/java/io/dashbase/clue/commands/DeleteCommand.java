package io.dashbase.clue.commands;

import java.io.PrintStream;

import io.dashbase.clue.ClueContext;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

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
    Query q = null;
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
    
    out.println("parsed query: "+q);
    
    if (q != null){
      IndexWriter writer = ctx.getIndexWriter();
      if (writer != null) {
        writer.deleteDocuments(q);
        writer.commit();
        ctx.refreshReader();
      }
      else {
        out.println("unable to open writer, index is in readonly mode");
      }
    }
  }

}
