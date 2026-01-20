// io-planner.jsh
// Analyze I/O requirements for Lucene operations before executing them
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/io-planner.jsh
//   jshell> planSum("price")
//   jshell> planTermQuery("color_indexed", "red")
//   jshell> planRangeQuery("price", 7000, 12000)
//   jshell> closeIOPlanner()

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.index.*;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.util.BytesRef;
import java.nio.file.Path;
import java.util.*;

// Cached resources
Directory _ioDir = null;
DirectoryReader _ioReader = null;
Map<String, long[]> _compoundEntries = null;  // file -> [offset, length]

// I/O block record
record IOBlock(String file, String component, long offset, long length, String purpose) {}

void _openIOPlanner() throws Exception {
    if (_ioDir != null) return;
    _ioDir = FSDirectory.open(Path.of(indexPath));
    _ioReader = DirectoryReader.open(_ioDir);
    _loadCompoundEntries();
    System.out.println("I/O Planner ready for: " + indexPath);
}

void closeIOPlanner() throws Exception {
    if (_ioReader != null) { _ioReader.close(); _ioReader = null; }
    if (_ioDir != null) { _ioDir.close(); _ioDir = null; }
    _compoundEntries = null;
    System.out.println("I/O Planner closed.");
}

// Load compound file entries
void _loadCompoundEntries() throws Exception {
    _compoundEntries = new HashMap<>();
    String cfeFile = "_0.cfe";
    if (!Arrays.asList(_ioDir.listAll()).contains(cfeFile)) return;

    IndexInput input = _ioDir.openInput(cfeFile, IOContext.DEFAULT);
    try {
        int magic = input.readInt();
        if (magic != CodecUtil.CODEC_MAGIC) {
            magic = Integer.reverseBytes(magic);
        }
        input.readString(); // codec name
        input.readInt(); // version
        byte[] objectId = new byte[16];
        input.readBytes(objectId, 0, 16);
        int suffixLen = input.readByte() & 0xFF;
        if (suffixLen > 0) input.skipBytes(suffixLen);

        int numEntries = input.readVInt();
        for (int i = 0; i < numEntries; i++) {
            String fileName = input.readString();
            long offset = input.readLong();
            long length = input.readLong();
            _compoundEntries.put(fileName, new long[]{offset, length});
        }
    } finally {
        input.close();
    }
}

// Helper for reflection
Object _getField(Object obj, String fieldName) {
    Class<?> clazz = obj.getClass();
    while (clazz != null) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            clazz = clazz.getSuperclass();
        } catch (Exception e) {
            return null;
        }
    }
    return null;
}

// Get absolute offset in compound file
long[] _getAbsoluteOffset(String component, long relOffset, long length) {
    for (var entry : _compoundEntries.entrySet()) {
        if (entry.getKey().contains(component)) {
            long baseOffset = entry.getValue()[0];
            return new long[]{baseOffset + relOffset, length};
        }
    }
    return new long[]{relOffset, length};
}

// Print I/O plan
void _printPlan(String operation, List<IOBlock> blocks) {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("I/O PLAN: " + operation);
    System.out.println("=".repeat(80));
    System.out.println();

    long totalBytes = 0;
    System.out.printf("%-4s %-12s %-22s %12s %12s  %s%n", "#", "File", "Component", "Offset", "Length", "Purpose");
    System.out.println("-".repeat(80));

    int i = 1;
    for (IOBlock block : blocks) {
        System.out.printf("%-4d %-12s %-22s %,12d %,12d  %s%n",
            i++, block.file(), block.component(), block.offset(), block.length(), block.purpose());
        totalBytes += block.length();
    }

    System.out.println("-".repeat(80));
    System.out.printf("TOTAL: %,d bytes (%d blocks)%n", totalBytes, blocks.size());
    System.out.println();

    // JSON output
    System.out.println("JSON:");
    System.out.println("[");
    for (int j = 0; j < blocks.size(); j++) {
        IOBlock b = blocks.get(j);
        System.out.printf("  {\"file\": \"%s\", \"component\": \"%s\", \"offset\": %d, \"length\": %d}%s%n",
            b.file(), b.component(), b.offset(), b.length(), j < blocks.size() - 1 ? "," : "");
    }
    System.out.println("]");
}

