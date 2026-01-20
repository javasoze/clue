// nested-agg.jsh
// OpenSearch-style nested bucket aggregations for Lucene indexes
// Combines two bucket aggregations with a metric aggregation at the innermost level
//
// Requires t-digest library for percentiles:
//   jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/nested-agg.jsh
//   jshell> histogramTermsBy("year", 1, "long", "color", "price", "double", "p90", 5)

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
DirectoryReader _nestedReader = null;
Directory _nestedDir = null;

// Codec error detection helper
String _detectCodecErrorNested(Exception e) {
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

void _openNestedIndex() throws Exception {
    if (_nestedReader != null) return;
    try {
        _nestedDir = FSDirectory.open(Path.of(indexPath));
        _nestedReader = DirectoryReader.open(_nestedDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _nestedReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorNested(e);
        if (codecName != null) {
            System.out.println("\n" + "!".repeat(60));
            System.out.println("ERROR: Codec '" + codecName + "' not found!");
            System.out.println("!".repeat(60));
            throw new RuntimeException("Codec not found: " + codecName);
        }
        throw e;
    }
}

void closeNestedIndex() throws Exception {
    if (_nestedReader != null) {
        _nestedReader.close();
        _nestedReader = null;
    }
    if (_nestedDir != null) {
        _nestedDir.close();
        _nestedDir = null;
    }
    System.out.println("Index closed.");
}

// Convert raw long value based on field type
double _convertValueNested(long rawValue, String type) {
    if (type == null) type = "long";
    return switch (type.toLowerCase()) {
        case "double", "d", "float", "f" -> Double.longBitsToDouble(rawValue);
        case "int", "i", "long", "l" -> (double) rawValue;
        default -> (double) rawValue;
    };
}

// Inner bucket data holder
class InnerBucket {
    String key;
    long count;
    TDigest digest;
    double sum;
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;

    InnerBucket(String key) {
        this.key = key;
        this.count = 0;
        this.digest = TDigest.createMergingDigest(100);
        this.sum = 0;
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

// Outer bucket data holder
class OuterBucket {
    String key;
    long sortKey;
    Map<String, InnerBucket> innerBuckets = new HashMap<>();

    OuterBucket(String key) {
        this.key = key;
        this.sortKey = 0;
    }

    OuterBucket(String key, long sortKey) {
        this.key = key;
        this.sortKey = sortKey;
    }

    void add(String innerKey, double value) {
        innerBuckets.computeIfAbsent(innerKey, InnerBucket::new).add(value);
    }

    long totalCount() {
        return innerBuckets.values().stream().mapToLong(b -> b.count).sum();
    }
}

// Format metric value for display
String _formatMetricNested(double value, String metric, String metricField) {
    if (metric.equalsIgnoreCase("count")) {
        return String.format("%,d", (long) value);
    }
    if (metricField.toLowerCase().contains("price") || metricField.toLowerCase().contains("cost")) {
        return String.format("$%,.2f", value);
    }
    return String.format("%,.2f", value);
}

// Print nested aggregation results
void _printNestedResult(String outerType, String outerField, String innerType, String innerField,
                        String metricField, String metric, List<OuterBucket> outerBuckets,
                        int innerSize, String innerSort) {

    long totalDocs = outerBuckets.stream().mapToLong(OuterBucket::totalCount).sum();

    System.out.println();
    System.out.println("Nested Aggregation: " + outerType + "(" + outerField + ") -> " +
                       innerType + "(" + innerField + ") -> " + metric + "(" + metricField + ")");
    System.out.println("=".repeat(70));
    System.out.println("Total docs: " + NumberFormat.getInstance().format(totalDocs));
    System.out.println("=".repeat(70));

    for (OuterBucket outer : outerBuckets) {
        if (outer.innerBuckets.isEmpty()) continue;

        // Sort inner buckets
        Comparator<InnerBucket> comparator;
        if (innerSort.equalsIgnoreCase("key") || innerSort.equalsIgnoreCase("term")) {
            comparator = Comparator.comparing(b -> b.key);
        } else {
            comparator = (a, b) -> Double.compare(b.getMetric(metric), a.getMetric(metric));
        }

        List<InnerBucket> sortedInner = outer.innerBuckets.values().stream()
            .sorted(comparator)
            .limit(innerSize)
            .collect(Collectors.toList());

        System.out.println();
        System.out.printf("%s (%,d docs, %d inner buckets):%n", outer.key, outer.totalCount(), outer.innerBuckets.size());
        System.out.println("-".repeat(55));

        int rank = 1;
        int maxKeyLen = sortedInner.stream().mapToInt(b -> b.key.length()).max().orElse(10);
        maxKeyLen = Math.min(Math.max(maxKeyLen, 8), 15);

        for (InnerBucket inner : sortedInner) {
            double metricVal = inner.getMetric(metric);
            System.out.printf("  %2d. %-" + maxKeyLen + "s  %s: %-12s (%,d docs)%n",
                rank++, inner.key, metric, _formatMetricNested(metricVal, metric, metricField), inner.count);
        }
    }

    System.out.println();
    System.out.println("=".repeat(70));
}

// ==================== Histogram -> Terms -> Metric ====================

void _histogramTermsByImpl(String histField, double interval, String histType,
                           String termsField, String metricField, String metricType,
                           String metric, int innerSize, String innerSort) throws Exception {
    _openNestedIndex();

    Map<Long, OuterBucket> outerMap = new TreeMap<>();

    for (LeafReaderContext leaf : _nestedReader.leaves()) {
        LeafReader reader = leaf.reader();

        // Histogram field (numeric)
        NumericDocValues histDv = reader.getNumericDocValues(histField);
        if (histDv == null) continue;

        // Terms field (SORTED or SORTED_SET)
        FieldInfo termsInfo = reader.getFieldInfos().fieldInfo(termsField);
        if (termsInfo == null) continue;

        SortedDocValues sdv = null;
        SortedSetDocValues ssdv = null;
        if (termsInfo.getDocValuesType() == DocValuesType.SORTED) {
            sdv = reader.getSortedDocValues(termsField);
        } else if (termsInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
            ssdv = reader.getSortedSetDocValues(termsField);
        } else {
            continue;
        }

        // Metric field (numeric)
        NumericDocValues metricDv = reader.getNumericDocValues(metricField);
        if (metricDv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (!histDv.advanceExact(doc) || !metricDv.advanceExact(doc)) continue;

            double histVal = _convertValueNested(histDv.longValue(), histType);
            double metricVal = _convertValueNested(metricDv.longValue(), metricType);

            long bucketKey = (long) (Math.floor(histVal / interval) * interval);
            String outerKeyStr;
            if (interval == 1) {
                outerKeyStr = String.valueOf(bucketKey);
            } else {
                outerKeyStr = String.format("[%d-%d)", bucketKey, (long)(bucketKey + interval));
            }

            OuterBucket outer = outerMap.computeIfAbsent(bucketKey,
                k -> new OuterBucket(outerKeyStr, bucketKey));

            if (sdv != null && sdv.advanceExact(doc)) {
                String termKey = sdv.lookupOrd(sdv.ordValue()).utf8ToString();
                outer.add(termKey, metricVal);
            } else if (ssdv != null && ssdv.advanceExact(doc)) {
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < ssdv.docValueCount(); i++) {
                    String termKey = ssdv.lookupOrd(ssdv.nextOrd()).utf8ToString();
                    if (seen.add(termKey)) {
                        outer.add(termKey, metricVal);
                    }
                }
            }
        }
    }

    _printNestedResult("histogram", histField + "(interval=" + (long)interval + ")",
                       "terms", termsField, metricField, metric,
                       new ArrayList<>(outerMap.values()), innerSize, innerSort);
}

// ==================== Terms -> Terms -> Metric ====================

void _termsTermsByImpl(String outerTermsField, String innerTermsField,
                       String metricField, String metricType, String metric,
                       int outerSize, int innerSize, String outerSort, String innerSort) throws Exception {
    _openNestedIndex();

    Map<String, OuterBucket> outerMap = new HashMap<>();

    for (LeafReaderContext leaf : _nestedReader.leaves()) {
        LeafReader reader = leaf.reader();

        // Outer terms field
        FieldInfo outerInfo = reader.getFieldInfos().fieldInfo(outerTermsField);
        if (outerInfo == null) continue;

        SortedDocValues outerSdv = null;
        SortedSetDocValues outerSsdv = null;
        if (outerInfo.getDocValuesType() == DocValuesType.SORTED) {
            outerSdv = reader.getSortedDocValues(outerTermsField);
        } else if (outerInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
            outerSsdv = reader.getSortedSetDocValues(outerTermsField);
        } else {
            continue;
        }

        // Inner terms field
        FieldInfo innerInfo = reader.getFieldInfos().fieldInfo(innerTermsField);
        if (innerInfo == null) continue;

        SortedDocValues innerSdv = null;
        SortedSetDocValues innerSsdv = null;
        if (innerInfo.getDocValuesType() == DocValuesType.SORTED) {
            innerSdv = reader.getSortedDocValues(innerTermsField);
        } else if (innerInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
            innerSsdv = reader.getSortedSetDocValues(innerTermsField);
        } else {
            continue;
        }

        // Metric field
        NumericDocValues metricDv = reader.getNumericDocValues(metricField);
        if (metricDv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (!metricDv.advanceExact(doc)) continue;

            double metricVal = _convertValueNested(metricDv.longValue(), metricType);

            // Get outer term(s)
            List<String> outerTerms = new ArrayList<>();
            if (outerSdv != null && outerSdv.advanceExact(doc)) {
                outerTerms.add(outerSdv.lookupOrd(outerSdv.ordValue()).utf8ToString());
            } else if (outerSsdv != null && outerSsdv.advanceExact(doc)) {
                for (int i = 0; i < outerSsdv.docValueCount(); i++) {
                    outerTerms.add(outerSsdv.lookupOrd(outerSsdv.nextOrd()).utf8ToString());
                }
            }

            // Get inner term(s)
            List<String> innerTerms = new ArrayList<>();
            if (innerSdv != null && innerSdv.advanceExact(doc)) {
                innerTerms.add(innerSdv.lookupOrd(innerSdv.ordValue()).utf8ToString());
            } else if (innerSsdv != null && innerSsdv.advanceExact(doc)) {
                for (int i = 0; i < innerSsdv.docValueCount(); i++) {
                    innerTerms.add(innerSsdv.lookupOrd(innerSsdv.nextOrd()).utf8ToString());
                }
            }

            // Cross-product
            for (String outerTerm : outerTerms) {
                OuterBucket outer = outerMap.computeIfAbsent(outerTerm, OuterBucket::new);
                for (String innerTerm : innerTerms) {
                    outer.add(innerTerm, metricVal);
                }
            }
        }
    }

    // Sort and limit outer buckets
    Comparator<OuterBucket> outerComp;
    if (outerSort.equalsIgnoreCase("key") || outerSort.equalsIgnoreCase("term")) {
        outerComp = Comparator.comparing(b -> b.key);
    } else {
        outerComp = (a, b) -> Long.compare(b.totalCount(), a.totalCount());
    }

    List<OuterBucket> sortedOuter = outerMap.values().stream()
        .sorted(outerComp)
        .limit(outerSize)
        .collect(Collectors.toList());

    _printNestedResult("terms", outerTermsField, "terms", innerTermsField,
                       metricField, metric, sortedOuter, innerSize, innerSort);
}

// ==================== Range -> Terms -> Metric ====================

void _rangeTermsByImpl(String rangeField, String rangeType, double[] ranges, String[] names,
                       String termsField, String metricField, String metricType,
                       String metric, int innerSize, String innerSort) throws Exception {
    _openNestedIndex();

    // Create outer buckets for each range
    OuterBucket[] outerBuckets = new OuterBucket[ranges.length];
    for (int i = 0; i < ranges.length; i++) {
        String name;
        if (names != null && i < names.length) {
            name = names[i];
        } else if (i < ranges.length - 1) {
            name = String.format("[%.0f-%.0f)", ranges[i], ranges[i + 1]);
        } else {
            name = String.format("[%.0f-*)", ranges[i]);
        }
        outerBuckets[i] = new OuterBucket(name, (long) ranges[i]);
    }

    for (LeafReaderContext leaf : _nestedReader.leaves()) {
        LeafReader reader = leaf.reader();

        // Range field (numeric)
        NumericDocValues rangeDv = reader.getNumericDocValues(rangeField);
        if (rangeDv == null) continue;

        // Terms field
        FieldInfo termsInfo = reader.getFieldInfos().fieldInfo(termsField);
        if (termsInfo == null) continue;

        SortedDocValues sdv = null;
        SortedSetDocValues ssdv = null;
        if (termsInfo.getDocValuesType() == DocValuesType.SORTED) {
            sdv = reader.getSortedDocValues(termsField);
        } else if (termsInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
            ssdv = reader.getSortedSetDocValues(termsField);
        } else {
            continue;
        }

        // Metric field
        NumericDocValues metricDv = reader.getNumericDocValues(metricField);
        if (metricDv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (!rangeDv.advanceExact(doc) || !metricDv.advanceExact(doc)) continue;

            double rangeVal = _convertValueNested(rangeDv.longValue(), rangeType);
            double metricVal = _convertValueNested(metricDv.longValue(), metricType);

            // Find bucket
            int bucketIdx = -1;
            for (int i = 0; i < ranges.length - 1; i++) {
                if (rangeVal >= ranges[i] && rangeVal < ranges[i + 1]) {
                    bucketIdx = i;
                    break;
                }
            }
            if (bucketIdx < 0 && rangeVal >= ranges[ranges.length - 1]) {
                bucketIdx = ranges.length - 1;
            }
            if (bucketIdx < 0) continue;

            // Get term(s)
            if (sdv != null && sdv.advanceExact(doc)) {
                String termKey = sdv.lookupOrd(sdv.ordValue()).utf8ToString();
                outerBuckets[bucketIdx].add(termKey, metricVal);
            } else if (ssdv != null && ssdv.advanceExact(doc)) {
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < ssdv.docValueCount(); i++) {
                    String termKey = ssdv.lookupOrd(ssdv.nextOrd()).utf8ToString();
                    if (seen.add(termKey)) {
                        outerBuckets[bucketIdx].add(termKey, metricVal);
                    }
                }
            }
        }
    }

    _printNestedResult("range", rangeField, "terms", termsField,
                       metricField, metric, Arrays.asList(outerBuckets), innerSize, innerSort);
}

// ==================== Terms -> Histogram -> Metric ====================

void _termsHistogramByImpl(String termsField, String histField, double interval, String histType,
                           String metricField, String metricType, String metric,
                           int outerSize, String outerSort) throws Exception {
    _openNestedIndex();

    // terms -> (histBucket -> InnerBucket)
    Map<String, Map<Long, InnerBucket>> termHistMap = new HashMap<>();

    for (LeafReaderContext leaf : _nestedReader.leaves()) {
        LeafReader reader = leaf.reader();

        // Terms field
        FieldInfo termsInfo = reader.getFieldInfos().fieldInfo(termsField);
        if (termsInfo == null) continue;

        SortedDocValues sdv = null;
        SortedSetDocValues ssdv = null;
        if (termsInfo.getDocValuesType() == DocValuesType.SORTED) {
            sdv = reader.getSortedDocValues(termsField);
        } else if (termsInfo.getDocValuesType() == DocValuesType.SORTED_SET) {
            ssdv = reader.getSortedSetDocValues(termsField);
        } else {
            continue;
        }

        // Histogram field
        NumericDocValues histDv = reader.getNumericDocValues(histField);
        if (histDv == null) continue;

        // Metric field
        NumericDocValues metricDv = reader.getNumericDocValues(metricField);
        if (metricDv == null) continue;

        int maxDoc = reader.maxDoc();
        for (int doc = 0; doc < maxDoc; doc++) {
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
            if (!histDv.advanceExact(doc) || !metricDv.advanceExact(doc)) continue;

            double histVal = _convertValueNested(histDv.longValue(), histType);
            double metricVal = _convertValueNested(metricDv.longValue(), metricType);

            long bucketKey = (long) (Math.floor(histVal / interval) * interval);

            // Get term(s)
            List<String> terms = new ArrayList<>();
            if (sdv != null && sdv.advanceExact(doc)) {
                terms.add(sdv.lookupOrd(sdv.ordValue()).utf8ToString());
            } else if (ssdv != null && ssdv.advanceExact(doc)) {
                for (int i = 0; i < ssdv.docValueCount(); i++) {
                    terms.add(ssdv.lookupOrd(ssdv.nextOrd()).utf8ToString());
                }
            }

            for (String term : terms) {
                String innerKeyStr;
                if (interval == 1) {
                    innerKeyStr = String.valueOf(bucketKey);
                } else {
                    innerKeyStr = String.format("[%d-%d)", bucketKey, (long)(bucketKey + interval));
                }

                termHistMap.computeIfAbsent(term, k -> new TreeMap<>())
                           .computeIfAbsent(bucketKey, k -> new InnerBucket(innerKeyStr))
                           .add(metricVal);
            }
        }
    }

    // Convert to OuterBucket format
    List<OuterBucket> outerBuckets = new ArrayList<>();
    for (var entry : termHistMap.entrySet()) {
        OuterBucket outer = new OuterBucket(entry.getKey());
        for (var histEntry : entry.getValue().entrySet()) {
            InnerBucket inner = histEntry.getValue();
            outer.innerBuckets.put(inner.key, inner);
        }
        outerBuckets.add(outer);
    }

    // Sort outer
    Comparator<OuterBucket> outerComp;
    if (outerSort.equalsIgnoreCase("key") || outerSort.equalsIgnoreCase("term")) {
        outerComp = Comparator.comparing(b -> b.key);
    } else {
        outerComp = (a, b) -> Long.compare(b.totalCount(), a.totalCount());
    }

    outerBuckets = outerBuckets.stream()
        .sorted(outerComp)
        .limit(outerSize)
        .collect(Collectors.toList());

    _printNestedResult("terms", termsField, "histogram", histField + "(interval=" + (long)interval + ")",
                       metricField, metric, outerBuckets, 100, "key");
}

// ==================== Public API ====================

// Histogram -> Terms -> Metric
void histogramTermsBy(String histField, double interval, String histType,
                      String termsField, String metricField, String metricType,
                      String metric, int innerSize) throws Exception {
    _histogramTermsByImpl(histField, interval, histType, termsField, metricField, metricType, metric, innerSize, "metric");
}

void histogramTermsBy(String histField, double interval, String histType,
                      String termsField, String metricField, String metricType,
                      String metric, int innerSize, String innerSort) throws Exception {
    _histogramTermsByImpl(histField, interval, histType, termsField, metricField, metricType, metric, innerSize, innerSort);
}

// Terms -> Terms -> Metric
void termsTermsBy(String outerField, String innerField, String metricField, String metricType,
                  String metric, int outerSize, int innerSize) throws Exception {
    _termsTermsByImpl(outerField, innerField, metricField, metricType, metric, outerSize, innerSize, "count", "metric");
}

void termsTermsBy(String outerField, String innerField, String metricField, String metricType,
                  String metric, int outerSize, int innerSize, String outerSort, String innerSort) throws Exception {
    _termsTermsByImpl(outerField, innerField, metricField, metricType, metric, outerSize, innerSize, outerSort, innerSort);
}

// Range -> Terms -> Metric
void rangeTermsBy(String rangeField, String rangeType, double[] ranges,
                  String termsField, String metricField, String metricType,
                  String metric, int innerSize) throws Exception {
    _rangeTermsByImpl(rangeField, rangeType, ranges, null, termsField, metricField, metricType, metric, innerSize, "metric");
}

void rangeTermsBy(String rangeField, String rangeType, double[] ranges, String[] names,
                  String termsField, String metricField, String metricType,
                  String metric, int innerSize) throws Exception {
    _rangeTermsByImpl(rangeField, rangeType, ranges, names, termsField, metricField, metricType, metric, innerSize, "metric");
}

// Terms -> Histogram -> Metric
void termsHistogramBy(String termsField, String histField, double interval, String histType,
                      String metricField, String metricType, String metric, int outerSize) throws Exception {
    _termsHistogramByImpl(termsField, histField, interval, histType, metricField, metricType, metric, outerSize, "count");
}

void termsHistogramBy(String termsField, String histField, double interval, String histType,
                      String metricField, String metricType, String metric, int outerSize, String outerSort) throws Exception {
    _termsHistogramByImpl(termsField, histField, interval, histType, metricField, metricType, metric, outerSize, outerSort);
}

System.out.println("Loaded nested-agg skill (nested bucket + metric aggregation)");
System.out.println();
System.out.println("Requires: jshell --class-path \"build/libs/*:plugins/t-digest-3.3.jar\"");
System.out.println();
System.out.println("Histogram -> Terms -> Metric:");
System.out.println("  histogramTermsBy(\"year\", 1, \"long\", \"color\", \"price\", \"double\", \"p90\", 5)");
System.out.println();
System.out.println("Terms -> Terms -> Metric:");
System.out.println("  termsTermsBy(\"make\", \"color\", \"price\", \"double\", \"avg\", 5, 3)");
System.out.println();
System.out.println("Range -> Terms -> Metric:");
System.out.println("  rangeTermsBy(\"price\", \"double\", new double[]{0,10000,20000}, \"color\", \"mileage\", \"long\", \"p50\", 5)");
System.out.println();
System.out.println("Terms -> Histogram -> Metric:");
System.out.println("  termsHistogramBy(\"color\", \"year\", 1, \"long\", \"price\", \"double\", \"avg\", 5)");
System.out.println();
System.out.println("Metrics: p50, p75, p90, p95, p99, min, max, avg, sum, count");
System.out.println("Sort:    \"metric\" (default) or \"key\"/\"term\"");
System.out.println();
System.out.println("  closeNestedIndex() - close when done");
