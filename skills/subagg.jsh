// subagg.jsh
// OpenSearch-style bucket + metric sub-aggregation for Lucene indexes
// Combines bucket aggregations (terms, histogram, range) with metric aggregations
//
// Requires t-digest library for percentiles:
//   jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/subagg.jsh
//   jshell> termsBy("color", "price", "double", "p95", 10)

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import com.tdunning.math.stats.TDigest;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.NumberFormat;

// Cached reader
DirectoryReader _subReader = null;
Directory _subDir = null;

// Codec error detection helper
String _detectCodecErrorSub(Exception e) {
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

void _openSubIndex() throws Exception {
    if (_subReader != null) return;
    try {
        _subDir = FSDirectory.open(Path.of(indexPath));
        _subReader = DirectoryReader.open(_subDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _subReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorSub(e);
        if (codecName != null) {
            System.out.println("\n" + "!".repeat(60));
            System.out.println("ERROR: Codec '" + codecName + "' not found!");
            System.out.println("!".repeat(60));
            throw new RuntimeException("Codec not found: " + codecName);
        }
        throw e;
    }
}

void closeSubIndex() throws Exception {
    if (_subReader != null) {
        _subReader.close();
        _subReader = null;
    }
    if (_subDir != null) {
        _subDir.close();
        _subDir = null;
    }
    System.out.println("Index closed.");
}

// Convert raw long value based on field type
double _convertValueSub(long rawValue, String type) {
    if (type == null) type = "long";
    return switch (type.toLowerCase()) {
        case "double", "d", "float", "f" -> Double.longBitsToDouble(rawValue);
        case "int", "i", "long", "l" -> (double) rawValue;
        default -> (double) rawValue;
    };
}

// Bucket data holder
class BucketData {
    String key;
    long sortKey;  // For numeric sorting of buckets
    long count;
    TDigest digest;
    double sum;
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;

    BucketData(String key) {
        this.key = key;
        this.sortKey = 0;
        this.count = 0;
        this.digest = TDigest.createMergingDigest(100);
        this.sum = 0;
    }

    BucketData(String key, long sortKey) {
        this(key);
        this.sortKey = sortKey;
    }

    void add(double value) {
        count++;
        digest.add(value);
        sum += value;
        if (value < min) min = value;
        if (value > max) max = value;
    }

    double getMetric(String metric) {
        if (count == 0) return 0;
        return switch (metric.toLowerCase()) {
            case "p50", "median" -> digest.quantile(0.50);
            case "p75" -> digest.quantile(0.75);
            case "p90" -> digest.quantile(0.90);
            case "p95" -> digest.quantile(0.95);
            case "p99" -> digest.quantile(0.99);
            case "min" -> min;
            case "max" -> max;
            case "avg", "average" -> sum / count;
            case "sum" -> sum;
            case "count" -> (double) count;
            default -> digest.quantile(0.95);
        };
    }
}

// Format metric value for display
String _formatMetric(double value, String metric, String metricField) {
    if (metric.equalsIgnoreCase("count")) {
        return String.format("%,d", (long) value);
    }
    // Only use $ for price-like fields
    if (metricField.toLowerCase().contains("price") || metricField.toLowerCase().contains("cost")) {
        return String.format("$%,.2f", value);
    }
    return String.format("%,.2f", value);
}

// Print sub-aggregation results
void _printSubAggResult(String bucketType, String bucketField, String metricField,
                        String metric, List<BucketData> buckets, String sortBy, int size) {

    // Sort buckets
    Comparator<BucketData> comparator;
    if (sortBy.equalsIgnoreCase("key") || sortBy.equalsIgnoreCase("bucket")) {
        // Use numeric sortKey if available, otherwise string key
        comparator = Comparator.comparingLong((BucketData b) -> b.sortKey).thenComparing(b -> b.key);
    } else {
        comparator = (a, b) -> Double.compare(b.getMetric(metric), a.getMetric(metric));
    }

    List<BucketData> sorted = buckets.stream()
        .sorted(comparator)
        .limit(size)
        .collect(Collectors.toList());

    long totalDocs = buckets.stream().mapToLong(b -> b.count).sum();

    System.out.println();
    System.out.println(bucketType + " + Metric Sub-Aggregation");
    System.out.println("=".repeat(55));
    System.out.println("Bucket:  " + bucketField + " (" + bucketType.toLowerCase() + ")");
    System.out.println("Metric:  " + metric + "(" + metricField + ")");
    System.out.println("Sort:    " + (sortBy.equalsIgnoreCase("key") ? "bucket key" : "metric desc"));
    System.out.println("Showing: " + sorted.size() + " of " + buckets.size() + " buckets");
    System.out.println("-".repeat(55));

    if (sorted.isEmpty()) {
        System.out.println("  (no buckets found)");
    } else {
        int rank = 1;
        int maxKeyLen = sorted.stream().mapToInt(b -> b.key.length()).max().orElse(10);
        maxKeyLen = Math.min(Math.max(maxKeyLen, 8), 20);

        for (BucketData bucket : sorted) {
            double metricVal = bucket.getMetric(metric);
            System.out.printf("  %2d. %-" + maxKeyLen + "s  %s: %-12s (%,d docs)%n",
                rank++, bucket.key, metric, _formatMetric(metricVal, metric, metricField), bucket.count);
        }
    }

    System.out.println("=".repeat(55));
}

// ==================== Terms + Metric ====================

void _termsByImpl(String bucketField, String metricField, String metricType,
                  String metric, int size, String sortBy) throws Exception {
    _openSubIndex();

    Map<String, BucketData> bucketMap = new HashMap<>();

    for (LeafReaderContext leaf : _subReader.leaves()) {
        LeafReader reader = leaf.reader();

        // Get bucket field (SORTED or SORTED_SET)
        FieldInfo bucketInfo = reader.getFieldInfos().fieldInfo(bucketField);
        if (bucketInfo == null) continue;

        SortedDocValues sdv = null;
        SortedSetDocValues ssdv = null;
        if (bucketInfo.getDocValuesType() == DocValuesType.SORTED) {
            sdv = reader.getSortedDocValues(bucketField);
        } else if (bucketInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
            ssdv = reader.getSortedSetDocValues(bucketField);
        } else {
            System.out.println("Warning: " + bucketField + " needs SORTED or SORTED_SET doc values");
            return;
        }

        // Get metric field (NUMERIC)
        NumericDocValues metricDv = reader.getNumericDocValues(metricField);
        if (metricDv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (!metricDv.advanceExact(doc)) continue;

            double metricVal = _convertValueSub(metricDv.longValue(), metricType);

            if (sdv != null && sdv.advanceExact(doc)) {
                String key = sdv.lookupOrd(sdv.ordValue()).utf8ToString();
                bucketMap.computeIfAbsent(key, BucketData::new).add(metricVal);
            } else if (ssdv != null && ssdv.advanceExact(doc)) {
                // For SORTED_SET, add to each bucket the doc belongs to
                Set<String> seenKeys = new HashSet<>();
                for (int i = 0; i < ssdv.docValueCount(); i++) {
                    String key = ssdv.lookupOrd(ssdv.nextOrd()).utf8ToString();
                    if (seenKeys.add(key)) {
                        bucketMap.computeIfAbsent(key, BucketData::new).add(metricVal);
                    }
                }
            }
        }
    }

    _printSubAggResult("Terms", bucketField, metricField, metric,
                       new ArrayList<>(bucketMap.values()), sortBy, size);
}

// ==================== Histogram + Metric ====================

void _histogramByImpl(String bucketField, double interval, String bucketType,
                      String metricField, String metricType, String metric,
                      int size, String sortBy) throws Exception {
    _openSubIndex();

    Map<Long, BucketData> bucketMap = new TreeMap<>();

    for (LeafReaderContext leaf : _subReader.leaves()) {
        LeafReader reader = leaf.reader();

        NumericDocValues bucketDv = reader.getNumericDocValues(bucketField);
        NumericDocValues metricDv = reader.getNumericDocValues(metricField);
        if (bucketDv == null || metricDv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (!bucketDv.advanceExact(doc) || !metricDv.advanceExact(doc)) continue;

            double bucketVal = _convertValueSub(bucketDv.longValue(), bucketType);
            double metricVal = _convertValueSub(metricDv.longValue(), metricType);

            long bucketKey = (long) (Math.floor(bucketVal / interval) * interval);
            String keyStr = String.format("[%d-%d)", bucketKey, (long)(bucketKey + interval));

            bucketMap.computeIfAbsent(bucketKey, k -> new BucketData(keyStr, bucketKey)).add(metricVal);
        }
    }

    _printSubAggResult("Histogram", bucketField + " (interval=" + (long)interval + ")",
                       metricField, metric, new ArrayList<>(bucketMap.values()), sortBy, size);
}

// ==================== Range + Metric ====================

void _rangeByImpl(String bucketField, String bucketType, double[] ranges, String[] names,
                  String metricField, String metricType, String metric, String sortBy) throws Exception {
    _openSubIndex();

    // Create bucket data for each range
    BucketData[] buckets = new BucketData[ranges.length];
    for (int i = 0; i < ranges.length; i++) {
        String name;
        if (names != null && i < names.length) {
            name = names[i];
        } else if (i < ranges.length - 1) {
            name = String.format("[%.0f-%.0f)", ranges[i], ranges[i + 1]);
        } else {
            name = String.format("[%.0f-*)", ranges[i]);
        }
        buckets[i] = new BucketData(name, (long) ranges[i]);
    }

    for (LeafReaderContext leaf : _subReader.leaves()) {
        LeafReader reader = leaf.reader();

        NumericDocValues bucketDv = reader.getNumericDocValues(bucketField);
        NumericDocValues metricDv = reader.getNumericDocValues(metricField);
        if (bucketDv == null || metricDv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (!bucketDv.advanceExact(doc) || !metricDv.advanceExact(doc)) continue;

            double bucketVal = _convertValueSub(bucketDv.longValue(), bucketType);
            double metricVal = _convertValueSub(metricDv.longValue(), metricType);

            // Find which bucket
            int bucketIdx = -1;
            for (int i = 0; i < ranges.length - 1; i++) {
                if (bucketVal >= ranges[i] && bucketVal < ranges[i + 1]) {
                    bucketIdx = i;
                    break;
                }
            }
            if (bucketIdx < 0 && bucketVal >= ranges[ranges.length - 1]) {
                bucketIdx = ranges.length - 1;
            }

            if (bucketIdx >= 0) {
                buckets[bucketIdx].add(metricVal);
            }
        }
    }

    _printSubAggResult("Range", bucketField, metricField, metric,
                       Arrays.asList(buckets), sortBy, ranges.length);
}

// ==================== Public API ====================

// Terms + Metric
void termsBy(String bucketField, String metricField, String metricType, String metric, int size) throws Exception {
    _termsByImpl(bucketField, metricField, metricType, metric, size, "metric");
}

void termsBy(String bucketField, String metricField, String metricType, String metric, int size, String sortBy) throws Exception {
    _termsByImpl(bucketField, metricField, metricType, metric, size, sortBy);
}

// Histogram + Metric
void histogramBy(String bucketField, double interval, String metricField, String metricType, String metric) throws Exception {
    _histogramByImpl(bucketField, interval, "long", metricField, metricType, metric, 100, "key");
}

void histogramBy(String bucketField, double interval, String bucketType, String metricField, String metricType, String metric) throws Exception {
    _histogramByImpl(bucketField, interval, bucketType, metricField, metricType, metric, 100, "key");
}

void histogramBy(String bucketField, double interval, String bucketType, String metricField, String metricType, String metric, int size, String sortBy) throws Exception {
    _histogramByImpl(bucketField, interval, bucketType, metricField, metricType, metric, size, sortBy);
}

// Range + Metric
void rangeBy(String bucketField, String bucketType, double[] ranges, String metricField, String metricType, String metric) throws Exception {
    _rangeByImpl(bucketField, bucketType, ranges, null, metricField, metricType, metric, "key");
}

void rangeBy(String bucketField, String bucketType, double[] ranges, String[] names, String metricField, String metricType, String metric) throws Exception {
    _rangeByImpl(bucketField, bucketType, ranges, names, metricField, metricType, metric, "key");
}

void rangeBy(String bucketField, String bucketType, double[] ranges, String metricField, String metricType, String metric, String sortBy) throws Exception {
    _rangeByImpl(bucketField, bucketType, ranges, null, metricField, metricType, metric, sortBy);
}

System.out.println("Loaded subagg skill (bucket + metric sub-aggregation)");
System.out.println();
System.out.println("Requires: jshell --class-path \"build/libs/*:plugins/t-digest-3.3.jar\"");
System.out.println();
System.out.println("Terms + Metric:");
System.out.println("  termsBy(\"color\", \"price\", \"double\", \"p95\", 10)");
System.out.println("  termsBy(\"color\", \"price\", \"double\", \"avg\", 5, \"key\")");
System.out.println();
System.out.println("Histogram + Metric:");
System.out.println("  histogramBy(\"price\", 5000, \"double\", \"mileage\", \"long\", \"avg\")");
System.out.println();
System.out.println("Range + Metric:");
System.out.println("  rangeBy(\"price\", \"double\", new double[]{0,10000,20000}, \"mileage\", \"long\", \"p50\")");
System.out.println();
System.out.println("Metrics: p50, p75, p90, p95, p99, min, max, avg, sum, count");
System.out.println("Sort:    \"metric\" (default) or \"key\"");
System.out.println();
System.out.println("  closeSubIndex() - close when done");