// Get numeric docvalues entry info
Map<String, Long> _getNumericDVInfo(String fieldName) throws Exception {
    var leaf = _ioReader.leaves().get(0).reader();
    var codecReader = (CodecReader) leaf;
    var dvProducer = codecReader.getDocValuesReader();

    var formats = (java.util.HashMap) _getField(dvProducer, "formats");
    var producer = formats.values().iterator().next();
    var numerics = _getField(producer, "numerics");

    var fieldInfo = leaf.getFieldInfos().fieldInfo(fieldName);
    if (fieldInfo == null) return null;

    var getMethod = numerics.getClass().getMethod("get", int.class);
    var entry = getMethod.invoke(numerics, fieldInfo.number);
    if (entry == null) return null;

    Map<String, Long> info = new HashMap<>();
    info.put("valuesOffset", ((Number) _getField(entry, "valuesOffset")).longValue());
    info.put("valuesLength", ((Number) _getField(entry, "valuesLength")).longValue());
    info.put("numValues", ((Number) _getField(entry, "numValues")).longValue());

    return info;
}

// Get sorted docvalues entry info
Map<String, Long> _getSortedDVInfo(String fieldName) throws Exception {
    var leaf = _ioReader.leaves().get(0).reader();
    var codecReader = (CodecReader) leaf;
    var dvProducer = codecReader.getDocValuesReader();

    var formats = (java.util.HashMap) _getField(dvProducer, "formats");
    var producer = formats.values().iterator().next();
    var sorted = _getField(producer, "sorted");

    var fieldInfo = leaf.getFieldInfos().fieldInfo(fieldName);
    if (fieldInfo == null) return null;

    var getMethod = sorted.getClass().getMethod("get", int.class);
    var entry = getMethod.invoke(sorted, fieldInfo.number);
    if (entry == null) return null;

    Map<String, Long> info = new HashMap<>();

    // Get ordsEntry
    var ordsEntry = _getField(entry, "ordsEntry");
    if (ordsEntry != null) {
        info.put("ordsOffset", ((Number) _getField(ordsEntry, "valuesOffset")).longValue());
        info.put("ordsLength", ((Number) _getField(ordsEntry, "valuesLength")).longValue());
    }

    // Get termsDictEntry
    var termsEntry = _getField(entry, "termsDictEntry");
    if (termsEntry != null) {
        info.put("termsDataOffset", ((Number) _getField(termsEntry, "termsDataOffset")).longValue());
        info.put("termsDataLength", ((Number) _getField(termsEntry, "termsDataLength")).longValue());
    }

    return info;
}

// Get term/posting info from FieldInfo metadata (NO term dictionary I/O)
// Returns estimated values based on field-level statistics
Map<String, Long> _getTermInfoFromMeta(String fieldName) throws Exception {
    var leaf = _ioReader.leaves().get(0).reader();
    var fieldInfo = leaf.getFieldInfos().fieldInfo(fieldName);
    if (fieldInfo == null) return null;

    Terms terms = leaf.terms(fieldName);
    if (terms == null) return null;

    Map<String, Long> info = new HashMap<>();
    // Field-level stats (available without seeking)
    info.put("totalTerms", terms.size());  // -1 if not available
    info.put("docCount", (long) terms.getDocCount());
    info.put("sumDocFreq", terms.getSumDocFreq());
    info.put("sumTotalTermFreq", terms.getSumTotalTermFreq());

    return info;
}

// Get term block location from compound file entries (NO term dictionary I/O)
// This gives the ENTIRE field's term data location, not per-term
Map<String, Long> _getTermBlockFromCompound(String fieldName) throws Exception {
    // Get .tim and .doc locations from compound file
    long[] timLoc = _compoundEntries.get("_Lucene101_0.tim");
    long[] docLoc = _compoundEntries.get("_Lucene101_0.doc");

    Map<String, Long> info = new HashMap<>();
    if (timLoc != null) {
        info.put("timOffset", timLoc[0]);
        info.put("timLength", timLoc[1]);
    }
    if (docLoc != null) {
        info.put("docOffset", docLoc[0]);
        info.put("docLength", docLoc[1]);
    }
    return info;
}

