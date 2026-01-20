// weighted-avg-agg.jsh
// OpenSearch-style weighted average aggregation for Lucene indexes
// https://docs.opensearch.org/latest/aggregations/metric/weighted-avg/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/weighted-avg-agg.jsh
//   jshell> weightedAvg("price", "quantity", "double")

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
DirectoryReader _wavgReader = null;
Directory _wavgDir = null;

// Codec error detection helper
String _detectCodecErrorWavg(Exception e) {
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

void _openWavgIndex() throws Exception {
    if (_wavgReader != null) return;
    try {
        _wavgDir = FSDirectory.open(Path.of(indexPath));
        _wavgReader = DirectoryReader.open(_wavgDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _wavgReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorWavg(e);
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

void closeWavgIndex() throws Exception {
    if (_wavgReader != null) {
        _wavgReader.close();
        _wavgReader = null;
    }
    if (_wavgDir != null) {
        _wavgDir.close();
        _wavgDir = null;
    }
    System.out.println("Index closed.");
}

// Convert raw long value based on field type
double _convertValueWavg(long rawValue, String type) {
    if (type == null) type = "long";
    return switch (type.toLowerCase()) {
        case "double", "d", "float", "f" -> Double.longBitsToDouble(rawValue);
        case "int", "i", "long", "l" -> (double) rawValue;
        default -> (double) rawValue;
    };
}

// Parse query string to Lucene Query
Query _parseQueryWavg(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Core weighted avg computation for all docs
void _weightedAvgImpl(String valueField, String weightField, String valueType, String weightType) throws Exception {
    _openWavgIndex();

    if (valueType == null) valueType = "long";
    if (weightType == null) weightType = "long";

    double sumProduct = 0;  // sum(value * weight)
    double sumWeight = 0;   // sum(weight)
    long count = 0;

    for (LeafReaderContext leaf : _wavgReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues valueNdv = reader.getNumericDocValues(valueField);
        NumericDocValues weightNdv = reader.getNumericDocValues(weightField);

        if (valueNdv == null || weightNdv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            if (valueNdv.advanceExact(doc) && weightNdv.advanceExact(doc)) {
                double value = _convertValueWavg(valueNdv.longValue(), valueType);
                double weight = _convertValueWavg(weightNdv.longValue(), weightType);
                sumProduct += value * weight;
                sumWeight += weight;
                count++;
            }
        }
    }

    double weightedAvg = sumWeight > 0 ? sumProduct / sumWeight : 0;

    System.out.println();
    System.out.println("Weighted Avg Aggregation");
    System.out.println("=".repeat(40));
    System.out.println("Value Field:  " + valueField + " (" + valueType + ")");
    System.out.println("Weight Field: " + weightField + " (" + weightType + ")");
    System.out.println("Count:        " + count);
    System.out.println("-".repeat(40));
    System.out.println("Weighted Avg: " + weightedAvg);
    System.out.println("=".repeat(40));
}

// Core weighted avg computation with query filter
void _weightedAvgWithQuery(String valueField, String weightField, String valueType, String weightType, String queryStr) throws Exception {
    _openWavgIndex();

    if (valueType == null) valueType = "long";
    if (weightType == null) weightType = "long";

    Query query = _parseQueryWavg(queryStr, valueField);

    double sumProduct = 0;
    double sumWeight = 0;
    long count = 0;

    IndexSearcher searcher = new IndexSearcher(_wavgReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    for (LeafReaderContext leaf : _wavgReader.leaves()) {
        LeafReader reader = leaf.reader();
        NumericDocValues valueNdv = reader.getNumericDocValues(valueField);
        NumericDocValues weightNdv = reader.getNumericDocValues(weightField);

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocIdSetIterator disi = scorer.iterator();
        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            if (valueNdv != null && weightNdv != null &&
                valueNdv.advanceExact(doc) && weightNdv.advanceExact(doc)) {
                double value = _convertValueWavg(valueNdv.longValue(), valueType);
                double w = _convertValueWavg(weightNdv.longValue(), weightType);
                sumProduct += value * w;
                sumWeight += w;
                count++;
            }
        }
    }

    double weightedAvg = sumWeight > 0 ? sumProduct / sumWeight : 0;

    System.out.println();
    System.out.println("Weighted Avg Aggregation (query: " + queryStr + ")");
    System.out.println("=".repeat(40));
    System.out.println("Value Field:  " + valueField + " (" + valueType + ")");
    System.out.println("Weight Field: " + weightField + " (" + weightType + ")");
    System.out.println("Count:        " + count);
    System.out.println("-".repeat(40));
    System.out.println("Weighted Avg: " + weightedAvg);
    System.out.println("=".repeat(40));
}

// Core weighted avg computation with docid list
void _weightedAvgWithDocs(String valueField, String weightField, String valueType, String weightType, int[] docIds) throws Exception {
    _openWavgIndex();

    if (valueType == null) valueType = "long";
    if (weightType == null) weightType = "long";

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);

    double sumProduct = 0;
    double sumWeight = 0;
    long count = 0;
    int docIdIndex = 0;

    for (LeafReaderContext leaf : _wavgReader.leaves()) {
        LeafReader reader = leaf.reader();
        int docBase = leaf.docBase;
        int maxDoc = reader.maxDoc();

        while (docIdIndex < sortedDocIds.length && sortedDocIds[docIdIndex] < docBase) {
            docIdIndex++;
        }
        if (docIdIndex >= sortedDocIds.length) break;

        NumericDocValues valueNdv = reader.getNumericDocValues(valueField);
        NumericDocValues weightNdv = reader.getNumericDocValues(weightField);

        while (docIdIndex < sortedDocIds.length) {
            int globalDocId = sortedDocIds[docIdIndex];
            if (globalDocId >= docBase + maxDoc) break;

            int localDocId = globalDocId - docBase;
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
                docIdIndex++;
                continue;
            }

            if (valueNdv != null && weightNdv != null &&
                valueNdv.advanceExact(localDocId) && weightNdv.advanceExact(localDocId)) {
                double value = _convertValueWavg(valueNdv.longValue(), valueType);
                double w = _convertValueWavg(weightNdv.longValue(), weightType);
                sumProduct += value * w;
                sumWeight += w;
                count++;
            }
            docIdIndex++;
        }
    }

    double weightedAvg = sumWeight > 0 ? sumProduct / sumWeight : 0;

    System.out.println();
    System.out.println("Weighted Avg Aggregation (" + docIds.length + " docs)");
    System.out.println("=".repeat(40));
    System.out.println("Value Field:  " + valueField + " (" + valueType + ")");
    System.out.println("Weight Field: " + weightField + " (" + weightType + ")");
    System.out.println("Count:        " + count);
    System.out.println("-".repeat(40));
    System.out.println("Weighted Avg: " + weightedAvg);
    System.out.println("=".repeat(40));
}

// === Public API ===

void weightedAvg(String valueField, String weightField) throws Exception {
    _weightedAvgImpl(valueField, weightField, "long", "long");
}

void weightedAvg(String valueField, String weightField, String type) throws Exception {
    _weightedAvgImpl(valueField, weightField, type, type);
}

void weightedAvg(String valueField, String weightField, String valueType, String weightType) throws Exception {
    _weightedAvgImpl(valueField, weightField, valueType, weightType);
}

void weightedAvgQuery(String valueField, String weightField, String type, String query) throws Exception {
    _weightedAvgWithQuery(valueField, weightField, type, type, query);
}

void weightedAvgQuery(String valueField, String weightField, String valueType, String weightType, String query) throws Exception {
    _weightedAvgWithQuery(valueField, weightField, valueType, weightType, query);
}

void weightedAvgDocs(String valueField, String weightField, String type, int[] docIds) throws Exception {
    _weightedAvgWithDocs(valueField, weightField, type, type, docIds);
}

void weightedAvgDocs(String valueField, String weightField, String valueType, String weightType, int[] docIds) throws Exception {
    _weightedAvgWithDocs(valueField, weightField, valueType, weightType, docIds);
}

System.out.println("Loaded weighted-avg-agg skill (OpenSearch-style weighted average)");
System.out.println();
System.out.println("Usage:");
System.out.println("  weightedAvg(\"valueField\", \"weightField\")");
System.out.println("  weightedAvg(\"valueField\", \"weightField\", \"double\")");
System.out.println("  weightedAvg(\"valueField\", \"weightField\", \"double\", \"long\")");
System.out.println();
System.out.println("With filters:");
System.out.println("  weightedAvgQuery(\"value\", \"weight\", \"double\", \"query\")");
System.out.println("  weightedAvgDocs(\"value\", \"weight\", \"double\", new int[]{...})");
System.out.println();
System.out.println("  closeWavgIndex() - close when done");
