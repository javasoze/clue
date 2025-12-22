package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.util.DocIdMatcher;
import io.dashbase.clue.util.MatchSomeDocsQuery;
import io.dashbase.clue.util.MatcherDocIdSetIterator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import java.io.PrintStream;

@Command(name = "trim", mixinStandardHelpOptions = true)
public class IndexTrimCommand extends ClueCommand {

  private final LuceneContext ctx;

  public IndexTrimCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-p", "--percent"}, required = true, description = "percent to trim")
  private int percent;

  @Override
  public String getName() {
    return "trim";
  }

  @Override
  public String help() {
    return "trims the index, <TRIM PERCENTAGE>"; 
  }
  
  private static Query buildDeleteQuery(final int percentToDelete, int maxDoc) {
    assert percentToDelete >= 0 && percentToDelete <= 100;
    return new MatchSomeDocsQuery(new MatcherDocIdSetIterator(DocIdMatcher.newRandomMatcher(percentToDelete), maxDoc));
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    int trimPercent = percent;
    
    if (trimPercent < 0 || trimPercent > 100) {
      out.println("invalid percent: " + trimPercent);
      return;
    }
    
    IndexWriter writer = ctx.getIndexWriter();    
    if (writer != null) {      
      IndexReader reader = ctx.getIndexReader();
      
      writer.deleteDocuments(buildDeleteQuery(trimPercent, reader.maxDoc()));
      writer.commit();      
      ctx.refreshReader();
      reader = ctx.getIndexReader();
      out.println("trim successful, index now contains: " + reader.numDocs() + " docs.");
    }
    else {
      out.println("unable to open writer, index is in readonly mode");
    }
  }

}