// Estimate posting size based on docFreq (NO I/O)
// Uses heuristic: ~1-2 bytes per doc for simple posting lists
long _estimatePostingSize(long docFreq) {
    // Rough estimate: docFreq * 1.5 bytes for compressed doc IDs
    return (long)(docFreq * 1.5);
}

// ============================================================================
// PUBLIC API: Plan operations
// ============================================================================

// Plan SUM aggregation on numeric field
void planSum(String fieldName) throws Exception {
    _openIOPlanner();

    var dvInfo = _getNumericDVInfo(fieldName);
    if (dvInfo == null) {
        System.out.println("ERROR: Field '" + fieldName + "' not found or has no numeric doc values");
        return;
    }

    List<IOBlock> blocks = new ArrayList<>();

    // .dvm metadata
    long[] dvmLoc = _compoundEntries.get("_Lucene90_0.dvm");
    if (dvmLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".dvm", dvmLoc[0], dvmLoc[1], "DocValues metadata"));
    }

    // .dvd field data
    long[] dvdLoc = _compoundEntries.get("_Lucene90_0.dvd");
    if (dvdLoc != null) {
        long absOffset = dvdLoc[0] + dvInfo.get("valuesOffset");
        blocks.add(new IOBlock("_0.cfs", ".dvd (" + fieldName + ")",
            absOffset, dvInfo.get("valuesLength"),
            "Numeric values (" + dvInfo.get("numValues") + " docs)"));
    }

    _printPlan("SUM(" + fieldName + ")", blocks);

    // Now execute
    System.out.println("Executing aggregation...");
    var leaf = _ioReader.leaves().get(0).reader();
    var dv = leaf.getNumericDocValues(fieldName);
    long sum = 0;
    long count = 0;
    while (dv.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        sum += dv.longValue();
        count++;
    }
    System.out.println("Result: SUM(" + fieldName + ") = " + sum + " (over " + count + " docs)");
}

// Plan AVG aggregation
void planAvg(String fieldName) throws Exception {
    _openIOPlanner();

    var dvInfo = _getNumericDVInfo(fieldName);
    if (dvInfo == null) {
        System.out.println("ERROR: Field '" + fieldName + "' not found or has no numeric doc values");
        return;
    }

    List<IOBlock> blocks = new ArrayList<>();

    long[] dvmLoc = _compoundEntries.get("_Lucene90_0.dvm");
    if (dvmLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".dvm", dvmLoc[0], dvmLoc[1], "DocValues metadata"));
    }

    long[] dvdLoc = _compoundEntries.get("_Lucene90_0.dvd");
    if (dvdLoc != null) {
        long absOffset = dvdLoc[0] + dvInfo.get("valuesOffset");
        blocks.add(new IOBlock("_0.cfs", ".dvd (" + fieldName + ")",
            absOffset, dvInfo.get("valuesLength"),
            "Numeric values (" + dvInfo.get("numValues") + " docs)"));
    }

    _printPlan("AVG(" + fieldName + ")", blocks);

    System.out.println("Executing aggregation...");
    var leaf = _ioReader.leaves().get(0).reader();
    var dv = leaf.getNumericDocValues(fieldName);
    long sum = 0;
    long count = 0;
    while (dv.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        sum += dv.longValue();
        count++;
    }
    double avg = count > 0 ? (double) sum / count : 0;
    System.out.printf("Result: AVG(%s) = %.2f (over %d docs)%n", fieldName, avg, count);
}

