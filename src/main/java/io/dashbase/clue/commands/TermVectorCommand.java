package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import java.io.PrintStream;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

@Readonly
public class TermVectorCommand extends ClueCommand {

    private final LuceneContext ctx;

    public TermVectorCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "tv";
    }

    @Override
    public String help() {
        return "shows term vector of a field for a doc";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-f", "--field").required(true).help("field name");
        parser.addArgument("-d", "--doc").required(true).type(Integer.class).help("docid");
        return parser;
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {

        String field = args.getString("field");

        int doc = args.getInt("doc");

        IndexReader reader = ctx.getIndexReader();
        List<LeafReaderContext> leaves = reader.leaves();

        boolean found = false;
        boolean tvFound = false;
        for (LeafReaderContext ctx : leaves) {
            LeafReader atomicReader = ctx.reader();
            FieldInfo finfo = atomicReader.getFieldInfos().fieldInfo(field);
            if (finfo == null || !finfo.hasVectors()) continue;

            tvFound = true;

            int docID = doc - ctx.docBase;
            if (docID >= 0) {

                Terms terms = atomicReader.getTermVector(docID, field);
                if (terms == null) continue;

                TermsEnum te = terms.iterator();

                BytesRef text = null;

                while ((text = te.next()) != null) {
                    long tf = te.totalTermFreq();
                    out.println(text.utf8ToString() + " (" + tf + ")");
                }
                found = true;
                break;
            }
        }

        if (!tvFound) {
            out.println("term vector is not available for field: " + field);
            return;
        }

        if (!found) {
            out.println(doc + " not found");
            return;
        }
    }
}
