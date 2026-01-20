// percentile-agg.jsh
// OpenSearch-style percentile aggregations for Lucene indexes
// Uses T-Digest algorithm for approximate percentile calculation
// https://docs.opensearch.org/latest/aggregations/metric/percentile/
//
// Requires t-digest library on classpath:
//   jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/percentile-agg.jsh
//   jshell> percentiles("price", "double", new double[]{25, 50, 75, 95, 99})

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import com.tdunning.math.stats.TDigest;
import com.tdunning.math.stats.MergingDigest;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cached reader
DirectoryReader _pctReader = null;
Directory _pctDir = null;

// Codec error detection helper
String _detectCodecErrorPct(Exception e) {
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

void _openPctIndex() throws Exception {
    if (_pctReader != null) return;
    try {
        _pctDir = FSDirectory.open(Path.of(indexPath));
        _pctReader = DirectoryReader.open(_pctDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _pctReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorPct(e);
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

void closePctIndex() throws Exception {
    if (_pctReader != null) {
        _pctReader.close();
        _pctReader = null;
    }
    if (_pctDir != null) {
        _pctDir.close();
        _pctDir = null;
    }
    System.out.println("Index closed.");
}

// Convert raw long value based on field type
double _convertValuePct(long rawValue, String type) {
    if (type == null) type = "long";
    return switch (type.toLowerCase()) {
        case "double", "d", "float", "f" -> Double.longBitsToDouble(rawValue);
        case "int", "i", "long", "l" -> (double) rawValue;
        default -> (double) rawValue;
    };
}

// Parse query string to Lucene Query
Query _parseQueryPct(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Build TDigest from all docs
TDigest _buildDigest(String field, String type, double compression) throws Exception {
    _openPctIndex();
    if (type == null) type = "long";

    TDigest digest = new MergingDigest(compression);

    for (LeafReaderContext leaf : _pctReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);
        if (ndv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (ndv.advanceExact(doc)) {
                double value = _convertValuePct(ndv.longValue(), type);
                digest.add(value);
            }
        }
    }

    return digest;
}

// Build TDigest with query filter
TDigest _buildDigestWithQuery(String field, String type, double compression, String queryStr) throws Exception {
    _openPctIndex();
    if (type == null) type = "long";

    TDigest digest = new MergingDigest(compression);
    Query query = _parseQueryPct(queryStr, field);

    IndexSearcher searcher = new IndexSearcher(_pctReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    for (LeafReaderContext leaf : _pctReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocIdSetIterator disi = scorer.iterator();
        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (ndv != null && ndv.advanceExact(doc)) {
                double value = _convertValuePct(ndv.longValue(), type);
                digest.add(value);
            }
        }
    }

    return digest;
}

// Build TDigest with docid list
TDigest _buildDigestWithDocs(String field, String type, double compression, int[] docIds) throws Exception {
    _openPctIndex();
    if (type == null) type = "long";

    TDigest digest = new MergingDigest(compression);

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);
    int docIdIndex = 0;

    for (LeafReaderContext leaf : _pctReader.leaves()) {
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
                double value = _convertValuePct(ndv.longValue(), type);
                digest.add(value);
            }
            docIdIndex++;
        }
    }

    return digest;
}

// ==================== Percentiles ====================

void _printPercentiles(String field, String type, TDigest digest, double[] pcts, String filter) {
    System.out.println();
    System.out.println("Percentiles Aggregation" + (filter != null ? " (" + filter + ")" : "") + ": " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Type:         " + (type != null ? type : "long"));
    System.out.println("Count:        " + (long) digest.size());
    System.out.println("-".repeat(40));
    for (double p : pcts) {
        double value = digest.quantile(p / 100.0);
        System.out.printf("%6.1fth:     %.4f%n", p, value);
    }
    System.out.println("=".repeat(40));
}

void percentiles(String field, String type, double[] pcts) throws Exception {
    TDigest digest = _buildDigest(field, type, 100);
    _printPercentiles(field, type, digest, pcts, null);
}

void percentiles(String field, double[] pcts) throws Exception {
    percentiles(field, "long", pcts);
}

void percentilesQuery(String field, String type, double[] pcts, String query) throws Exception {
    TDigest digest = _buildDigestWithQuery(field, type, 100, query);
    _printPercentiles(field, type, digest, pcts, "query: " + query);
}

void percentilesDocs(String field, String type, double[] pcts, int[] docIds) throws Exception {
    TDigest digest = _buildDigestWithDocs(field, type, 100, docIds);
    _printPercentiles(field, type, digest, pcts, docIds.length + " docs");
}

// ==================== Percentile Ranks ====================

void _printPercentileRanks(String field, String type, TDigest digest, double[] values, String filter) {
    System.out.println();
    System.out.println("Percentile Ranks Aggregation" + (filter != null ? " (" + filter + ")" : "") + ": " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Type:         " + (type != null ? type : "long"));
    System.out.println("Count:        " + (long) digest.size());
    System.out.println("-".repeat(40));
    for (double v : values) {
        double rank = digest.cdf(v) * 100;
        System.out.printf("%.2f is at   %.2fth percentile%n", v, rank);
    }
    System.out.println("=".repeat(40));
}

void percentileRanks(String field, String type, double[] values) throws Exception {
    TDigest digest = _buildDigest(field, type, 100);
    _printPercentileRanks(field, type, digest, values, null);
}

void percentileRanks(String field, double[] values) throws Exception {
    percentileRanks(field, "long", values);
}

void percentileRanksQuery(String field, String type, double[] values, String query) throws Exception {
    TDigest digest = _buildDigestWithQuery(field, type, 100, query);
    _printPercentileRanks(field, type, digest, values, "query: " + query);
}

void percentileRanksDocs(String field, String type, double[] values, int[] docIds) throws Exception {
    TDigest digest = _buildDigestWithDocs(field, type, 100, docIds);
    _printPercentileRanks(field, type, digest, values, docIds.length + " docs");
}

// ==================== Median Absolute Deviation ====================

void _madImpl(String field, String type, String filter, TDigest digest) throws Exception {
    // MAD = median(|x - median(x)|)
    double median = digest.quantile(0.5);

    // Second pass: collect deviations
    TDigest madDigest = new MergingDigest(100);

    if (filter == null) {
        // All docs
        for (LeafReaderContext leaf : _pctReader.leaves()) {
            LeafReader reader = leaf.reader();
            NumericDocValues ndv = reader.getNumericDocValues(field);
            if (ndv == null) continue;

            int maxDoc = reader.maxDoc();
            for (int doc = 0; doc < maxDoc; doc++) {
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                if (ndv.advanceExact(doc)) {
                    double value = _convertValuePct(ndv.longValue(), type);
                    madDigest.add(Math.abs(value - median));
                }
            }
        }
    }

    double mad = madDigest.quantile(0.5);

    System.out.println();
    System.out.println("Median Absolute Deviation" + (filter != null ? " (" + filter + ")" : "") + ": " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Type:         " + (type != null ? type : "long"));
    System.out.println("Count:        " + (long) digest.size());
    System.out.println("-".repeat(40));
    System.out.println("Median:       " + median);
    System.out.println("MAD:          " + mad);
    System.out.println("=".repeat(40));
}

void medianAbsoluteDeviation(String field) throws Exception {
    TDigest digest = _buildDigest(field, "long", 100);
    _madImpl(field, "long", null, digest);
}

void medianAbsoluteDeviation(String field, String type) throws Exception {
    TDigest digest = _buildDigest(field, type, 100);
    _madImpl(field, type, null, digest);
}

void madQuery(String field, String type, String query) throws Exception {
    TDigest digest = _buildDigestWithQuery(field, type, 100, query);
    // For MAD with query, we need to re-iterate with query filter for deviations
    // Simplified: just use the same digest for median calculation
    double median = digest.quantile(0.5);

    // Build deviation digest with same query
    Query q = _parseQueryPct(query, field);
    IndexSearcher searcher = new IndexSearcher(_pctReader);
    Weight weight = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    TDigest madDigest = new MergingDigest(100);

    for (LeafReaderContext leaf : _pctReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues ndv = reader.getNumericDocValues(field);

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocIdSetIterator disi = scorer.iterator();
        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (ndv != null && ndv.advanceExact(doc)) {
                double value = _convertValuePct(ndv.longValue(), type);
                madDigest.add(Math.abs(value - median));
            }
        }
    }

    double mad = madDigest.quantile(0.5);

    System.out.println();
    System.out.println("Median Absolute Deviation (query: " + query + "): " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Type:         " + type);
    System.out.println("Count:        " + (long) digest.size());
    System.out.println("-".repeat(40));
    System.out.println("Median:       " + median);
    System.out.println("MAD:          " + mad);
    System.out.println("=".repeat(40));
}

void madDocs(String field, String type, int[] docIds) throws Exception {
    TDigest digest = _buildDigestWithDocs(field, type, 100, docIds);
    double median = digest.quantile(0.5);

    // Second pass for deviations with same docid list
    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);
    int docIdIndex = 0;

    TDigest madDigest = new MergingDigest(100);

    for (LeafReaderContext leaf : _pctReader.leaves()) {
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
                double value = _convertValuePct(ndv.longValue(), type);
                madDigest.add(Math.abs(value - median));
            }
            docIdIndex++;
        }
    }

    double mad = madDigest.quantile(0.5);

    System.out.println();
    System.out.println("Median Absolute Deviation (" + docIds.length + " docs): " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Type:         " + type);
    System.out.println("Count:        " + (long) digest.size());
    System.out.println("-".repeat(40));
    System.out.println("Median:       " + median);
    System.out.println("MAD:          " + mad);
    System.out.println("=".repeat(40));
}

System.out.println("Loaded percentile-agg skill (T-Digest percentile aggregations)");
System.out.println();
System.out.println("Percentiles (values at given percentile ranks):");
System.out.println("  percentiles(\"field\", \"double\", new double[]{25, 50, 75, 95, 99})");
System.out.println("  percentilesQuery(\"field\", \"double\", new double[]{50}, \"query\")");
System.out.println("  percentilesDocs(\"field\", \"double\", new double[]{50}, new int[]{...})");
System.out.println();
System.out.println("Percentile Ranks (what percentile is a value at):");
System.out.println("  percentileRanks(\"field\", \"double\", new double[]{5000, 10000})");
System.out.println("  percentileRanksQuery(\"field\", \"double\", new double[]{10000}, \"query\")");
System.out.println("  percentileRanksDocs(\"field\", \"double\", new double[]{10000}, new int[]{...})");
System.out.println();
System.out.println("Median Absolute Deviation:");
System.out.println("  medianAbsoluteDeviation(\"field\", \"double\")");
System.out.println("  madQuery(\"field\", \"double\", \"query\")");
System.out.println("  madDocs(\"field\", \"double\", new int[]{...})");
System.out.println();
System.out.println("  closePctIndex() - close when done");
