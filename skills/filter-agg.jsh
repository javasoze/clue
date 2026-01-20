// filter-agg.jsh
// OpenSearch-style filter bucket aggregation for Lucene indexes
// Single filter bucket and multiple named filter buckets
// https://docs.opensearch.org/latest/aggregations/bucket/filter/
// https://docs.opensearch.org/latest/aggregations/bucket/filters/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/filter-agg.jsh
//   jshell> filter("color_indexed:red")
//   jshell> filters(new String[]{"red", "blue"}, new String[]{"color_indexed:red", "color_indexed:blue"})

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
DirectoryReader _filterReader = null;
Directory _filterDir = null;

// Codec error detection helper
String _detectCodecErrorFilter(Exception e) {
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

void _openFilterIndex() throws Exception {
    if (_filterReader != null) return;
    try {
        _filterDir = FSDirectory.open(Path.of(indexPath));
        _filterReader = DirectoryReader.open(_filterDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _filterReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorFilter(e);
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

void closeFilterIndex() throws Exception {
    if (_filterReader != null) {
        _filterReader.close();
        _filterReader = null;
    }
    if (_filterDir != null) {
        _filterDir.close();
        _filterDir = null;
    }
    System.out.println("Index closed.");
}

// Parse query string to Lucene Query
Query _parseQueryFilter(String queryStr) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser("_default", new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// Format number with commas
String _formatNumFilter(long n) {
    return NumberFormat.getInstance().format(n);
}

// Count matching documents for a query
long _countMatching(Query query) throws Exception {
    IndexSearcher searcher = new IndexSearcher(_filterReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    long count = 0;

    for (LeafReaderContext leaf : _filterReader.leaves()) {
        LeafReader reader = leaf.reader();

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocIdSetIterator disi = scorer.iterator();
        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            count++;
        }
    }

    return count;
}

// Get total live docs
long _getTotalDocs() throws Exception {
    long total = 0;
    for (LeafReaderContext leaf : _filterReader.leaves()) {
        LeafReader reader = leaf.reader();
        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            total++;
        }
    }
    return total;
}

// ==================== Single Filter ====================

void filter(String queryStr) throws Exception {
    _openFilterIndex();

    Query query = _parseQueryFilter(queryStr);
    long matchCount = _countMatching(query);
    long totalDocs = _getTotalDocs();
    double pct = totalDocs > 0 ? (matchCount * 100.0 / totalDocs) : 0;

    System.out.println();
    System.out.println("Filter Aggregation");
    System.out.println("=".repeat(50));
    System.out.println("Query:      " + queryStr);
    System.out.println("Total Docs: " + _formatNumFilter(totalDocs));
    System.out.println("-".repeat(50));
    System.out.printf("Matching:   %,10d docs (%5.1f%%)%n", matchCount, pct);
    System.out.println("=".repeat(50));
}

// ==================== Multiple Filters ====================

void filters(String[] names, String[] queries) throws Exception {
    if (names.length != queries.length) {
        System.out.println("Error: names and queries arrays must have same length");
        return;
    }

    _openFilterIndex();

    long totalDocs = _getTotalDocs();
    long[] counts = new long[queries.length];

    for (int i = 0; i < queries.length; i++) {
        Query query = _parseQueryFilter(queries[i]);
        counts[i] = _countMatching(query);
    }

    long totalMatched = Arrays.stream(counts).sum();

    System.out.println();
    System.out.println("Filters Aggregation");
    System.out.println("=".repeat(55));
    System.out.println("Total Docs: " + _formatNumFilter(totalDocs));
    System.out.println("Buckets:    " + names.length);
    System.out.println("-".repeat(55));

    int maxNameLen = Arrays.stream(names).mapToInt(String::length).max().orElse(10);
    maxNameLen = Math.min(Math.max(maxNameLen, 10), 20);

    for (int i = 0; i < names.length; i++) {
        double pct = totalDocs > 0 ? (counts[i] * 100.0 / totalDocs) : 0;
        String name = names[i].length() > 20 ? names[i].substring(0, 17) + "..." : names[i];
        System.out.printf("  %-" + maxNameLen + "s  %,10d docs (%5.1f%%)  [%s]%n",
            name, counts[i], pct, queries[i]);
    }

    System.out.println("=".repeat(55));
    System.out.println("Note: Buckets may overlap (docs can match multiple filters)");
    System.out.println("=".repeat(55));
}

// Filters with auto-generated names
void filters(String[] queries) throws Exception {
    String[] names = new String[queries.length];
    for (int i = 0; i < queries.length; i++) {
        names[i] = "bucket_" + i;
    }
    filters(names, queries);
}

System.out.println("Loaded filter-agg skill (OpenSearch-style bucket aggregation)");
System.out.println();
System.out.println("Single filter:");
System.out.println("  filter(\"query\")                           - count docs matching query");
System.out.println();
System.out.println("Multiple filters:");
System.out.println("  filters(new String[]{\"q1\", \"q2\", ...})    - auto-named buckets");
System.out.println("  filters(names, queries)                    - named buckets");
System.out.println();
System.out.println("Examples:");
System.out.println("  filter(\"color_indexed:red\")");
System.out.println("  filters(new String[]{\"red\", \"blue\"}, new String[]{\"color_indexed:red\", \"color_indexed:blue\"})");
System.out.println();
System.out.println("  closeFilterIndex() - close when done");
