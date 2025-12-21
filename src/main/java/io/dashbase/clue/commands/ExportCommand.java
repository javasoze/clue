package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.PrintStream;
import java.nio.file.FileSystems;

@Readonly
@Command(name = "export", mixinStandardHelpOptions = true)
public class ExportCommand extends ClueCommand {

  private final LuceneContext ctx;

  public ExportCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-t", "--text"}, arity = "0..1", defaultValue = "true", fallbackValue = "true",
      description = "export to text")
  private boolean exportToText;

  @Option(names = {"-o", "--output"}, required = true, description = "output directory")
  private String output;

  @Override
  public String getName() {
    return "export";
  }

  @Override
  public String help() {
    return "export index to readable text files";
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    boolean isExportToText = exportToText;
    if (isExportToText) {
      out.println("exporting index to text");
    }
    else {
      out.println("exporting index to binary");
    }

    FSDirectory fsdir = FSDirectory.open(FileSystems.getDefault().getPath(output));
    
    IndexWriter writer = null;
    
    try {
      IndexWriterConfig conf = new IndexWriterConfig(null);
      if (isExportToText) {
        conf.setCodec(new SimpleTextCodec());
      }
      writer = new IndexWriter(fsdir, conf);
      writer.addIndexes(ctx.getDirectory());
      writer.forceMerge(1);
    }
    finally {
      if (writer != null) {
        writer.commit();
        writer.close();
      }
    }
  }
}
