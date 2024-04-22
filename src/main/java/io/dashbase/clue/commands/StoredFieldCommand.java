package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.BytesRef;

@Readonly
public class StoredFieldCommand extends ClueCommand {

    private final LuceneContext ctx;

    public StoredFieldCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "stored";
    }

    @Override
    public String help() {
        return "displays stored data for a given field";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-f", "--field").required(true).help("field name");
        parser.addArgument("-d", "--doc").type(Integer.class).required(true).help("docid");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {

        String field = args.getString("field");

        int doc = args.getInt("doc");

        IndexReader reader = ctx.getIndexReader();
        List<LeafReaderContext> leaves = reader.leaves();

        boolean found = false;
        boolean stored = false;

        for (LeafReaderContext ctx : leaves) {
            LeafReader atomicReader = ctx.reader();
            FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);
            if (finfo == null) continue;

            stored = true;

            int docID = doc - ctx.docBase;

            if (docID >= atomicReader.maxDoc()) {
                continue;
            }

            if (docID >= 0) {

                Document storedData =
                        atomicReader.document(docID, new HashSet<String>(Arrays.asList(field)));

                if (storedData == null) continue;

                String strData = storedData.get(field);

                if (strData != null) {
                    out.println(strData);
                    found = true;
                    break;
                }

                BytesRef bytesRef = storedData.getBinaryValue(field);
                if (bytesRef != null) {
                    out.println(bytesRef);
                    found = true;
                    break;
                }

                BytesRef[] dataArray = storedData.getBinaryValues(field);

                if (dataArray == null || dataArray.length == 0) {
                    continue;
                }

                for (BytesRef data : dataArray) {
                    out.println(data);
                }
                found = true;
                break;
            }
        }

        if (!stored) {
            out.println("stored data is not available for field: " + field);
            return;
        }

        if (!found) {
            out.println(doc + " not found");
            return;
        }
    }
}
