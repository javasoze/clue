package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.index.IndexWriter;

import java.io.PrintStream;

@Command(name = "merge", mixinStandardHelpOptions = true)
public class MergeCommand extends ClueCommand {

  private final LuceneContext ctx;

  public MergeCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-n", "--num"}, defaultValue = "1")
  private int num;

  @Override
  public String getName() {
    return "merge";
  }

  @Override
  public String help() {
    return "force merges segments into given N segments, input: number of max segments";
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    int count = num;
    IndexWriter writer = ctx.getIndexWriter();
    if (writer != null) {
      writer.forceMerge(count, true);
      writer.commit();
      ctx.refreshReader();
    }
    else {
      out.println("unable to open index writer, index is in readonly mode");
    }
  }

}
