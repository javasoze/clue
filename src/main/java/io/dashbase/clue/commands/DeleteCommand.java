package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import java.io.PrintStream;

@Command(name = "delete", mixinStandardHelpOptions = true)
public class DeleteCommand extends ClueCommand {

  private final LuceneContext luceneContext;
  public DeleteCommand(LuceneContext ctx) {
    super(ctx);
    this.luceneContext = ctx;
  }

  @Option(names = {"-q", "--query"}, required = true)
  private String query;

  @Override
  public String getName() {
    return "delete";
  }

  @Override
  public String help() {
    return "deletes a list of documents from searching via a query, input: query";
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    Query q = null;
    String qstring = query;
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
