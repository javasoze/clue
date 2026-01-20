// histogram-agg.jsh
// OpenSearch-style histogram bucket aggregation for Lucene indexes
// Buckets numeric values by fixed interval
// https://docs.opensearch.org/latest/aggregations/bucket/histogram/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/histogram-agg.jsh
//   jshell> histogram("price", 1000, "double")

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.NumberFormat;

// Cached reader
DirectoryReader _histReader = null;
Directory _histDir = null;

// Codec error detection helper
String _detectCodecErrorHist(Exception e) {
    String msg = e.getMessage();
    if (msg == null) msg = "";
    Throwable cause = e.getCause();
    while (cause != null) {
        if (cause.getMessage() != null) msg += " " + cause.getMessage();
        cause = cause.getCause();
    }
    var pattern = Pattern.compile("Could not load codec '([^']+)'");
    var matcher = pattern.matcher(msg);
    if (matcher.find()) return matcher.group(1);
    return null;
}

void _openHistIndex() throws Exception {
    if (_histReader != null) return;
    try {
        _histDir = FSDirectory.open(Path.of(indexPath));
        _histReader = DirectoryReader.open(_histDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _histReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorHist(e);
        if (codecName != null) {
            System.out.println("\n" + "!".repeat(60));
            System.out.println("ERROR: Codec '" + codecName + "' not found!");
            System.out.println("!".repeat(60));
            System.out.println("\nTo fix this:");
            System.out.println("1. Load codec support:  /open skills/codec-support.jsh");
            System.out.println("2. Register the codec:  registerCodec(\"" + codecName + "\", \"/path/to/codec.jar\")");
            System.out.println("3. Run the /env command shown, then reload this skill");
            throw new RuntimeException("Codec not found: " + codecName);
        }
        throw e;
    }
}

void closeHistIndex() throws Exception {
    if (_histReader != null) {
        _histReader.close();
        _histReader = null;
    }
    if (_histDir != null) {
        _histDir.close();
        _histDir = null;
    }
    System.out.println("Index closed.");
}

// Parse query string to Lucene Query
Query _parseQueryHist(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Convert raw long value based on field type
double _convertValueHist(long rawValue, String type) {
    if (type == null) type = "long";
    return switch (type.toLowerCase()) {
        case "double", "d", "float", "f" -> Double.longBitsToDouble(rawValue);
        case "int", "i", "long", "l" -> (double) rawValue;
        default -> (double) rawValue;
    };
}

// Format number with commas
String _formatNum(long n) {
    return NumberFormat.getInstance().format(n);
}

// Calculate bucket key for a value
long _bucketKey(double value, double interval) {
    return (long) (Math.floor(value / interval) * interval);
}

// Print histogram results
void _printHistogramResult(String field, String filterInfo, Map<Long, Long> buckets,
                           double interval, String type, long totalDocs, int minDocCount) {

    // Filter and sort buckets by key
    List<Map.Entry<Long, Long>> sorted = buckets.entrySet().stream()
        .filter(e -> e.getValue() >= minDocCount)
        .sorted(Map.Entry.comparingByKey())
        .collect(Collectors.toList());

    long displayedSum = sorted.stream().mapToLong(Map.Entry::getValue).sum();

    System.out.println();
    if (filterInfo != null) {
        System.out.println("Histogram Aggregation (" + filterInfo + "): " + field);
    } else {
        System.out.println("Histogram Aggregation: " + field);
    }
    System.out.println("=".repeat(55));
    System.out.println("Field:    " + field + " (" + type + ")");
    System.out.println("Interval: " + interval);
    System.out.println("Docs:     " + _formatNum(totalDocs));
    System.out.println("Buckets:  " + sorted.size());
    System.out.println("-".repeat(55));

    if (sorted.isEmpty()) {
        System.out.println("  (no buckets found)");
    } else {
        for (Map.Entry<Long, Long> entry : sorted) {
            long key = entry.getKey();
            long count = entry.getValue();
            double pct = totalDocs > 0 ? (count * 100.0 / totalDocs) : 0;

            String rangeStr;
            if (type.toLowerCase().startsWith("d") || type.toLowerCase().startsWith("f")) {
                rangeStr = String.format("[%.1f - %.1f)", (double) key, key + interval);
            } else {
                rangeStr = String.format("[%d - %d)", key, (long)(key + interval));
            }
            System.out.printf("  %-20s  %,10d docs (%5.1f%%)%n", rangeStr, count, pct);
        }
    }

    System.out.println("=".repeat(55));
    System.out.println("Total:   " + _formatNum(displayedSum) + " docs");
    System.out.println("=".repeat(55));
}

// ==================== Core Implementation ====================

// Histogram for all documents
void _histogramImpl(String field, double interval, String type, int minDocCount) throws Exception {
    _openHistIndex();

    Map<Long, Long> buckets = new TreeMap<>();
    long totalDocs = 0;

    for (LeafReaderContext leaf : _histReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);
        if (ndv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (ndv.advanceExact(doc)) {
                double value = _convertValueHist(ndv.longValue(), type);
                long bucketKey = _bucketKey(value, interval);
                buckets.merge(bucketKey, 1L, Long::sum);
                totalDocs++;
            }
        }
    }

    _printHistogramResult(field, null, buckets, interval, type, totalDocs, minDocCount);
}

// Histogram with query filter
void _histogramWithQuery(String field, double interval, String type, String queryStr, int minDocCount) throws Exception {
    _openHistIndex();

    Query query = _parseQueryHist(queryStr, field);
    IndexSearcher searcher = new IndexSearcher(_histReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    Map<Long, Long> buckets = new TreeMap<>();
    long totalDocs = 0;

    for (LeafReaderContext leaf : _histReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);
        if (ndv == null) continue;

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocIdSetIterator disi = scorer.iterator();
        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (ndv.advanceExact(doc)) {
                double value = _convertValueHist(ndv.longValue(), type);
                long bucketKey = _bucketKey(value, interval);
                buckets.merge(bucketKey, 1L, Long::sum);
                totalDocs++;
            }
        }
    }

    _printHistogramResult(field, "query: " + queryStr, buckets, interval, type, totalDocs, minDocCount);
}