// Plan term query (NO term dictionary I/O - uses estimates from compound file metadata)
void planTermQuery(String fieldName, String termValue) throws Exception {
    _openIOPlanner();

    // Get field-level metadata (no term seeking)
    var fieldMeta = _getTermInfoFromMeta(fieldName);
    if (fieldMeta == null) {
        System.out.println("ERROR: Field '" + fieldName + "' not found or not indexed");
        return;
    }

    // Get compound file locations
    var compoundMeta = _getTermBlockFromCompound(fieldName);

    List<IOBlock> blocks = new ArrayList<>();

    // .tip (term index) - always needed to find term block
    long[] tipLoc = _compoundEntries.get("_Lucene101_0.tip");
    if (tipLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".tip", tipLoc[0], tipLoc[1], "Term index"));
    }

    // .tim (term dictionary) - estimate one block read
    // BlockTree uses ~25-48 terms per block, estimate ~100 bytes per block
    long[] timLoc = _compoundEntries.get("_Lucene101_0.tim");
    if (timLoc != null) {
        long totalTerms = fieldMeta.get("totalTerms");
        // Estimate: bytes per term * ~40 terms per block
        long bytesPerTerm = totalTerms > 0 ? timLoc[1] / totalTerms : 20;
        long estimatedBlockSize = Math.max(100, bytesPerTerm * 40);
        blocks.add(new IOBlock("_0.cfs", ".tim (block)", timLoc[0], estimatedBlockSize,
            "Term dictionary block (estimated)"));
    }

    // .doc (postings) - estimate based on average docFreq per term
    long[] docLoc = _compoundEntries.get("_Lucene101_0.doc");
    if (docLoc != null) {
        long totalTerms = fieldMeta.get("totalTerms");
        // Average docFreq = sumDocFreq / numTerms
        long avgDocFreq = totalTerms > 0 ?
            fieldMeta.get("sumDocFreq") / totalTerms : 100;
        long estimatedPostingSize = _estimatePostingSize(avgDocFreq);
        blocks.add(new IOBlock("_0.cfs", ".doc (postings)", docLoc[0], estimatedPostingSize,
            "Posting list (estimated ~" + avgDocFreq + " docs avg)"));
    }

    _printPlan("TermQuery(" + fieldName + ":" + termValue + ") [ESTIMATED - no term I/O]", blocks);

    System.out.println("Note: Estimates based on field statistics. Actual I/O may vary per term.");
    System.out.println("Field stats: " + fieldMeta.get("totalTerms") + " terms, " +
        fieldMeta.get("docCount") + " docs with field");
}

// Plan term query with ACTUAL I/O to get exact offsets (reads term dictionary)
void planTermQueryExact(String fieldName, String termValue) throws Exception {
    _openIOPlanner();

    System.out.println("[Reading term dictionary to get exact offsets...]");

    var leaf = _ioReader.leaves().get(0).reader();
    Terms terms = leaf.terms(fieldName);
    if (terms == null) {
        System.out.println("ERROR: Field '" + fieldName + "' not found");
        return;
    }

    TermsEnum te = terms.iterator();
    if (!te.seekExact(new BytesRef(termValue))) {
        System.out.println("ERROR: Term '" + termValue + "' not found");
        return;
    }

    long docFreq = te.docFreq();

    // Get exact offsets via reflection
    var frame = _getField(te, "currentFrame");
    long termFP = 0, termFPEnd = 0, docStartFP = 0;
    if (frame != null) {
        termFP = ((Number) _getField(frame, "fp")).longValue();
        termFPEnd = ((Number) _getField(frame, "fpEnd")).longValue();
        var state = _getField(frame, "state");
        if (state != null) {
            docStartFP = ((Number) _getField(state, "docStartFP")).longValue();
        }
    }

    // Get posting length by iterating terms (expensive!)
    List<Long> allOffsets = new ArrayList<>();
    te = terms.iterator();
    BytesRef term;
    while ((term = te.next()) != null) {
        var f = _getField(te, "currentFrame");
        if (f != null) {
            var s = _getField(f, "state");
            if (s != null) {
                var fp = _getField(s, "docStartFP");
                if (fp != null) allOffsets.add(((Number) fp).longValue());
            }
        }
    }
    Collections.sort(allOffsets);
    long postingLen = -1;
    for (int i = 0; i < allOffsets.size() - 1; i++) {
        if (allOffsets.get(i).equals(docStartFP)) {
            postingLen = allOffsets.get(i + 1) - docStartFP;
            break;
        }
    }
    if (postingLen < 0) postingLen = _estimatePostingSize(docFreq);

    List<IOBlock> blocks = new ArrayList<>();

    long[] tipLoc = _compoundEntries.get("_Lucene101_0.tip");
    if (tipLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".tip", tipLoc[0], tipLoc[1], "Term index"));
    }

    long[] timLoc = _compoundEntries.get("_Lucene101_0.tim");
    if (timLoc != null) {
        long blockOffset = timLoc[0] + termFP;
        long blockLen = termFPEnd - termFP;
        blocks.add(new IOBlock("_0.cfs", ".tim (block)", blockOffset, blockLen, "Term dictionary block (exact)"));
    }

    long[] docLoc = _compoundEntries.get("_Lucene101_0.doc");
    if (docLoc != null) {
        long absOffset = docLoc[0] + docStartFP;
        blocks.add(new IOBlock("_0.cfs", ".doc (postings)", absOffset, postingLen,
            "Posting list (" + docFreq + " docs) (exact)"));
    }

    _printPlan("TermQuery(" + fieldName + ":" + termValue + ") [EXACT - read term dict]", blocks);

    System.out.println("Result: " + docFreq + " matching documents");
}

