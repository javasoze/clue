package io.dashbase.clue.commands;


import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.api.BytesRefPrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.BytesRef;

import java.io.PrintStream;
import java.util.List;

@Readonly
@Command(name = "dumpdoc", mixinStandardHelpOptions = true)
public class DumpDocCommand extends ClueCommand {

    private final LuceneContext ctx;

    public DumpDocCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Option(names = {"-d", "--doc"}, required = true, description = "doc id")
    private int doc;

    @Override
    public String getName() {
        return "dumpdoc";
    }

    @Override
    public String help() {
        return "dumps all the stored fields in the document";
    }

    @Override
    protected void run(PrintStream out) throws Exception {
        IndexReader reader = ctx.getIndexReader();
        List<LeafReaderContext> leaves = reader.leaves();

        for (LeafReaderContext ctx : leaves) {
            LeafReader atomicReader = ctx.reader();

            int docID = doc - ctx.docBase;

            if (docID >= atomicReader.maxDoc()) {
                continue;
            }

            if (docID >= 0) {
                var storedField = atomicReader.storedFields();
                Document storedData = storedField.document(docID);

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
                        out.println(BytesRefPrinter.toUtf8String(bytesRef));
                        continue;
                    }

                    out.println("<unsupported value type>");
                }
            }
        }
    }
}