// Histogram with numeric range filter
void _histogramWithRange(String field, double interval, String type,
                         String filterField, String filterType, double minVal, double maxVal,
                         int minDocCount) throws Exception {
    _openHistIndex();

    Map<Long, Long> buckets = new TreeMap<>();
    long totalDocs = 0;

    for (LeafReaderContext leaf : _histReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);
        NumericDocValues filterNdv = reader.getNumericDocValues(filterField);
        if (ndv == null || filterNdv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            // Check filter
            if (!filterNdv.advanceExact(doc)) continue;
            double filterVal = _convertValueHist(filterNdv.longValue(), filterType);
            if (filterVal < minVal || filterVal > maxVal) continue;

            if (ndv.advanceExact(doc)) {
                double value = _convertValueHist(ndv.longValue(), type);
                long bucketKey = _bucketKey(value, interval);
                buckets.merge(bucketKey, 1L, Long::sum);
                totalDocs++;
            }
        }
    }

    String rangeInfo = filterField + ":[" + minVal + " TO " + maxVal + "]";
    _printHistogramResult(field, rangeInfo, buckets, interval, type, totalDocs, minDocCount);
}

// Histogram with docId list
void _histogramWithDocs(String field, double interval, String type, int[] docIds, int minDocCount) throws Exception {
    _openHistIndex();

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);

    Map<Long, Long> buckets = new TreeMap<>();
    long totalDocs = 0;
    int docIdIndex = 0;

    for (LeafReaderContext leaf : _histReader.leaves()) {
        LeafReader reader = leaf.reader();
        int docBase = leaf.docBase;
        int maxDoc = reader.maxDoc();

        while (docIdIndex < sortedDocIds.length && sortedDocIds[docIdIndex] < docBase) {
            docIdIndex++;
        }
        if (docIdIndex >= sortedDocIds.length) break;

        NumericDocValues ndv = reader.getNumericDocValues(field);
        if (ndv == null) continue;

        while (docIdIndex < sortedDocIds.length) {
            int globalDocId = sortedDocIds[docIdIndex];
            if (globalDocId >= docBase + maxDoc) break;

            int localDocId = globalDocId - docBase;
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
                docIdIndex++;
                continue;
            }

            if (ndv.advanceExact(localDocId)) {
                double value = _convertValueHist(ndv.longValue(), type);
                long bucketKey = _bucketKey(value, interval);
                buckets.merge(bucketKey, 1L, Long::sum);
                totalDocs++;
            }
            docIdIndex++;
        }
    }

    _printHistogramResult(field, docIds.length + " docs", buckets, interval, type, totalDocs, minDocCount);
}

// ==================== Public API ====================

void histogram(String field, double interval) throws Exception {
    _histogramImpl(field, interval, "long", 1);
}

void histogram(String field, double interval, String type) throws Exception {
    _histogramImpl(field, interval, type, 1);
}

void histogram(String field, double interval, String type, int minDocCount) throws Exception {
    _histogramImpl(field, interval, type, minDocCount);
}

void histogramQuery(String field, double interval, String query) throws Exception {
    _histogramWithQuery(field, interval, "long", query, 1);
}

void histogramQuery(String field, double interval, String type, String query) throws Exception {
    _histogramWithQuery(field, interval, type, query, 1);
}

void histogramQuery(String field, double interval, String type, String query, int minDocCount) throws Exception {
    _histogramWithQuery(field, interval, type, query, minDocCount);
}

void histogramRange(String field, double interval, String type,
                    String filterField, String filterType, double min, double max) throws Exception {
    _histogramWithRange(field, interval, type, filterField, filterType, min, max, 1);
}

void histogramRange(String field, double interval, String type,
                    String filterField, String filterType, double min, double max, int minDocCount) throws Exception {
    _histogramWithRange(field, interval, type, filterField, filterType, min, max, minDocCount);
}

void histogramDocs(String field, double interval, int[] docIds) throws Exception {
    _histogramWithDocs(field, interval, "long", docIds, 1);
}

void histogramDocs(String field, double interval, String type, int[] docIds) throws Exception {
    _histogramWithDocs(field, interval, type, docIds, 1);
}

void histogramDocs(String field, double interval, String type, int[] docIds, int minDocCount) throws Exception {
    _histogramWithDocs(field, interval, type, docIds, minDocCount);
}

System.out.println("Loaded histogram-agg skill (OpenSearch-style bucket aggregation)");
System.out.println();
System.out.println("Usage:");
System.out.println("  histogram(\"field\", interval)              - long field");
System.out.println("  histogram(\"field\", interval, \"double\")    - double field");
System.out.println("  histogram(\"field\", interval, \"double\", 5) - minDocCount=5");
System.out.println();
System.out.println("With filters:");
System.out.println("  histogramQuery(\"field\", interval, \"type\", \"query\")");
System.out.println("  histogramRange(\"field\", interval, \"type\", \"filterField\", \"filterType\", min, max)");
System.out.println("  histogramDocs(\"field\", interval, \"type\", new int[]{...})");
System.out.println();
System.out.println("  closeHistIndex() - close when done");
