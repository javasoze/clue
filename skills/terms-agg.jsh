// terms-agg.jsh
// OpenSearch-style terms bucket aggregation for Lucene indexes
// Works on SORTED/SORTED_SET doc values using ordinal-based counting
// https://docs.opensearch.org/latest/aggregations/bucket/terms/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/terms-agg.jsh
//   jshell> terms("color")

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.NumberFormat;

// Cached reader
DirectoryReader _termsReader = null;
Directory _termsDir = null;

// Codec error detection helper
String _detectCodecErrorTerms(Exception e) {
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

void _openTermsIndex() throws Exception {
    if (_termsReader != null) return;
    try {
        _termsDir = FSDirectory.open(Path.of(indexPath));
        _termsReader = DirectoryReader.open(_termsDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _termsReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorTerms(e);
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

void closeTermsIndex() throws Exception {
    if (_termsReader != null) {
        _termsReader.close();
        _termsReader = null;
    }
    if (_termsDir != null) {
        _termsDir.close();
        _termsDir = null;
    }
    System.out.println("Index closed.");
}

// Parse query string to Lucene Query
Query _parseQueryTerms(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Format number with commas
String _formatNum(long n) {
    return NumberFormat.getInstance().format(n);
}

// Print results
void _printTermsResult(String field, String queryInfo, Map<String, Long> counts,
                       int size, String order, int minDocCount, long totalDocs) {

    // Filter by minDocCount and sort
    Comparator<Map.Entry<String, Long>> comparator;
    switch (order.toLowerCase()) {
        case "count_asc":
            comparator = Map.Entry.comparingByValue();
            break;
        case "term":
            comparator = Map.Entry.comparingByKey();
            break;
        case "term_desc":
            comparator = Map.Entry.<String, Long>comparingByKey().reversed();
            break;
        case "count":
        default:
            comparator = Map.Entry.<String, Long>comparingByValue().reversed();
            break;
    }

    List<Map.Entry<String, Long>> sorted = counts.entrySet().stream()
        .filter(e -> e.getValue() >= minDocCount)
        .sorted(comparator)
        .limit(size)
        .collect(Collectors.toList());

    long uniqueTerms = counts.size();
    long displayedSum = sorted.stream().mapToLong(Map.Entry::getValue).sum();
    long totalSum = counts.values().stream().mapToLong(Long::longValue).sum();
    long otherSum = totalSum - displayedSum;

    System.out.println();
    if (queryInfo != null) {
        System.out.println("Terms Aggregation (" + queryInfo + "): " + field);
    } else {
        System.out.println("Terms Aggregation: " + field);
    }
    System.out.println("=".repeat(50));
    System.out.println("Field:   " + field);
    System.out.println("Docs:    " + _formatNum(totalDocs));
    System.out.println("Buckets: " + sorted.size() + " of " + uniqueTerms + " unique terms");
    System.out.println("Order:   " + order);
    if (minDocCount > 1) {
        System.out.println("MinDoc:  " + minDocCount);
    }
    System.out.println("-".repeat(50));

    if (sorted.isEmpty()) {
        System.out.println("  (no buckets found)");
    } else {
        int rank = 1;
        int maxTermLen = sorted.stream().mapToInt(e -> e.getKey().length()).max().orElse(10);
        maxTermLen = Math.min(Math.max(maxTermLen, 10), 30);

        for (Map.Entry<String, Long> entry : sorted) {
            String term = entry.getKey();
            long count = entry.getValue();
            double pct = totalSum > 0 ? (count * 100.0 / totalSum) : 0;

            String termDisplay = term.length() > 30 ? term.substring(0, 27) + "..." : term;
            System.out.printf("%3d. %-" + maxTermLen + "s  %,10d docs (%5.1f%%)%n",
                rank++, termDisplay, count, pct);
        }
    }

    System.out.println("=".repeat(50));
    System.out.println("Sum:     " + _formatNum(displayedSum) + " docs in displayed buckets");
    if (otherSum > 0) {
        System.out.println("Other:   " + _formatNum(otherSum) + " docs in " +
            (uniqueTerms - sorted.size()) + " other buckets");
    }
    System.out.println("=".repeat(50));
}

// ==================== Core Implementation ====================

// Terms aggregation for all documents
void _termsImpl(String field, int size, String order, int minDocCount) throws Exception {
    _openTermsIndex();

    Map<String, Long> globalCounts = new HashMap<>();
    long totalDocs = 0;

    for (LeafReaderContext leaf : _termsReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
        if (finfo == null) continue;

        DocValuesType dvType = finfo.getDocValuesType();

        if (dvType == DocValuesType.SORTED) {
            SortedDocValues sdv = reader.getSortedDocValues(field);
            if (sdv == null) continue;

            int valueCount = sdv.getValueCount();
            long[] ordCounts = new long[valueCount];

            int maxDoc = reader.maxDoc();
            for (int doc = 0; doc < maxDoc; doc++) {
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                if (sdv.advanceExact(doc)) {
                    ordCounts[sdv.ordValue()]++;
                    totalDocs++;
                }
            }

            // Merge non-zero counts into global map
            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = sdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else if (dvType == DocValuesType.SORTED_SET) {
            SortedSetDocValues ssdv = reader.getSortedSetDocValues(field);
            if (ssdv == null) continue;

            long valueCount = ssdv.getValueCount();
            long[] ordCounts = new long[(int) valueCount];

            int maxDoc = reader.maxDoc();
            for (int doc = 0; doc < maxDoc; doc++) {
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                if (ssdv.advanceExact(doc)) {
                    totalDocs++;
                    for (int i = 0; i < ssdv.docValueCount(); i++) {
                        long ord = ssdv.nextOrd();
                        ordCounts[(int) ord]++;
                    }
                }
            }

            // Merge non-zero counts into global map
            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = ssdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else {
            System.out.println("Warning: field '" + field + "' has doc values type " + dvType);
            System.out.println("Terms aggregation requires SORTED or SORTED_SET doc values");
            return;
        }
    }

    _printTermsResult(field, null, globalCounts, size, order, minDocCount, totalDocs);
}

// Terms aggregation with query filter
void _termsWithQuery(String field, String queryStr, int size, String order, int minDocCount) throws Exception {
    _openTermsIndex();

    Query query = _parseQueryTerms(queryStr, field);
    IndexSearcher searcher = new IndexSearcher(_termsReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    Map<String, Long> globalCounts = new HashMap<>();
    long totalDocs = 0;

    for (LeafReaderContext leaf : _termsReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
        if (finfo == null) continue;

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocValuesType dvType = finfo.getDocValuesType();
        DocIdSetIterator disi = scorer.iterator();

        if (dvType == DocValuesType.SORTED) {
            SortedDocValues sdv = reader.getSortedDocValues(field);
            if (sdv == null) continue;

            int valueCount = sdv.getValueCount();
            long[] ordCounts = new long[valueCount];

            int doc;
            while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                if (sdv.advanceExact(doc)) {
                    ordCounts[sdv.ordValue()]++;
                    totalDocs++;
                }
            }

            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = sdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else if (dvType == DocValuesType.SORTED_SET) {
            SortedSetDocValues ssdv = reader.getSortedSetDocValues(field);
            if (ssdv == null) continue;

            long valueCount = ssdv.getValueCount();
            long[] ordCounts = new long[(int) valueCount];

            int doc;
            while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                if (ssdv.advanceExact(doc)) {
                    totalDocs++;
                    for (int i = 0; i < ssdv.docValueCount(); i++) {
                        long ord = ssdv.nextOrd();
                        ordCounts[(int) ord]++;
                    }
                }
            }

            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = ssdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else {
            System.out.println("Warning: field '" + field + "' has doc values type " + dvType);
            System.out.println("Terms aggregation requires SORTED or SORTED_SET doc values");
            return;
        }
    }

    _printTermsResult(field, "query: " + queryStr, globalCounts, size, order, minDocCount, totalDocs);
}

// Terms aggregation with docId list
void _termsWithDocs(String field, int[] docIds, int size, String order, int minDocCount) throws Exception {
    _openTermsIndex();

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);

    Map<String, Long> globalCounts = new HashMap<>();
    long totalDocs = 0;
    int docIdIndex = 0;

    for (LeafReaderContext leaf : _termsReader.leaves()) {
        LeafReader reader = leaf.reader();
        int docBase = leaf.docBase;
        int maxDoc = reader.maxDoc();

        // Skip docIds before this segment
        while (docIdIndex < sortedDocIds.length && sortedDocIds[docIdIndex] < docBase) {
            docIdIndex++;
        }
        if (docIdIndex >= sortedDocIds.length) break;

        FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
        if (finfo == null) continue;

        DocValuesType dvType = finfo.getDocValuesType();

        if (dvType == DocValuesType.NONE) {
            System.out.println("Warning: field '" + field + "' has no doc values");
            System.out.println("termsDocs requires SORTED or SORTED_SET doc values");
            return;
        }

        if (dvType == DocValuesType.SORTED) {
            SortedDocValues sdv = reader.getSortedDocValues(field);
            if (sdv == null) continue;

            int valueCount = sdv.getValueCount();
            long[] ordCounts = new long[valueCount];

            int startIdx = docIdIndex;
            while (docIdIndex < sortedDocIds.length) {
                int globalDocId = sortedDocIds[docIdIndex];
                if (globalDocId >= docBase + maxDoc) break;

                int localDocId = globalDocId - docBase;
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
                    docIdIndex++;
                    continue;
                }

                if (sdv.advanceExact(localDocId)) {
                    ordCounts[sdv.ordValue()]++;
                    totalDocs++;
                }
                docIdIndex++;
            }

            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = sdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else if (dvType == DocValuesType.SORTED_SET) {
            SortedSetDocValues ssdv = reader.getSortedSetDocValues(field);
            if (ssdv == null) continue;

            long valueCount = ssdv.getValueCount();
            long[] ordCounts = new long[(int) valueCount];

            while (docIdIndex < sortedDocIds.length) {
                int globalDocId = sortedDocIds[docIdIndex];
                if (globalDocId >= docBase + maxDoc) break;

                int localDocId = globalDocId - docBase;
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
                    docIdIndex++;
                    continue;
                }

                if (ssdv.advanceExact(localDocId)) {
                    totalDocs++;
                    for (int i = 0; i < ssdv.docValueCount(); i++) {
                        long ord = ssdv.nextOrd();
                        ordCounts[(int) ord]++;
                    }
                }
                docIdIndex++;
            }

            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = ssdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else {
            System.out.println("Warning: field '" + field + "' has doc values type " + dvType);
            System.out.println("Terms aggregation requires SORTED or SORTED_SET doc values");
            return;
        }
    }

    _printTermsResult(field, docIds.length + " docs", globalCounts, size, order, minDocCount, totalDocs);
}

// ==================== Public API ====================

void terms(String field) throws Exception {
    _termsImpl(field, 10, "count", 1);
}

void terms(String field, int size) throws Exception {
    _termsImpl(field, size, "count", 1);
}

void terms(String field, int size, String order) throws Exception {
    _termsImpl(field, size, order, 1);
}

void terms(String field, int size, String order, int minDocCount) throws Exception {
    _termsImpl(field, size, order, minDocCount);
}

void termsQuery(String field, String query) throws Exception {
    _termsWithQuery(field, query, 10, "count", 1);
}

void termsQuery(String field, String query, int size) throws Exception {
    _termsWithQuery(field, query, size, "count", 1);
}

void termsQuery(String field, String query, int size, String order) throws Exception {
    _termsWithQuery(field, query, size, order, 1);
}

void termsQuery(String field, String query, int size, String order, int minDocCount) throws Exception {
    _termsWithQuery(field, query, size, order, minDocCount);
}

void termsDocs(String field, int[] docIds) throws Exception {
    _termsWithDocs(field, docIds, 10, "count", 1);
}

void termsDocs(String field, int[] docIds, int size) throws Exception {
    _termsWithDocs(field, docIds, size, "count", 1);
}

void termsDocs(String field, int[] docIds, int size, String order) throws Exception {
    _termsWithDocs(field, docIds, size, order, 1);
}

void termsDocs(String field, int[] docIds, int size, String order, int minDocCount) throws Exception {
    _termsWithDocs(field, docIds, size, order, minDocCount);
}

// ==================== Numeric Range Filter ====================

// Terms aggregation with numeric doc value range filter
void _termsWithNumericRange(String field, String numericField, String numericType,
                            double minVal, double maxVal, int size, String order, int minDocCount) throws Exception {
    _openTermsIndex();

    Map<String, Long> globalCounts = new HashMap<>();
    long totalDocs = 0;

    for (LeafReaderContext leaf : _termsReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
        if (finfo == null) continue;

        NumericDocValues numDv = reader.getNumericDocValues(numericField);
        if (numDv == null) {
            System.out.println("Warning: numeric field '" + numericField + "' has no doc values");
            return;
        }

        DocValuesType dvType = finfo.getDocValuesType();

        if (dvType == DocValuesType.SORTED) {
            SortedDocValues sdv = reader.getSortedDocValues(field);
            if (sdv == null) continue;

            int valueCount = sdv.getValueCount();
            long[] ordCounts = new long[valueCount];

            int maxDoc = reader.maxDoc();
            for (int doc = 0; doc < maxDoc; doc++) {
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

                // Check numeric range filter
                if (!numDv.advanceExact(doc)) continue;
                double numVal;
                if (numericType.toLowerCase().startsWith("d") || numericType.toLowerCase().startsWith("f")) {
                    numVal = Double.longBitsToDouble(numDv.longValue());
                } else {
                    numVal = numDv.longValue();
                }
                if (numVal < minVal || numVal > maxVal) continue;

                if (sdv.advanceExact(doc)) {
                    ordCounts[sdv.ordValue()]++;
                    totalDocs++;
                }
            }

            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = sdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else if (dvType == DocValuesType.SORTED_SET) {
            SortedSetDocValues ssdv = reader.getSortedSetDocValues(field);
            if (ssdv == null) continue;

            long valueCount = ssdv.getValueCount();
            long[] ordCounts = new long[(int) valueCount];

            int maxDoc = reader.maxDoc();
            for (int doc = 0; doc < maxDoc; doc++) {
                if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;

                // Check numeric range filter
                if (!numDv.advanceExact(doc)) continue;
                double numVal;
                if (numericType.toLowerCase().startsWith("d") || numericType.toLowerCase().startsWith("f")) {
                    numVal = Double.longBitsToDouble(numDv.longValue());
                } else {
                    numVal = numDv.longValue();
                }
                if (numVal < minVal || numVal > maxVal) continue;

                if (ssdv.advanceExact(doc)) {
                    totalDocs++;
                    for (int i = 0; i < ssdv.docValueCount(); i++) {
                        long ord = ssdv.nextOrd();
                        ordCounts[(int) ord]++;
                    }
                }
            }

            for (int ord = 0; ord < valueCount; ord++) {
                if (ordCounts[ord] > 0) {
                    String term = ssdv.lookupOrd(ord).utf8ToString();
                    globalCounts.merge(term, ordCounts[ord], Long::sum);
                }
            }

        } else {
            System.out.println("Warning: field '" + field + "' has doc values type " + dvType);
            System.out.println("Terms aggregation requires SORTED or SORTED_SET doc values");
            return;
        }
    }

    String rangeInfo = numericField + ":[" + minVal + " TO " + maxVal + "]";
    _printTermsResult(field, rangeInfo, globalCounts, size, order, minDocCount, totalDocs);
}

// Public API for numeric range filter
void termsRange(String field, String numericField, double min, double max) throws Exception {
    _termsWithNumericRange(field, numericField, "double", min, max, 10, "count", 1);
}

void termsRange(String field, String numericField, String numericType, double min, double max) throws Exception {
    _termsWithNumericRange(field, numericField, numericType, min, max, 10, "count", 1);
}

void termsRange(String field, String numericField, String numericType, double min, double max, int size) throws Exception {
    _termsWithNumericRange(field, numericField, numericType, min, max, size, "count", 1);
}

void termsRange(String field, String numericField, String numericType, double min, double max, int size, String order) throws Exception {
    _termsWithNumericRange(field, numericField, numericType, min, max, size, order, 1);
}

System.out.println("Loaded terms-agg skill (OpenSearch-style bucket aggregation)");
System.out.println();
System.out.println("Usage:");
System.out.println("  terms(\"field\")                    - top 10 terms by count");
System.out.println("  terms(\"field\", 20)                - top 20 terms");
System.out.println("  terms(\"field\", 20, \"term\")        - top 20 alphabetically");
System.out.println("  terms(\"field\", 20, \"count\", 5)    - minDocCount=5");
System.out.println();
System.out.println("Order options: \"count\" (desc), \"count_asc\", \"term\" (asc), \"term_desc\"");
System.out.println();
System.out.println("With filters:");
System.out.println("  termsQuery(\"field\", \"query\")              - indexed field query");
System.out.println("  termsRange(\"field\", \"numField\", \"double\", min, max)  - numeric range");
System.out.println("  termsDocs(\"field\", new int[]{0, 1, 2, ...})");
System.out.println();
System.out.println("  closeTermsIndex() - close when done");