// Plan numeric range query
void planRangeQuery(String fieldName, long min, long max) throws Exception {
    _openIOPlanner();

    var dvInfo = _getNumericDVInfo(fieldName);
    if (dvInfo == null) {
        System.out.println("ERROR: Field '" + fieldName + "' not found or has no numeric doc values");
        return;
    }

    List<IOBlock> blocks = new ArrayList<>();

    long[] dvmLoc = _compoundEntries.get("_Lucene90_0.dvm");
    if (dvmLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".dvm", dvmLoc[0], dvmLoc[1], "DocValues metadata"));
    }

    long[] dvdLoc = _compoundEntries.get("_Lucene90_0.dvd");
    if (dvdLoc != null) {
        long absOffset = dvdLoc[0] + dvInfo.get("valuesOffset");
        blocks.add(new IOBlock("_0.cfs", ".dvd (" + fieldName + ")",
            absOffset, dvInfo.get("valuesLength"),
            "Scan " + dvInfo.get("numValues") + " values for range"));
    }

    _printPlan("RangeQuery(" + fieldName + ":[" + min + " TO " + max + "])", blocks);

    System.out.println("Executing range query...");
    var leaf = _ioReader.leaves().get(0).reader();
    var dv = leaf.getNumericDocValues(fieldName);
    long count = 0;
    while (dv.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        long val = dv.longValue();
        if (val >= min && val <= max) count++;
    }
    System.out.println("Result: " + count + " matching documents");
}

// Plan terms aggregation (cardinality + top values)
void planTermsAgg(String fieldName) throws Exception {
    _openIOPlanner();

    var dvInfo = _getSortedDVInfo(fieldName);
    if (dvInfo == null) {
        System.out.println("ERROR: Field '" + fieldName + "' not found or has no sorted doc values");
        return;
    }

    List<IOBlock> blocks = new ArrayList<>();

    long[] dvmLoc = _compoundEntries.get("_Lucene90_0.dvm");
    if (dvmLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".dvm", dvmLoc[0], dvmLoc[1], "DocValues metadata"));
    }

    long[] dvdLoc = _compoundEntries.get("_Lucene90_0.dvd");
    if (dvdLoc != null) {
        // Ordinals
        if (dvInfo.containsKey("ordsOffset")) {
            long absOffset = dvdLoc[0] + dvInfo.get("ordsOffset");
            blocks.add(new IOBlock("_0.cfs", ".dvd (ordinals)",
                absOffset, dvInfo.get("ordsLength"), "Doc -> ordinal mapping"));
        }
        // Terms data
        if (dvInfo.containsKey("termsDataOffset")) {
            long absOffset = dvdLoc[0] + dvInfo.get("termsDataOffset");
            blocks.add(new IOBlock("_0.cfs", ".dvd (terms)",
                absOffset, dvInfo.get("termsDataLength"), "Ordinal -> term values"));
        }
    }

    _printPlan("TermsAggregation(" + fieldName + ")", blocks);

    System.out.println("Executing terms aggregation...");
    var leaf = _ioReader.leaves().get(0).reader();
    var dv = leaf.getSortedDocValues(fieldName);
    Map<String, Integer> counts = new TreeMap<>();
    while (dv.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        String val = dv.lookupOrd(dv.ordValue()).utf8ToString();
        counts.merge(val, 1, Integer::sum);
    }
    System.out.println("Result: " + counts.size() + " unique values");
    counts.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .limit(10)
        .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));
}

