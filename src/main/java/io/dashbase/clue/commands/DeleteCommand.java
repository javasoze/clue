package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import java.io.PrintStream;

public class DeleteCommand extends ClueCommand {

  private final LuceneContext luceneContext;
  public DeleteCommand(LuceneContext ctx) {
    super(ctx);
    this.luceneContext = ctx;
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
      q = luceneContext.getQueryBuilder().build(qstring);
    }
    catch(Exception e){
      out.println("cannot parse query: "+e.getMessage());
      return;
    }
    
    out.println("parsed query: " + q);
    
    if (q != null){
      IndexWriter writer = luceneContext.getIndexWriter();
      if (writer != null) {
        writer.deleteDocuments(q);
        writer.commit();
        luceneContext.refreshReader();
      }
      else {
        out.println("unable to open writer, index is in readonly mode");
      }
    }
  }

}
