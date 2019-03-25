package io.dashbase.clue.commands;

import io.dashbase.clue.ClueContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import java.io.PrintStream;

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
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-q", "--query").required(true);
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    Query q = null;
    String qstring = args.getString("query");
    try{
      q = ctx.getQueryBuilder().build(qstring);
    }
    catch(Exception e){
      out.println("cannot parse query: "+e.getMessage());
      return;
    }
    
    out.println("parsed query: " + q);
    
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
