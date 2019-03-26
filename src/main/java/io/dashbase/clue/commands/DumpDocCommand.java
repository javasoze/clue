package io.dashbase.clue.commands;


import io.dashbase.clue.ClueContext;
import io.dashbase.clue.LuceneContext;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

import java.io.PrintStream;
import java.util.List;

@Readonly
public class DumpDocCommand extends ClueCommand {

    private final LuceneContext ctx;

    public DumpDocCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "dumpdoc";
    }

    @Override
    public String help() {
        return "dumps all the stored fields in the document";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-d", "--doc").type(Integer.class).required(true).help("doc id");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        int doc = args.getInt("doc");
        IndexReader reader = ctx.getIndexReader();
        List<LeafReaderContext> leaves = reader.leaves();

        for (LeafReaderContext ctx : leaves) {
            LeafReader atomicReader = ctx.reader();

            int docID = doc - ctx.docBase;

            if (docID >= atomicReader.maxDoc()) {
                continue;
            }

            if (docID >= 0) {
                Document storedData = atomicReader.document(docID);

                if (storedData == null) continue;

                for (IndexableField indexableField : storedData.getFields()) {
                    out.print(indexableField.name() + ":");
                    final Number number = indexableField.numericValue();
                    if (number != null) {
                        out.println(number);
                        continue;
                    }

                    final String strData = indexableField.stringValue();

                    if (strData != null) {
                        out.println(strData);
                        continue;
                    }

                    final BytesRef bytesRef = indexableField.binaryValue();
                    if (bytesRef != null) {
                        out.println(bytesRef);
                    }

                    out.println("<unsupported value type>");
                }
            }
        }
    }
}
