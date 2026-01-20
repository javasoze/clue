// range-agg.jsh
// OpenSearch-style range bucket aggregation for Lucene indexes
// Buckets numeric values by custom ranges
// https://docs.opensearch.org/latest/aggregations/bucket/range/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/range-agg.jsh
//   jshell> range("price", "double", new double[]{0, 5000, 10000, 20000})

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
DirectoryReader _rangeReader = null;
Directory _rangeDir = null;

// Codec error detection helper
String _detectCodecErrorRange(Exception e) {
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

void _openRangeIndex() throws Exception {
    if (_rangeReader != null) return;
    try {
        _rangeDir = FSDirectory.open(Path.of(indexPath));
        _rangeReader = DirectoryReader.open(_rangeDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _rangeReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorRange(e);
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

void closeRangeIndex() throws Exception {
    if (_rangeReader != null) {
        _rangeReader.close();
        _rangeReader = null;
    }
    if (_rangeDir != null) {
        _rangeDir.close();
        _rangeDir = null;
    }
    System.out.println("Index closed.");
}

// Parse query string to Lucene Query
Query _parseQueryRange(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Convert raw long value based on field type
double _convertValueRange(long rawValue, String type) {
    if (type == null) type = "long";
    return switch (type.toLowerCase()) {
        case "double", "d", "float", "f" -> Double.longBitsToDouble(rawValue);
        case "int", "i", "long", "l" -> (double) rawValue;
        default -> (double) rawValue;
    };
}

// Format number with commas
String _formatNumRange(long n) {
    return NumberFormat.getInstance().format(n);
}

// Find which bucket a value belongs to
int _findBucket(double value, double[] ranges) {
    for (int i = 0; i < ranges.length - 1; i++) {
        if (value >= ranges[i] && value < ranges[i + 1]) {
            return i;
        }
    }
    // Last bucket: >= last range value
    if (value >= ranges[ranges.length - 1]) {
        return ranges.length - 1;
    }
    return -1;  // Below first range
}

// Print range results
void _printRangeResult(String field, String filterInfo, long[] buckets, double[] ranges,
                       String[] names, String type, long totalDocs) {

    long displayedSum = Arrays.stream(buckets).sum();

    System.out.println();
    if (filterInfo != null) {
        System.out.println("Range Aggregation (" + filterInfo + "): " + field);
    } else {
        System.out.println("Range Aggregation: " + field);
    }
    System.out.println("=".repeat(55));
    System.out.println("Field:   " + field + " (" + type + ")");
    System.out.println("Docs:    " + _formatNumRange(totalDocs));
    System.out.println("Buckets: " + buckets.length);
    System.out.println("-".repeat(55));

    for (int i = 0; i < buckets.length; i++) {
        long count = buckets[i];
        double pct = totalDocs > 0 ? (count * 100.0 / totalDocs) : 0;

        String rangeStr;
        String name = (names != null && i < names.length) ? names[i] : null;

        if (i < ranges.length - 1) {
            if (type.toLowerCase().startsWith("d") || type.toLowerCase().startsWith("f")) {
                rangeStr = String.format("[%.1f - %.1f)", ranges[i], ranges[i + 1]);
            } else {
                rangeStr = String.format("[%d - %d)", (long) ranges[i], (long) ranges[i + 1]);
            }
        } else {
            // Last bucket
            if (type.toLowerCase().startsWith("d") || type.toLowerCase().startsWith("f")) {
                rangeStr = String.format("[%.1f - *)", ranges[i]);
            } else {
                rangeStr = String.format("[%d - *)", (long) ranges[i]);
            }
        }

        if (name != null) {
            System.out.printf("  %-12s %-18s  %,10d docs (%5.1f%%)%n", name, rangeStr, count, pct);
        } else {
            System.out.printf("  %-18s  %,10d docs (%5.1f%%)%n", rangeStr, count, pct);
        }
    }

    System.out.println("=".repeat(55));
    System.out.println("Total:   " + _formatNumRange(displayedSum) + " docs");
    System.out.println("=".repeat(55));
}

// ==================== Core Implementation ====================

// Range aggregation for all documents
void _rangeImpl(String field, String type, double[] ranges, String[] names) throws Exception {
    _openRangeIndex();

    // buckets.length = ranges.length (last bucket is >= last range)
    long[] buckets = new long[ranges.length];
    long totalDocs = 0;

    for (LeafReaderContext leaf : _rangeReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);
        if (ndv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (ndv.advanceExact(doc)) {
                double value = _convertValueRange(ndv.longValue(), type);
                int bucketIdx = _findBucket(value, ranges);
                if (bucketIdx >= 0) {
                    buckets[bucketIdx]++;
                    totalDocs++;
                }
            }
        }
    }

    _printRangeResult(field, null, buckets, ranges, names, type, totalDocs);
}

// Range aggregation with query filter
void _rangeWithQuery(String field, String type, double[] ranges, String[] names, String queryStr) throws Exception {
    _openRangeIndex();

    Query query = _parseQueryRange(queryStr, field);
    IndexSearcher searcher = new IndexSearcher(_rangeReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    long[] buckets = new long[ranges.length];
    long totalDocs = 0;

    for (LeafReaderContext leaf : _rangeReader.leaves()) {
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
                double value = _convertValueRange(ndv.longValue(), type);
                int bucketIdx = _findBucket(value, ranges);
                if (bucketIdx >= 0) {
                    buckets[bucketIdx]++;
                    totalDocs++;
                }
            }
        }
    }

    _printRangeResult(field, "query: " + queryStr, buckets, ranges, names, type, totalDocs);
}

// Range aggregation with numeric filter
void _rangeWithNumericFilter(String field, String type, double[] ranges, String[] names,
                              String filterField, String filterType, double minVal, double maxVal) throws Exception {
    _openRangeIndex();

    long[] buckets = new long[ranges.length];
    long totalDocs = 0;

    for (LeafReaderContext leaf : _rangeReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);
        NumericDocValues filterNdv = reader.getNumericDocValues(filterField);
        if (ndv == null || filterNdv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            // Check filter
            if (!filterNdv.advanceExact(doc)) continue;
            double filterVal = _convertValueRange(filterNdv.longValue(), filterType);
            if (filterVal < minVal || filterVal > maxVal) continue;

            if (ndv.advanceExact(doc)) {
                double value = _convertValueRange(ndv.longValue(), type);
                int bucketIdx = _findBucket(value, ranges);
                if (bucketIdx >= 0) {
                    buckets[bucketIdx]++;
                    totalDocs++;
                }
            }
        }
    }

    String rangeInfo = filterField + ":[" + minVal + " TO " + maxVal + "]";
    _printRangeResult(field, rangeInfo, buckets, ranges, names, type, totalDocs);
}

// Range aggregation with docId list
void _rangeWithDocs(String field, String type, double[] ranges, String[] names, int[] docIds) throws Exception {
    _openRangeIndex();

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);

    long[] buckets = new long[ranges.length];
    long totalDocs = 0;
    int docIdIndex = 0;

    for (LeafReaderContext leaf : _rangeReader.leaves()) {
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
                double value = _convertValueRange(ndv.longValue(), type);
                int bucketIdx = _findBucket(value, ranges);
                if (bucketIdx >= 0) {
                    buckets[bucketIdx]++;
                    totalDocs++;
                }
            }
            docIdIndex++;
        }
    }

    _printRangeResult(field, docIds.length + " docs", buckets, ranges, names, type, totalDocs);
}

