package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

@Readonly
public class ExportCommand extends ClueCommand {

    private final LuceneContext ctx;

    public ExportCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
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
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-t", "--text")
                .type(Boolean.class)
                .setDefault(true)
                .help("export to text");
        parser.addArgument("-o", "--output").required(true).help("output directory");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        boolean isExportToText = args.getBoolean("text");
        if (isExportToText) {
            System.out.println("exporting index to text");
        } else {
            System.out.println("exporting index to binary");
        }

        FSDirectory fsdir =
                FSDirectory.open(FileSystems.getDefault().getPath(args.getString("output")));

        IndexWriter writer = null;

        try {
            IndexWriterConfig conf = new IndexWriterConfig(null);
            if (isExportToText) {
                conf.setCodec(new SimpleTextCodec());
            }
            writer = new IndexWriter(fsdir, conf);
            writer.addIndexes(ctx.getDirectory());
            writer.forceMerge(1);
        } finally {
            if (writer != null) {
                writer.commit();
                writer.close();
            }
        }
    }
}
