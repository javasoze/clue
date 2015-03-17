package com.senseidb.clue.commands;


import com.senseidb.clue.ClueContext;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

import java.io.PrintStream;
import java.util.List;

public class DumpDocCommand extends ClueCommand {

    public DumpDocCommand(ClueContext ctx) {
        super(ctx);
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
    public void execute(String[] args, PrintStream out) throws Exception {
        if (args.length != 1) {
            out.println("usage: doc");
            return;
        }

        int doc = Integer.parseInt(args[0]);

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
