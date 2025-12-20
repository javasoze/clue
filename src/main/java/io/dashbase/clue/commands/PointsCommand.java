package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
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
import java.util.concurrent.atomic.AtomicLong;

@Readonly
@Command(name = "points", mixinStandardHelpOptions = true)
public class PointsCommand extends ClueCommand {
    private final LuceneContext ctx;

    public PointsCommand(LuceneContext ctx) {
        super(ctx);
        this.ctx = ctx;
    }

    @Option(names = {"-f", "--field"}, required = true, description = "field")
    private String field;

    @Override
    public String getName() {
        return "points";
    }

    @Override
    public String help() {
        return "gets points values from the index, e.g. <field:value>";
    }

    static Long toLong(byte[] original) {
        if (original == null || original.length < 8) {
            return null; // Skip if invalid
        }
        return LongPoint.decodeDimension(original, 0);
    }

    @Override
    protected void run(PrintStream out) throws Exception {
        String field = this.field;

        final AtomicLong pointsVal = new AtomicLong(Long.MIN_VALUE);

        if (field != null){
            String[] parts = field.split(":");
            if (parts.length > 1){
                field = parts[0];
                pointsVal.set(Long.parseLong(parts[1]));
            }
        }
        else{
            out.println("Usage: field:value");
            return;
        }

        if (field == null){
            out.println("Usage: field");
            return;
        }
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
                            if (value.equals(pointsVal.get())) {
                                valueToDocCount.put(value, valueToDocCount.getOrDefault(value, 0) + 1);
                            }
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
                    Integer count = entry.getValue();
                    out.println(key+" ("+count+") ");
                    valueToDocCount.remove(key);
                    if (ctx.isInteractiveMode() && numCount % numPerPage == 0){
                        out.println("Press q to break");
                        int ch = System.in.read();
                        if (ch == 'q' || ch == 'Q') {
                            out.flush();
                            return;
                        }
                    }

                    entry = valueToDocCount.pollFirstEntry();
                    if (entry == null) break;
                    key = entry.getKey();
                    count = entry.getValue();
                    valueToDocCount.remove(key);
                    out.println(key+" ("+count+") ");
                }
            }
        }
        out.flush();
    }
}
