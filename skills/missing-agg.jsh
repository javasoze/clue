// missing-agg.jsh
// OpenSearch-style missing bucket aggregation for Lucene indexes
// Counts documents missing a field value
// https://docs.opensearch.org/latest/aggregations/bucket/missing/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/missing-agg.jsh
//   jshell> missing("color")

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
import java.text.NumberFormat;

// Cached reader
DirectoryReader _missReader = null;
Directory _missDir = null;

// Codec error detection helper
String _detectCodecErrorMiss(Exception e) {
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

void _openMissIndex() throws Exception {
    if (_missReader != null) return;
    try {
        _missDir = FSDirectory.open(Path.of(indexPath));
        _missReader = DirectoryReader.open(_missDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _missReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorMiss(e);
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

void closeMissIndex() throws Exception {
    if (_missReader != null) {
        _missReader.close();
        _missReader = null;
    }
    if (_missDir != null) {
        _missDir.close();
        _missDir = null;
    }
    System.out.println("Index closed.");
}

// Parse query string to Lucene Query
Query _parseQueryMiss(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Format number with commas
String _formatNumMiss(long n) {
    return NumberFormat.getInstance().format(n);
}

// Check if doc has value for any doc values type
boolean _hasValue(LeafReader reader, String field, int doc) throws Exception {
    FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
    if (finfo == null) return false;

    DocValuesType dvType = finfo.getDocValuesType();

    switch (dvType) {
        case NUMERIC:
            NumericDocValues ndv = reader.getNumericDocValues(field);
            return ndv != null && ndv.advanceExact(doc);

        case SORTED:
            SortedDocValues sdv = reader.getSortedDocValues(field);
            return sdv != null && sdv.advanceExact(doc);

        case SORTED_SET:
            SortedSetDocValues ssdv = reader.getSortedSetDocValues(field);
            return ssdv != null && ssdv.advanceExact(doc);

        case SORTED_NUMERIC:
            SortedNumericDocValues sndv = reader.getSortedNumericDocValues(field);
            return sndv != null && sndv.advanceExact(doc);

        case BINARY:
            BinaryDocValues bdv = reader.getBinaryDocValues(field);
            return bdv != null && bdv.advanceExact(doc);

        default:
            return false;
    }
}

// Print missing results
void _printMissingResult(String field, String filterInfo, long missingCount, long hasValueCount, long totalDocs) {

    System.out.println();
    if (filterInfo != null) {
        System.out.println("Missing Aggregation (" + filterInfo + "): " + field);
    } else {
        System.out.println("Missing Aggregation: " + field);
    }
    System.out.println("=".repeat(45));
    System.out.println("Field:       " + field);
    System.out.println("Total Docs:  " + _formatNumMiss(totalDocs));
    System.out.println("-".repeat(45));

    double missingPct = totalDocs > 0 ? (missingCount * 100.0 / totalDocs) : 0;
    double hasValuePct = totalDocs > 0 ? (hasValueCount * 100.0 / totalDocs) : 0;

    System.out.printf("Missing:     %,10d docs (%5.1f%%)%n", missingCount, missingPct);
    System.out.printf("Has Value:   %,10d docs (%5.1f%%)%n", hasValueCount, hasValuePct);
    System.out.println("=".repeat(45));
}

// ==================== Core Implementation ====================

// Missing aggregation for all documents
void _missingImpl(String field) throws Exception {
    _openMissIndex();

    long missingCount = 0;
    long hasValueCount = 0;

    for (LeafReaderContext leaf : _missReader.leaves()) {
        LeafReader reader = leaf.reader();
        int maxDoc = reader.maxDoc();

        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            if (_hasValue(reader, field, doc)) {
                hasValueCount++;
            } else {
                missingCount++;
            }
        }
    }

    _printMissingResult(field, null, missingCount, hasValueCount, missingCount + hasValueCount);
}

// Missing aggregation with query filter
void _missingWithQuery(String field, String queryStr) throws Exception {
    _openMissIndex();

    Query query = _parseQueryMiss(queryStr, field);
    IndexSearcher searcher = new IndexSearcher(_missReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    long missingCount = 0;
    long hasValueCount = 0;

    for (LeafReaderContext leaf : _missReader.leaves()) {
        LeafReader reader = leaf.reader();

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocIdSetIterator disi = scorer.iterator();
        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

            if (_hasValue(reader, field, doc)) {
                hasValueCount++;
            } else {
                missingCount++;
            }
        }
    }

    _printMissingResult(field, "query: " + queryStr, missingCount, hasValueCount, missingCount + hasValueCount);
}

// Missing aggregation with docId list
void _missingWithDocs(String field, int[] docIds) throws Exception {
    _openMissIndex();

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);

    long missingCount = 0;
    long hasValueCount = 0;
    int docIdIndex = 0;

    for (LeafReaderContext leaf : _missReader.leaves()) {
        LeafReader reader = leaf.reader();
        int docBase = leaf.docBase;
        int maxDoc = reader.maxDoc();

        while (docIdIndex < sortedDocIds.length && sortedDocIds[docIdIndex] < docBase) {
            docIdIndex++;
        }
        if (docIdIndex >= sortedDocIds.length) break;

        while (docIdIndex < sortedDocIds.length) {
            int globalDocId = sortedDocIds[docIdIndex];
            if (globalDocId >= docBase + maxDoc) break;

            int localDocId = globalDocId - docBase;
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
                docIdIndex++;
                continue;
            }

            if (_hasValue(reader, field, localDocId)) {
                hasValueCount++;
            } else {
                missingCount++;
            }
            docIdIndex++;
        }
    }

    _printMissingResult(field, docIds.length + " docs", missingCount, hasValueCount, missingCount + hasValueCount);
}

// ==================== Public API ====================

void missing(String field) throws Exception {
    _missingImpl(field);
}

void missingQuery(String field, String query) throws Exception {
    _missingWithQuery(field, query);
}

void missingDocs(String field, int[] docIds) throws Exception {
    _missingWithDocs(field, docIds);
}

System.out.println("Loaded missing-agg skill (OpenSearch-style bucket aggregation)");
System.out.println();
System.out.println("Usage:");
System.out.println("  missing(\"field\")                    - count docs missing field value");
System.out.println("  missingQuery(\"field\", \"query\")      - with query filter");
System.out.println("  missingDocs(\"field\", new int[]{...}) - with docId list");
System.out.println();
System.out.println("  closeMissIndex() - close when done");
