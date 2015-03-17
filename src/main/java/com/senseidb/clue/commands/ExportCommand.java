package com.senseidb.clue.commands;

import java.io.PrintStream;
import java.nio.file.FileSystems;

import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import com.senseidb.clue.ClueContext;

public class ExportCommand extends ClueCommand {

  public ExportCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "export";
  }

  @Override
  public String help() {
    return "export index to readable text files";
  }

  @Override
  public void execute(String[] args, PrintStream out) throws Exception {
    if (args.length < 1) {
      out.println("usage: export output bin/text (default text)");
      return;
    }
    
    boolean isExportToText;
    
    try {
      if ("bin".equals(args[1])) {
        isExportToText = false;
      }
      else {
        isExportToText = true;
      }
    }
    catch (Exception e) {
      isExportToText = true;
    }
    
    if (isExportToText) {
      System.out.println("exporting index to text");
    }
    else {
      System.out.println("exporting index to binary");
    }

    FSDirectory fsdir = FSDirectory.open(FileSystems.getDefault().getPath(args[0]));
    
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
