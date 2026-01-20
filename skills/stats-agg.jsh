// stats-agg.jsh
// OpenSearch-style stats aggregations for Lucene indexes
// https://docs.opensearch.org/latest/aggregations/metric/stats/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/stats-agg.jsh
//   jshell> stats("price", "double")
//   jshell> extendedStats("price", "double")
//   jshell> min("price", "double")
//   jshell> max("price", "double")

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cached reader
DirectoryReader _statsReader = null;
Directory _statsDir = null;

// Codec error detection helper
String _detectCodecErrorStats(Exception e) {
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

void _openStatsIndex() throws Exception {
    if (_statsReader != null) return;
    try {
        _statsDir = FSDirectory.open(Path.of(indexPath));
        _statsReader = DirectoryReader.open(_statsDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _statsReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorStats(e);
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

void closeStatsIndex() throws Exception {
    if (_statsReader != null) {
        _statsReader.close();
        _statsReader = null;
    }
    if (_statsDir != null) {
        _statsDir.close();
        _statsDir = null;
    }
    System.out.println("Index closed.");
}

// Check if field exists and is numeric
boolean _isNumericFieldStats(String field) throws Exception {
    for (LeafReaderContext leaf : _statsReader.leaves()) {
        FieldInfo finfo = leaf.reader().getFieldInfos().fieldInfo(field);
        if (finfo != null) {
            DocValuesType dvType = finfo.getDocValuesType();
            return dvType == DocValuesType.NUMERIC || dvType == DocValuesType.SORTED_NUMERIC;
        }
    }
    return false;
}

// Convert raw long value based on field type
double _convertValueStats(long rawValue, String type) {
    if (type == null) type = "long";
    return switch (type.toLowerCase()) {
        case "double", "d", "float", "f" -> Double.longBitsToDouble(rawValue);
        case "int", "i", "long", "l" -> (double) rawValue;
        default -> (double) rawValue;
    };
}

// Parse query string to Lucene Query
Query _parseQueryStats(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Stats result holder
record StatsResult(long count, double min, double max, double sum, double avg,
                   double variance, double stdDev, double sumOfSquares) {}

// Core stats computation for all docs
StatsResult _computeStats(String field, String type) throws Exception {
    _openStatsIndex();
    if (type == null) type = "long";

    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    double sum = 0;
    double mean = 0;
    double M2 = 0;  // For Welford's variance
    long count = 0;

    for (LeafReaderContext leaf : _statsReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);
        if (ndv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            if (ndv.advanceExact(doc)) {
                double value = _convertValueStats(ndv.longValue(), type);
                if (value < min) min = value;
                if (value > max) max = value;
                sum += value;
                count++;
                // Welford's online variance algorithm
                double delta = value - mean;
                mean += delta / count;
                double delta2 = value - mean;
                M2 += delta * delta2;
            }
        }
    }

    if (count == 0) {
        return new StatsResult(0, 0, 0, 0, 0, 0, 0, 0);
    }

    double avg = sum / count;
    double variance = M2 / count;
    double stdDev = Math.sqrt(variance);
    double sumOfSquares = M2 + count * mean * mean;

    return new StatsResult(count, min, max, sum, avg, variance, stdDev, sumOfSquares);
}

// Core stats computation with query filter
StatsResult _computeStatsWithQuery(String field, String type, String queryStr) throws Exception {
    _openStatsIndex();
    if (type == null) type = "long";

    Query query = _parseQueryStats(queryStr, field);

    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    double sum = 0;
    double mean = 0;
    double M2 = 0;
    long count = 0;

    IndexSearcher searcher = new IndexSearcher(_statsReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    for (LeafReaderContext leaf : _statsReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocIdSetIterator disi = scorer.iterator();
        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            if (ndv != null && ndv.advanceExact(doc)) {
                double value = _convertValueStats(ndv.longValue(), type);
                if (value < min) min = value;
                if (value > max) max = value;
                sum += value;
                count++;
                double delta = value - mean;
                mean += delta / count;
                double delta2 = value - mean;
                M2 += delta * delta2;
            }
        }
    }

    if (count == 0) {
        return new StatsResult(0, 0, 0, 0, 0, 0, 0, 0);
    }

    double avg = sum / count;
    double variance = M2 / count;
    double stdDev = Math.sqrt(variance);
    double sumOfSquares = M2 + count * mean * mean;

    return new StatsResult(count, min, max, sum, avg, variance, stdDev, sumOfSquares);
}

// Core stats computation with docid list
StatsResult _computeStatsWithDocs(String field, String type, int[] docIds) throws Exception {
    _openStatsIndex();
    if (type == null) type = "long";

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);

    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    double sum = 0;
    double mean = 0;
    double M2 = 0;
    long count = 0;
    int docIdIndex = 0;

    for (LeafReaderContext leaf : _statsReader.leaves()) {
        LeafReader reader = leaf.reader();
        int docBase = leaf.docBase;
        int maxDoc = reader.maxDoc();

        while (docIdIndex < sortedDocIds.length && sortedDocIds[docIdIndex] < docBase) {
            docIdIndex++;
        }
        if (docIdIndex >= sortedDocIds.length) break;

        NumericDocValues ndv = reader.getNumericDocValues(field);

        while (docIdIndex < sortedDocIds.length) {
            int globalDocId = sortedDocIds[docIdIndex];
            if (globalDocId >= docBase + maxDoc) break;

            int localDocId = globalDocId - docBase;
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
                docIdIndex++;
                continue;
            }

            if (ndv != null && ndv.advanceExact(localDocId)) {
                double value = _convertValueStats(ndv.longValue(), type);
                if (value < min) min = value;
                if (value > max) max = value;
                sum += value;
                count++;
                double delta = value - mean;
                mean += delta / count;
                double delta2 = value - mean;
                M2 += delta * delta2;
            }
            docIdIndex++;
        }
    }

    if (count == 0) {
        return new StatsResult(0, 0, 0, 0, 0, 0, 0, 0);
    }

    double avg = sum / count;
    double variance = M2 / count;
    double stdDev = Math.sqrt(variance);
    double sumOfSquares = M2 + count * mean * mean;

    return new StatsResult(count, min, max, sum, avg, variance, stdDev, sumOfSquares);
}

// Output helpers
void _printStats(String field, String type, StatsResult r, String filter) {
    System.out.println();
    System.out.println("Stats Aggregation" + (filter != null ? " (" + filter + ")" : "") + ": " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Type:         " + (type != null ? type : "long"));
    System.out.println("Count:        " + r.count());
    System.out.println("-".repeat(40));
    System.out.println("Min:          " + r.min());
    System.out.println("Max:          " + r.max());
    System.out.println("Sum:          " + r.sum());
    System.out.println("Avg:          " + r.avg());
    System.out.println("=".repeat(40));
}

void _printExtendedStats(String field, String type, StatsResult r, String filter) {
    System.out.println();
    System.out.println("Extended Stats Aggregation" + (filter != null ? " (" + filter + ")" : "") + ": " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Type:         " + (type != null ? type : "long"));
    System.out.println("Count:        " + r.count());
    System.out.println("-".repeat(40));
    System.out.println("Min:          " + r.min());
    System.out.println("Max:          " + r.max());
    System.out.println("Sum:          " + r.sum());
    System.out.println("Avg:          " + r.avg());
    System.out.println("-".repeat(40));
    System.out.println("Variance:     " + r.variance());
    System.out.println("Std Dev:      " + r.stdDev());
    System.out.println("Sum of Sq:    " + r.sumOfSquares());
    System.out.println("=".repeat(40));
}

// === Public API: min ===

void min(String field) throws Exception {
    var r = _computeStats(field, "long");
    System.out.println("\nMin(" + field + "): " + r.min() + "  (count: " + r.count() + ")");
}

void min(String field, String type) throws Exception {
    var r = _computeStats(field, type);
    System.out.println("\nMin(" + field + "): " + r.min() + "  (count: " + r.count() + ")");
}

void minQuery(String field, String type, String query) throws Exception {
    var r = _computeStatsWithQuery(field, type, query);
    System.out.println("\nMin(" + field + ") [" + query + "]: " + r.min() + "  (count: " + r.count() + ")");
}

void minDocs(String field, String type, int[] docIds) throws Exception {
    var r = _computeStatsWithDocs(field, type, docIds);
    System.out.println("\nMin(" + field + ") [" + docIds.length + " docs]: " + r.min() + "  (count: " + r.count() + ")");
}

// === Public API: max ===

void max(String field) throws Exception {
    var r = _computeStats(field, "long");
    System.out.println("\nMax(" + field + "): " + r.max() + "  (count: " + r.count() + ")");
}

void max(String field, String type) throws Exception {
    var r = _computeStats(field, type);
    System.out.println("\nMax(" + field + "): " + r.max() + "  (count: " + r.count() + ")");
}

void maxQuery(String field, String type, String query) throws Exception {
    var r = _computeStatsWithQuery(field, type, query);
    System.out.println("\nMax(" + field + ") [" + query + "]: " + r.max() + "  (count: " + r.count() + ")");
}

void maxDocs(String field, String type, int[] docIds) throws Exception {
    var r = _computeStatsWithDocs(field, type, docIds);
    System.out.println("\nMax(" + field + ") [" + docIds.length + " docs]: " + r.max() + "  (count: " + r.count() + ")");
}

// === Public API: valueCount ===

void valueCount(String field) throws Exception {
    var r = _computeStats(field, "long");
    System.out.println("\nValue Count(" + field + "): " + r.count());
}

void valueCountQuery(String field, String query) throws Exception {
    var r = _computeStatsWithQuery(field, "long", query);
    System.out.println("\nValue Count(" + field + ") [" + query + "]: " + r.count());
}

void valueCountDocs(String field, int[] docIds) throws Exception {
    var r = _computeStatsWithDocs(field, "long", docIds);
    System.out.println("\nValue Count(" + field + ") [" + docIds.length + " docs]: " + r.count());
}

// === Public API: stats ===

void stats(String field) throws Exception {
    var r = _computeStats(field, "long");
    _printStats(field, "long", r, null);
}

void stats(String field, String type) throws Exception {
    var r = _computeStats(field, type);
    _printStats(field, type, r, null);
}

void statsQuery(String field, String type, String query) throws Exception {
    var r = _computeStatsWithQuery(field, type, query);
    _printStats(field, type, r, "query: " + query);
}

void statsDocs(String field, String type, int[] docIds) throws Exception {
    var r = _computeStatsWithDocs(field, type, docIds);
    _printStats(field, type, r, docIds.length + " docs");
}

// === Public API: extendedStats ===

void extendedStats(String field) throws Exception {
    var r = _computeStats(field, "long");
    _printExtendedStats(field, "long", r, null);
}

void extendedStats(String field, String type) throws Exception {
    var r = _computeStats(field, type);
    _printExtendedStats(field, type, r, null);
}

void extendedStatsQuery(String field, String type, String query) throws Exception {
    var r = _computeStatsWithQuery(field, type, query);
    _printExtendedStats(field, type, r, "query: " + query);
}

void extendedStatsDocs(String field, String type, int[] docIds) throws Exception {
    var r = _computeStatsWithDocs(field, type, docIds);
    _printExtendedStats(field, type, r, docIds.length + " docs");
}

System.out.println("Loaded stats-agg skill (OpenSearch-style stats aggregations)");
System.out.println();
System.out.println("Functions:");
System.out.println("  min(\"field\", \"double\")              - minimum value");
System.out.println("  max(\"field\", \"double\")              - maximum value");
System.out.println("  valueCount(\"field\")                 - count of values");
System.out.println("  stats(\"field\", \"double\")            - count, min, max, avg, sum");
System.out.println("  extendedStats(\"field\", \"double\")    - stats + variance, stdDev");
System.out.println();
System.out.println("With filters:");
System.out.println("  minQuery(\"field\", \"double\", \"query\")");
System.out.println("  statsQuery(\"field\", \"double\", \"query\")");
System.out.println("  statsDocs(\"field\", \"double\", new int[]{...})");
System.out.println();
System.out.println("  closeStatsIndex() - close when done");
