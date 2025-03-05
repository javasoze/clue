package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.api.BytesRefPrinter;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PointValues;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Readonly
public class PointsCommand extends ClueCommand {
    private final LuceneContext ctx;

    public PointsCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Override
    public String getName() {
        return "points";
    }

    @Override
    public String help() {
        return "gets points values from the index, <field>";
    }

    @Override
    protected ArgumentParser buildParser(ArgumentParser parser) {
        parser.addArgument("-f", "--field").required(true).help("field");
        return parser;
    }

    static Long toLong(byte[] original) {
        if (original == null || original.length < 8) {
            return null; // Skip if invalid
        }
        return LongPoint.decodeDimension(original, 0);
    }

    @Override
    public void execute(Namespace args, PrintStream out) throws Exception {
        String field = args.getString("field");
        if (field == null){
            out.println("Usage: field");
            return;
        }

        BytesRefPrinter bytesRefPrinter = ctx.getTermBytesRefDisplay().getBytesRefPrinter(field);


        IndexReader reader = ctx.getIndexReader();
        List<LeafReaderContext> leaves = reader.leaves();

        int numCount = 0;
        int numPerPage = 20;

        for (LeafReaderContext leaf : leaves){
            LeafReader atomicReader = leaf.reader();

            PointValues pointValues = atomicReader.getPointValues(field);
            TreeMap<Long, Integer> valueToDocCount = new TreeMap<>();
            if (pointValues != null) {

                pointValues.intersect(new PointValues.IntersectVisitor() {
                    @Override
                    public void visit(int docID) throws IOException {
                    }

                    @Override
                    public void visit(int docID, byte[] packedValue) throws IOException {
                        Long value = toLong(packedValue);
                        if (value != null) {
                            valueToDocCount.put(value, valueToDocCount.getOrDefault(value, 0) + 1);
                        }
                    }

                    @Override
                    public PointValues.Relation compare(byte[] minPackedValue, byte[] maxPackedValue) {
                        return PointValues.Relation.CELL_CROSSES_QUERY;
                    }
                });

                // Print out the values and their counts
                while(valueToDocCount != null && !valueToDocCount.isEmpty()){
                    numCount++;
                    Map.Entry<Long, Integer> entry = valueToDocCount.pollFirstEntry();
                    if (entry == null) break;
                    Long key = entry.getKey();
                    Integer count = valueToDocCount.remove(key);
                    out.println(key+" ("+count+") ");
                    if (ctx.isInteractiveMode() && numCount % numPerPage == 0){
                        out.println("Press q to break");
                        int ch = System.in.read();
                        if (ch == 'q' || ch == 'Q') {
                            out.flush();
                            return;
                        }
                    }

                    valueToDocCount.entrySet().stream().forEach(
                            e -> {
                                long value = e.getKey();
                                int countVal = e.getValue();
                                System.out.println(value + " (" + countVal + ")");
                            }
                    );
                }
            }
        }
        out.flush();
    }
}
