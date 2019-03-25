package io.dashbase.clue.commands;

import java.io.PrintStream;

import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.IndexWriter;

import io.dashbase.clue.ClueContext;

public class MergeCommand extends ClueCommand {

  public MergeCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "merge";
  }

  @Override
  public String help() {
    return "force merges segments into given N segments, input: number of max segments";
  }

  @Override
  protected ArgumentParser buildParser(ArgumentParser parser) {
    parser.addArgument("-n", "--num").type(Integer.class).setDefault(1);
    return parser;
  }

  @Override
  public void execute(Namespace args, PrintStream out) throws Exception {
    int count = args.getInt("num");
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