// Plan stored fields retrieval for a document
void planGetDoc(int docId) throws Exception {
    _openIOPlanner();

    var leaf = _ioReader.leaves().get(0).reader();
    var codecReader = (CodecReader) leaf;
    var fieldsReader = codecReader.getFieldsReader();

    // Get chunk info
    var indexReader = _getField(fieldsReader, "indexReader");
    var startPointers = _getField(indexReader, "startPointers");
    var docs = _getField(indexReader, "docs");
    var numChunks = ((Number) _getField(indexReader, "numChunks")).intValue();
    var maxPointer = ((Number) _getField(fieldsReader, "maxPointer")).longValue();

    var getMethod = startPointers.getClass().getMethod("get", long.class);
    var getDocsMethod = docs.getClass().getMethod("get", long.class);

    // Find chunk for this doc
    int chunk = 0;
    for (int c = 0; c < numChunks; c++) {
        long firstDoc = ((Number) getDocsMethod.invoke(docs, (long) c)).longValue();
        long nextFirstDoc = (c < numChunks - 1) ?
            ((Number) getDocsMethod.invoke(docs, (long)(c + 1))).longValue() : _ioReader.maxDoc();
        if (docId >= firstDoc && docId < nextFirstDoc) {
            chunk = c;
            break;
        }
    }

    long chunkOffset = ((Number) getMethod.invoke(startPointers, (long) chunk)).longValue();
    long chunkEnd = (chunk < numChunks - 1) ?
        ((Number) getMethod.invoke(startPointers, (long)(chunk + 1))).longValue() : maxPointer;
    long chunkLen = chunkEnd - chunkOffset;

    List<IOBlock> blocks = new ArrayList<>();

    // .fdx (index)
    long[] fdxLoc = _compoundEntries.get(".fdx");
    if (fdxLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".fdx", fdxLoc[0], fdxLoc[1], "Stored fields index"));
    }

    // .fdm (metadata)
    long[] fdmLoc = _compoundEntries.get(".fdm");
    if (fdmLoc != null) {
        blocks.add(new IOBlock("_0.cfs", ".fdm", fdmLoc[0], fdmLoc[1], "Stored fields metadata"));
    }

    // .fdt (chunk containing doc)
    long[] fdtLoc = _compoundEntries.get(".fdt");
    if (fdtLoc != null) {
        long absOffset = fdtLoc[0] + chunkOffset;
        blocks.add(new IOBlock("_0.cfs", ".fdt (chunk " + chunk + ")",
            absOffset, chunkLen, "Compressed chunk with doc " + docId));
    }

    _printPlan("GetDocument(" + docId + ")", blocks);

    System.out.println("Document " + docId + " is in chunk " + chunk);
}

System.out.println("Loaded I/O Planner skill.");
System.out.println("Functions (NO I/O during planning - uses metadata only):");
System.out.println("  planSum(field)              - Plan SUM aggregation");
System.out.println("  planAvg(field)              - Plan AVG aggregation");
System.out.println("  planTermQuery(field, term)  - Plan term query (estimated)");
System.out.println("  planRangeQuery(field, min, max) - Plan numeric range query");
System.out.println("  planTermsAgg(field)         - Plan terms aggregation");
System.out.println("  planGetDoc(docId)           - Plan stored fields retrieval");
System.out.println("");
System.out.println("Functions (WITH I/O during planning - reads term dictionary):");
System.out.println("  planTermQueryExact(field, term) - Plan term query (exact offsets)");
System.out.println("");
System.out.println("  closeIOPlanner()            - Close resources");