// ==================== Public API ====================

void range(String field, double[] ranges) throws Exception {
    _rangeImpl(field, "long", ranges, null);
}

void range(String field, String type, double[] ranges) throws Exception {
    _rangeImpl(field, type, ranges, null);
}

void rangeNamed(String field, String type, String[] names, double[] ranges) throws Exception {
    _rangeImpl(field, type, ranges, names);
}

void rangeQuery(String field, String type, double[] ranges, String query) throws Exception {
    _rangeWithQuery(field, type, ranges, null, query);
}

void rangeQueryNamed(String field, String type, String[] names, double[] ranges, String query) throws Exception {
    _rangeWithQuery(field, type, ranges, names, query);
}

void rangeFilter(String field, String type, double[] ranges,
                 String filterField, String filterType, double min, double max) throws Exception {
    _rangeWithNumericFilter(field, type, ranges, null, filterField, filterType, min, max);
}

void rangeDocs(String field, String type, double[] ranges, int[] docIds) throws Exception {
    _rangeWithDocs(field, type, ranges, null, docIds);
}

void rangeDocsNamed(String field, String type, String[] names, double[] ranges, int[] docIds) throws Exception {
    _rangeWithDocs(field, type, ranges, names, docIds);
}

System.out.println("Loaded range-agg skill (OpenSearch-style bucket aggregation)");
System.out.println();
System.out.println("Usage:");
System.out.println("  range(\"field\", new double[]{0, 5000, 10000})     - long field");
System.out.println("  range(\"field\", \"double\", new double[]{...})     - double field");
System.out.println("  rangeNamed(\"field\", \"double\", new String[]{\"low\", \"mid\", \"high\"}, new double[]{0, 5000, 10000})");
System.out.println();
System.out.println("With filters:");
System.out.println("  rangeQuery(\"field\", \"type\", ranges, \"query\")");
System.out.println("  rangeFilter(\"field\", \"type\", ranges, \"filterField\", \"filterType\", min, max)");
System.out.println("  rangeDocs(\"field\", \"type\", ranges, new int[]{...})");
System.out.println();
System.out.println("  closeRangeIndex() - close when done");
