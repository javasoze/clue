// cardinality-agg.jsh
// OpenSearch-style cardinality aggregation for Lucene indexes
// Uses HyperLogLog algorithm for approximate distinct count
// https://docs.opensearch.org/latest/aggregations/metric/cardinality/
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/cardinality-agg.jsh
//   jshell> cardinality("color_indexed")

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cached reader
DirectoryReader _cardReader = null;
Directory _cardDir = null;

// Codec error detection helper
String _detectCodecErrorCard(Exception e) {
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

void _openCardIndex() throws Exception {
    if (_cardReader != null) return;
    try {
        _cardDir = FSDirectory.open(Path.of(indexPath));
        _cardReader = DirectoryReader.open(_cardDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _cardReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorCard(e);
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

void closeCardIndex() throws Exception {
    if (_cardReader != null) {
        _cardReader.close();
        _cardReader = null;
    }
    if (_cardDir != null) {
        _cardDir.close();
        _cardDir = null;
    }
    System.out.println("Index closed.");
}

// Parse query string to Lucene Query
Query _parseQueryCard(String queryStr, String defaultField) throws Exception {
    if (queryStr == null || queryStr.trim().isEmpty() || queryStr.equals("*")) {
        return new MatchAllDocsQuery();
    }
    QueryParser parser = new QueryParser(defaultField, new StandardAnalyzer());
    parser.setAllowLeadingWildcard(true);
    return parser.parse(queryStr);
}

// ==================== HyperLogLog Implementation ====================

// MurmurHash3 64-bit hash function
long _murmur3Hash64(byte[] data) {
    final long c1 = 0x87c37b91114253d5L;
    final long c2 = 0x4cf5ad432745937fL;
    final long seed = 0L;

    long h1 = seed;
    long h2 = seed;
    int len = data.length;
    int nblocks = len / 16;

    // body
    for (int i = 0; i < nblocks; i++) {
        int idx = i * 16;
        long k1 = getLongLE(data, idx);
        long k2 = getLongLE(data, idx + 8);

        k1 *= c1; k1 = Long.rotateLeft(k1, 31); k1 *= c2; h1 ^= k1;
        h1 = Long.rotateLeft(h1, 27); h1 += h2; h1 = h1 * 5 + 0x52dce729;

        k2 *= c2; k2 = Long.rotateLeft(k2, 33); k2 *= c1; h2 ^= k2;
        h2 = Long.rotateLeft(h2, 31); h2 += h1; h2 = h2 * 5 + 0x38495ab5;
    }

    // tail
    int tailIdx = nblocks * 16;
    long k1 = 0, k2 = 0;
    int remaining = len - tailIdx;

    if (remaining >= 15) k2 ^= ((long) data[tailIdx + 14] & 0xff) << 48;
    if (remaining >= 14) k2 ^= ((long) data[tailIdx + 13] & 0xff) << 40;
    if (remaining >= 13) k2 ^= ((long) data[tailIdx + 12] & 0xff) << 32;
    if (remaining >= 12) k2 ^= ((long) data[tailIdx + 11] & 0xff) << 24;
    if (remaining >= 11) k2 ^= ((long) data[tailIdx + 10] & 0xff) << 16;
    if (remaining >= 10) k2 ^= ((long) data[tailIdx + 9] & 0xff) << 8;
    if (remaining >= 9) { k2 ^= ((long) data[tailIdx + 8] & 0xff); k2 *= c2; k2 = Long.rotateLeft(k2, 33); k2 *= c1; h2 ^= k2; }

    if (remaining >= 8) k1 ^= getLongLE(data, tailIdx);
    else {
        if (remaining >= 7) k1 ^= ((long) data[tailIdx + 6] & 0xff) << 48;
        if (remaining >= 6) k1 ^= ((long) data[tailIdx + 5] & 0xff) << 40;
        if (remaining >= 5) k1 ^= ((long) data[tailIdx + 4] & 0xff) << 32;
        if (remaining >= 4) k1 ^= ((long) data[tailIdx + 3] & 0xff) << 24;
        if (remaining >= 3) k1 ^= ((long) data[tailIdx + 2] & 0xff) << 16;
        if (remaining >= 2) k1 ^= ((long) data[tailIdx + 1] & 0xff) << 8;
        if (remaining >= 1) k1 ^= ((long) data[tailIdx] & 0xff);
    }
    if (remaining > 0) { k1 *= c1; k1 = Long.rotateLeft(k1, 31); k1 *= c2; h1 ^= k1; }

    // finalization
    h1 ^= len; h2 ^= len;
    h1 += h2; h2 += h1;

    h1 ^= h1 >>> 33; h1 *= 0xff51afd7ed558ccdL; h1 ^= h1 >>> 33; h1 *= 0xc4ceb9fe1a85ec53L; h1 ^= h1 >>> 33;
    h2 ^= h2 >>> 33; h2 *= 0xff51afd7ed558ccdL; h2 ^= h2 >>> 33; h2 *= 0xc4ceb9fe1a85ec53L; h2 ^= h2 >>> 33;

    h1 += h2;
    return h1;
}

long getLongLE(byte[] data, int idx) {
    return ((long) data[idx] & 0xff) |
           (((long) data[idx + 1] & 0xff) << 8) |
           (((long) data[idx + 2] & 0xff) << 16) |
           (((long) data[idx + 3] & 0xff) << 24) |
           (((long) data[idx + 4] & 0xff) << 32) |
           (((long) data[idx + 5] & 0xff) << 40) |
           (((long) data[idx + 6] & 0xff) << 48) |
           (((long) data[idx + 7] & 0xff) << 56);
}

// HyperLogLog class
class HyperLogLog {
    private final int precision;  // p: number of bits for register index (4-18)
    private final int m;          // number of registers = 2^p
    private final int[] registers;
    private final double alphaMM;

    HyperLogLog(int precision) {
        if (precision < 4 || precision > 18) {
            throw new IllegalArgumentException("Precision must be between 4 and 18");
        }
        this.precision = precision;
        this.m = 1 << precision;
        this.registers = new int[m];

        // Alpha constant for bias correction
        double alpha;
        switch (m) {
            case 16: alpha = 0.673; break;
            case 32: alpha = 0.697; break;
            case 64: alpha = 0.709; break;
            default: alpha = 0.7213 / (1 + 1.079 / m);
        }
        this.alphaMM = alpha * m * m;
    }

    void add(byte[] data) {
        long hash = _murmur3Hash64(data);
        // Use first p bits as register index
        int idx = (int) (hash >>> (64 - precision));
        // Count leading zeros of remaining bits + 1
        long w = hash << precision;
        int rank = Long.numberOfLeadingZeros(w) + 1;
        if (registers[idx] < rank) {
            registers[idx] = rank;
        }
    }

    void add(String value) {
        add(value.getBytes(StandardCharsets.UTF_8));
    }

    void add(long value) {
        byte[] data = new byte[8];
        for (int i = 0; i < 8; i++) {
            data[i] = (byte) (value >>> (i * 8));
        }
        add(data);
    }

    long estimate() {
        // Harmonic mean of 2^registers
        double sum = 0;
        int zeros = 0;
        for (int r : registers) {
            sum += 1.0 / (1L << r);
            if (r == 0) zeros++;
        }

        double estimate = alphaMM / sum;

        // Small range correction
        if (estimate <= 2.5 * m && zeros > 0) {
            estimate = m * Math.log((double) m / zeros);
        }

        // Large range correction (not needed for 64-bit hash)
        return Math.round(estimate);
    }

    int getPrecision() { return precision; }
    int getNumRegisters() { return m; }
}

// ==================== Cardinality Implementation ====================

// Detect field type and iterate appropriately
void _cardinalityImpl(String field, int precision) throws Exception {
    _openCardIndex();

    HyperLogLog hll = new HyperLogLog(precision);
    long valuesProcessed = 0;

    for (LeafReaderContext leaf : _cardReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
        if (finfo == null) continue;

        DocValuesType dvType = finfo.getDocValuesType();

        switch (dvType) {
            case SORTED:
                SortedDocValues sdv = reader.getSortedDocValues(field);
                if (sdv != null) {
                    // Iterate unique ords directly
                    for (int ord = 0; ord < sdv.getValueCount(); ord++) {
                        BytesRef term = sdv.lookupOrd(ord);
                        hll.add(Arrays.copyOfRange(term.bytes, term.offset, term.offset + term.length));
                        valuesProcessed++;
                    }
                }
                break;

            case SORTED_SET:
                SortedSetDocValues ssdv = reader.getSortedSetDocValues(field);
                if (ssdv != null) {
                    for (long ord = 0; ord < ssdv.getValueCount(); ord++) {
                        BytesRef term = ssdv.lookupOrd(ord);
                        hll.add(Arrays.copyOfRange(term.bytes, term.offset, term.offset + term.length));
                        valuesProcessed++;
                    }
                }
                break;

            case NUMERIC:
                NumericDocValues ndv = reader.getNumericDocValues(field);
                if (ndv != null) {
                    int maxDoc = reader.maxDoc();
                    for (int doc = 0; doc < maxDoc; doc++) {
                        if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                        if (ndv.advanceExact(doc)) {
                            hll.add(ndv.longValue());
                            valuesProcessed++;
                        }
                    }
                }
                break;

            case SORTED_NUMERIC:
                SortedNumericDocValues sndv = reader.getSortedNumericDocValues(field);
                if (sndv != null) {
                    int maxDoc = reader.maxDoc();
                    for (int doc = 0; doc < maxDoc; doc++) {
                        if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                        if (sndv.advanceExact(doc)) {
                            for (int i = 0; i < sndv.docValueCount(); i++) {
                                hll.add(sndv.nextValue());
                                valuesProcessed++;
                            }
                        }
                    }
                }
                break;

            case BINARY:
                BinaryDocValues bdv = reader.getBinaryDocValues(field);
                if (bdv != null) {
                    int maxDoc = reader.maxDoc();
                    for (int doc = 0; doc < maxDoc; doc++) {
                        if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(doc)) continue;
                        if (bdv.advanceExact(doc)) {
                            BytesRef val = bdv.binaryValue();
                            hll.add(Arrays.copyOfRange(val.bytes, val.offset, val.offset + val.length));
                            valuesProcessed++;
                        }
                    }
                }
                break;

            default:
                // Try term dictionary for indexed fields
                Terms terms = reader.terms(field);
                if (terms != null) {
                    TermsEnum termsEnum = terms.iterator();
                    BytesRef term;
                    while ((term = termsEnum.next()) != null) {
                        hll.add(Arrays.copyOfRange(term.bytes, term.offset, term.offset + term.length));
                        valuesProcessed++;
                    }
                }
        }
    }

    long estimate = hll.estimate();

    System.out.println();
    System.out.println("Cardinality Aggregation: " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Precision:    " + precision + " (" + hll.getNumRegisters() + " registers)");
    System.out.println("Values:       " + valuesProcessed + " processed");
    System.out.println("-".repeat(40));
    System.out.println("Cardinality:  ~" + estimate + " (approximate)");
    System.out.println("=".repeat(40));
}

// Cardinality with query filter
void _cardinalityWithQuery(String field, int precision, String queryStr) throws Exception {
    _openCardIndex();

    HyperLogLog hll = new HyperLogLog(precision);
    Query query = _parseQueryCard(queryStr, field);

    IndexSearcher searcher = new IndexSearcher(_cardReader);
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    long valuesProcessed = 0;

    for (LeafReaderContext leaf : _cardReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
        if (finfo == null) continue;

        Scorer scorer = weight.scorer(leaf);
        if (scorer == null) continue;

        DocValuesType dvType = finfo.getDocValuesType();
        DocIdSetIterator disi = scorer.iterator();

        int doc;
        while ((doc = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            switch (dvType) {
                case SORTED:
                    SortedDocValues sdv = reader.getSortedDocValues(field);
                    if (sdv != null && sdv.advanceExact(doc)) {
                        BytesRef term = sdv.lookupOrd(sdv.ordValue());
                        hll.add(Arrays.copyOfRange(term.bytes, term.offset, term.offset + term.length));
                        valuesProcessed++;
                    }
                    break;

                case SORTED_SET:
                    SortedSetDocValues ssdv = reader.getSortedSetDocValues(field);
                    if (ssdv != null && ssdv.advanceExact(doc)) {
                        for (int i = 0; i < ssdv.docValueCount(); i++) {
                            long ord = ssdv.nextOrd();
                            BytesRef term = ssdv.lookupOrd(ord);
                            hll.add(Arrays.copyOfRange(term.bytes, term.offset, term.offset + term.length));
                            valuesProcessed++;
                        }
                    }
                    break;

                case NUMERIC:
                    NumericDocValues ndv = reader.getNumericDocValues(field);
                    if (ndv != null && ndv.advanceExact(doc)) {
                        hll.add(ndv.longValue());
                        valuesProcessed++;
                    }
                    break;

                case SORTED_NUMERIC:
                    SortedNumericDocValues sndv = reader.getSortedNumericDocValues(field);
                    if (sndv != null && sndv.advanceExact(doc)) {
                        for (int i = 0; i < sndv.docValueCount(); i++) {
                            hll.add(sndv.nextValue());
                            valuesProcessed++;
                        }
                    }
                    break;

                case BINARY:
                    BinaryDocValues bdv = reader.getBinaryDocValues(field);
                    if (bdv != null && bdv.advanceExact(doc)) {
                        BytesRef val = bdv.binaryValue();
                        hll.add(Arrays.copyOfRange(val.bytes, val.offset, val.offset + val.length));
                        valuesProcessed++;
                    }
                    break;

                default:
                    break;
            }
        }
    }

    long estimate = hll.estimate();

    System.out.println();
    System.out.println("Cardinality Aggregation (query: " + queryStr + "): " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Precision:    " + precision);
    System.out.println("Values:       " + valuesProcessed + " processed");
    System.out.println("-".repeat(40));
    System.out.println("Cardinality:  ~" + estimate + " (approximate)");
    System.out.println("=".repeat(40));
}

// Cardinality with docid list
void _cardinalityWithDocs(String field, int precision, int[] docIds) throws Exception {
    _openCardIndex();

    HyperLogLog hll = new HyperLogLog(precision);

    int[] sortedDocIds = docIds.clone();
    Arrays.sort(sortedDocIds);
    int docIdIndex = 0;
    long valuesProcessed = 0;

    for (LeafReaderContext leaf : _cardReader.leaves()) {
        LeafReader reader = leaf.reader();
        int docBase = leaf.docBase;
        int maxDoc = reader.maxDoc();

        while (docIdIndex < sortedDocIds.length && sortedDocIds[docIdIndex] < docBase) {
            docIdIndex++;
        }
        if (docIdIndex >= sortedDocIds.length) break;

        FieldInfo finfo = reader.getFieldInfos().fieldInfo(field);
        if (finfo == null) continue;

        DocValuesType dvType = finfo.getDocValuesType();

        if (dvType == DocValuesType.NONE) {
            System.out.println("Warning: field '" + field + "' has no doc values - cardinalityDocs requires doc values");
            System.out.println("Use cardinality() or cardinalityQuery() instead for indexed-only fields");
            return;
        }

        // Get doc values once per segment
        SortedDocValues sdv = (dvType == DocValuesType.SORTED) ? reader.getSortedDocValues(field) : null;
        SortedSetDocValues ssdv = (dvType == DocValuesType.SORTED_SET) ? reader.getSortedSetDocValues(field) : null;
        NumericDocValues ndv = (dvType == DocValuesType.NUMERIC) ? reader.getNumericDocValues(field) : null;
        SortedNumericDocValues sndv = (dvType == DocValuesType.SORTED_NUMERIC) ? reader.getSortedNumericDocValues(field) : null;
        BinaryDocValues bdv = (dvType == DocValuesType.BINARY) ? reader.getBinaryDocValues(field) : null;

        while (docIdIndex < sortedDocIds.length) {
            int globalDocId = sortedDocIds[docIdIndex];
            if (globalDocId >= docBase + maxDoc) break;

            int localDocId = globalDocId - docBase;
            if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
                docIdIndex++;
                continue;
            }

            switch (dvType) {
                case SORTED:
                    if (sdv != null && sdv.advanceExact(localDocId)) {
                        BytesRef term = sdv.lookupOrd(sdv.ordValue());
                        hll.add(Arrays.copyOfRange(term.bytes, term.offset, term.offset + term.length));
                        valuesProcessed++;
                    }
                    break;

                case SORTED_SET:
                    if (ssdv != null && ssdv.advanceExact(localDocId)) {
                        for (int i = 0; i < ssdv.docValueCount(); i++) {
                            long ord = ssdv.nextOrd();
                            BytesRef term = ssdv.lookupOrd(ord);
                            hll.add(Arrays.copyOfRange(term.bytes, term.offset, term.offset + term.length));
                            valuesProcessed++;
                        }
                    }
                    break;

                case NUMERIC:
                    if (ndv != null && ndv.advanceExact(localDocId)) {
                        hll.add(ndv.longValue());
                        valuesProcessed++;
                    }
                    break;

                case SORTED_NUMERIC:
                    if (sndv != null && sndv.advanceExact(localDocId)) {
                        for (int i = 0; i < sndv.docValueCount(); i++) {
                            hll.add(sndv.nextValue());
                            valuesProcessed++;
                        }
                    }
                    break;

                case BINARY:
                    if (bdv != null && bdv.advanceExact(localDocId)) {
                        BytesRef val = bdv.binaryValue();
                        hll.add(Arrays.copyOfRange(val.bytes, val.offset, val.offset + val.length));
                        valuesProcessed++;
                    }
                    break;

                default:
                    break;
            }
            docIdIndex++;
        }
    }

    long estimate = hll.estimate();

    System.out.println();
    System.out.println("Cardinality Aggregation (" + docIds.length + " docs): " + field);
    System.out.println("=".repeat(40));
    System.out.println("Field:        " + field);
    System.out.println("Precision:    " + precision);
    System.out.println("Values:       " + valuesProcessed + " processed");
    System.out.println("-".repeat(40));
    System.out.println("Cardinality:  ~" + estimate + " (approximate)");
    System.out.println("=".repeat(40));
}

// === Public API ===

void cardinality(String field) throws Exception {
    _cardinalityImpl(field, 14);  // default precision
}

void cardinality(String field, int precision) throws Exception {
    _cardinalityImpl(field, precision);
}

void cardinalityQuery(String field, String query) throws Exception {
    _cardinalityWithQuery(field, 14, query);
}

void cardinalityQuery(String field, int precision, String query) throws Exception {
    _cardinalityWithQuery(field, precision, query);
}

void cardinalityDocs(String field, int[] docIds) throws Exception {
    _cardinalityWithDocs(field, 14, docIds);
}

void cardinalityDocs(String field, int precision, int[] docIds) throws Exception {
    _cardinalityWithDocs(field, precision, docIds);
}

System.out.println("Loaded cardinality-agg skill (HyperLogLog approximate distinct count)");
System.out.println();
System.out.println("Usage:");
System.out.println("  cardinality(\"field\")              - default precision (14)");
System.out.println("  cardinality(\"field\", 10)          - custom precision (4-18)");
System.out.println("  cardinalityQuery(\"field\", \"query\")");
System.out.println("  cardinalityDocs(\"field\", new int[]{...})");
System.out.println();
System.out.println("Precision: higher = more accurate but more memory");
System.out.println("  10 = ~1KB,  error ~1.04%");
System.out.println("  14 = ~16KB, error ~0.81% (default)");
System.out.println("  18 = ~256KB, error ~0.41%");
System.out.println();
System.out.println("  closeCardIndex() - close when done");
