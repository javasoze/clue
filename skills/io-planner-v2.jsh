// io-planner-v2.jsh
// I/O Planner that uses pre-generated metadata file ONLY
// Does NOT read from the Lucene index - all data comes from io-metadata.json
//
// Usage:
//   jshell> /open skills/io-planner-v2.jsh
//   jshell> loadIOMetadata("io-metadata.json")  // Load metadata file
//   jshell> planSum("price")
//   jshell> planTermQuery("color_indexed", "red")

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

// Parsed metadata
Map<String, Object> _ioMeta = null;
String _metaFile = null;

// I/O block record
record IOBlock(String file, String component, long offset, long length, String purpose) {}

// Simple JSON parser for our metadata format
@SuppressWarnings("unchecked")
Map<String, Object> parseJson(String json) {
    // This is a simple parser for our specific JSON format
    // In production, use a proper JSON library
    Map<String, Object> result = new LinkedHashMap<>();
    json = json.trim();
    if (!json.startsWith("{")) return result;

    json = json.substring(1, json.lastIndexOf("}")).trim();

    // Simple state machine for parsing
    int i = 0;
    while (i < json.length()) {
        // Skip whitespace
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) break;

        // Expect key
        if (json.charAt(i) != '"') { i++; continue; }
        int keyStart = i + 1;
        int keyEnd = json.indexOf('"', keyStart);
        String key = json.substring(keyStart, keyEnd);
        i = keyEnd + 1;

        // Skip to colon
        while (i < json.length() && json.charAt(i) != ':') i++;
        i++; // skip colon

        // Skip whitespace
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

        // Parse value
        if (i >= json.length()) break;
        char c = json.charAt(i);

        if (c == '"') {
            // String value
            int valStart = i + 1;
            int valEnd = valStart;
            while (valEnd < json.length()) {
                if (json.charAt(valEnd) == '"' && json.charAt(valEnd-1) != '\\') break;
                valEnd++;
            }
            result.put(key, json.substring(valStart, valEnd));
            i = valEnd + 1;
        } else if (c == '{') {
            // Nested object - find matching brace
            int depth = 1;
            int objStart = i;
            i++;
            while (i < json.length() && depth > 0) {
                if (json.charAt(i) == '{') depth++;
                else if (json.charAt(i) == '}') depth--;
                i++;
            }
            result.put(key, parseJson(json.substring(objStart, i)));
        } else if (c == '[') {
            // Array - find matching bracket
            int depth = 1;
            int arrStart = i;
            i++;
            while (i < json.length() && depth > 0) {
                if (json.charAt(i) == '[') depth++;
                else if (json.charAt(i) == ']') depth--;
                i++;
            }
            result.put(key, parseJsonArray(json.substring(arrStart, i)));
        } else if (Character.isDigit(c) || c == '-') {
            // Number
            int numStart = i;
            while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '.' || json.charAt(i) == '-')) i++;
            String numStr = json.substring(numStart, i);
            if (numStr.contains(".")) {
                result.put(key, Double.parseDouble(numStr));
            } else {
                result.put(key, Long.parseLong(numStr));
            }
        } else {
            // Skip unknown
            i++;
        }

        // Skip comma
        while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
    }

    return result;
}

@SuppressWarnings("unchecked")
List<Object> parseJsonArray(String json) {
    List<Object> result = new ArrayList<>();
    json = json.trim();
    if (!json.startsWith("[")) return result;

    json = json.substring(1, json.lastIndexOf("]")).trim();
    if (json.isEmpty()) return result;

    int i = 0;
    while (i < json.length()) {
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) break;

        char c = json.charAt(i);
        if (c == '{') {
            int depth = 1;
            int objStart = i;
            i++;
            while (i < json.length() && depth > 0) {
                if (json.charAt(i) == '{') depth++;
                else if (json.charAt(i) == '}') depth--;
                i++;
            }
            result.add(parseJson(json.substring(objStart, i)));
        } else {
            i++;
        }

        while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
    }

    return result;
}

// Load metadata from file
void loadIOMetadata(String filePath) throws Exception {
    _metaFile = filePath;
    String content = Files.readString(Path.of(filePath));
    _ioMeta = parseJson(content);

    System.out.println("Loaded I/O metadata from: " + filePath);
    System.out.println("  Index: " + _ioMeta.get("indexPath"));
    System.out.println("  Generated: " + _ioMeta.get("generatedAt"));
    System.out.println("  Docs: " + _ioMeta.get("numDocs"));

    var indexed = (Map<String, Object>) _ioMeta.get("indexedFields");
    var docvals = (Map<String, Object>) _ioMeta.get("docValueFields");
    System.out.println("  Indexed fields: " + (indexed != null ? indexed.size() : 0));
    System.out.println("  DocValue fields: " + (docvals != null ? docvals.size() : 0));
}

// Check if metadata is loaded
void _checkMeta() {
    if (_ioMeta == null) {
        throw new RuntimeException("Metadata not loaded. Run: loadIOMetadata(\"io-metadata.json\")");
    }
}

// Print I/O plan
void _printPlan(String operation, List<IOBlock> blocks, List<String> warnings) {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("I/O PLAN: " + operation);
    System.out.println("=".repeat(80));
    System.out.println();

    if (!warnings.isEmpty()) {
        System.out.println("WARNINGS:");
        for (String w : warnings) {
            System.out.println("  ! " + w);
        }
        System.out.println();
    }

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

    System.out.println("JSON:");
    System.out.println("[");
    for (int j = 0; j < blocks.size(); j++) {
        IOBlock b = blocks.get(j);
        System.out.printf("  {\"file\": \"%s\", \"component\": \"%s\", \"offset\": %d, \"length\": %d}%s%n",
            b.file(), b.component(), b.offset(), b.length(), j < blocks.size() - 1 ? "," : "");
    }
    System.out.println("]");
}

// Get compound file name
String _getCompoundFile() {
    var cf = (Map<String, Object>) _ioMeta.get("compoundFile");
    return cf != null ? (String) cf.get("file") : "_0.cfs";
}

// ============================================================================
// PUBLIC API
// ============================================================================

@SuppressWarnings("unchecked")
void planSum(String fieldName) {
    _checkMeta();

    List<IOBlock> blocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String file = _getCompoundFile();

    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    if (dvFields == null) {
        warnings.add("No docValueFields in metadata");
        _printPlan("SUM(" + fieldName + ")", blocks, warnings);
        return;
    }

    var field = (Map<String, Object>) dvFields.get(fieldName);
    if (field == null) {
        warnings.add("Field '" + fieldName + "' not found in docValueFields");
        _printPlan("SUM(" + fieldName + ")", blocks, warnings);
        return;
    }

    String type = (String) field.get("type");
    if (!"NUMERIC".equals(type)) {
        warnings.add("Field '" + fieldName + "' is " + type + ", not NUMERIC");
    }

    // Metadata block
    if (field.containsKey("metaOffset")) {
        blocks.add(new IOBlock(file, ".dvm",
            ((Number) field.get("metaOffset")).longValue(),
            ((Number) field.get("metaLength")).longValue(),
            "DocValues metadata"));
    } else {
        warnings.add("Missing metaOffset for field");
    }

    // Data block
    if (field.containsKey("dataOffset")) {
        blocks.add(new IOBlock(file, ".dvd (" + fieldName + ")",
            ((Number) field.get("dataOffset")).longValue(),
            ((Number) field.get("dataLength")).longValue(),
            "Numeric values (" + field.get("numValues") + " docs)"));
    } else {
        warnings.add("Missing dataOffset for field");
    }

    _printPlan("SUM(" + fieldName + ")", blocks, warnings);
}

@SuppressWarnings("unchecked")
void planAvg(String fieldName) {
    _checkMeta();

    List<IOBlock> blocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String file = _getCompoundFile();

    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    var field = dvFields != null ? (Map<String, Object>) dvFields.get(fieldName) : null;

    if (field == null) {
        warnings.add("Field '" + fieldName + "' not found in docValueFields");
        _printPlan("AVG(" + fieldName + ")", blocks, warnings);
        return;
    }

    if (field.containsKey("metaOffset")) {
        blocks.add(new IOBlock(file, ".dvm",
            ((Number) field.get("metaOffset")).longValue(),
            ((Number) field.get("metaLength")).longValue(),
            "DocValues metadata"));
    }

    if (field.containsKey("dataOffset")) {
        blocks.add(new IOBlock(file, ".dvd (" + fieldName + ")",
            ((Number) field.get("dataOffset")).longValue(),
            ((Number) field.get("dataLength")).longValue(),
            "Numeric values (" + field.get("numValues") + " docs)"));
    }

    _printPlan("AVG(" + fieldName + ")", blocks, warnings);
}

@SuppressWarnings("unchecked")
void planTermQuery(String fieldName, String termValue) {
    _checkMeta();

    List<IOBlock> blocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String file = _getCompoundFile();

    var indexedFields = (Map<String, Object>) _ioMeta.get("indexedFields");
    if (indexedFields == null) {
        warnings.add("No indexedFields in metadata");
        _printPlan("TermQuery(" + fieldName + ":" + termValue + ")", blocks, warnings);
        return;
    }

    var field = (Map<String, Object>) indexedFields.get(fieldName);
    if (field == null) {
        warnings.add("Field '" + fieldName + "' not found in indexedFields");
        _printPlan("TermQuery(" + fieldName + ":" + termValue + ")", blocks, warnings);
        return;
    }

    var terms = (Map<String, Object>) field.get("terms");
    if (terms == null) {
        warnings.add("No terms data for field '" + fieldName + "'");
        _printPlan("TermQuery(" + fieldName + ":" + termValue + ")", blocks, warnings);
        return;
    }

    var termInfo = (Map<String, Object>) terms.get(termValue);
    if (termInfo == null) {
        warnings.add("Term '" + termValue + "' not found in metadata");
        _printPlan("TermQuery(" + fieldName + ":" + termValue + ")", blocks, warnings);
        return;
    }

    // .tip (term index) - get from compound file entries
    var cf = (Map<String, Object>) _ioMeta.get("compoundFile");
    var entries = (List<Object>) cf.get("entries");
    for (Object e : entries) {
        var entry = (Map<String, Object>) e;
        String comp = (String) entry.get("component");
        if (comp.contains(".tip")) {
            blocks.add(new IOBlock(file, ".tip",
                ((Number) entry.get("offset")).longValue(),
                ((Number) entry.get("length")).longValue(),
                "Term index"));
            break;
        }
    }

    // .tim (term dictionary block)
    if (termInfo.containsKey("timOffset") && termInfo.containsKey("timLength")) {
        blocks.add(new IOBlock(file, ".tim (block)",
            ((Number) termInfo.get("timOffset")).longValue(),
            ((Number) termInfo.get("timLength")).longValue(),
            "Term dictionary block"));
    } else {
        warnings.add("Missing timOffset/timLength for term");
    }

    // .doc (postings)
    if (termInfo.containsKey("docOffset")) {
        long docFreq = ((Number) termInfo.get("docFreq")).longValue();
        String purpose = "Posting list (" + docFreq + " docs)";

        if (termInfo.containsKey("docLength")) {
            blocks.add(new IOBlock(file, ".doc (postings)",
                ((Number) termInfo.get("docOffset")).longValue(),
                ((Number) termInfo.get("docLength")).longValue(),
                purpose));
        } else {
            warnings.add("Missing docLength for term (last term in field?)");
            // Use estimate
            blocks.add(new IOBlock(file, ".doc (postings)",
                ((Number) termInfo.get("docOffset")).longValue(),
                (long)(docFreq * 1.5),
                purpose + " [length estimated]"));
        }
    } else {
        warnings.add("Missing docOffset for term");
    }

    // .pos (positions) if available
    if (termInfo.containsKey("posOffset")) {
        blocks.add(new IOBlock(file, ".pos (positions)",
            ((Number) termInfo.get("posOffset")).longValue(),
            -1, // Length not stored
            "Position data"));
        warnings.add("Position length not available in metadata");
    }

    _printPlan("TermQuery(" + fieldName + ":" + termValue + ")", blocks, warnings);

    System.out.println("docFreq: " + termInfo.get("docFreq"));
}

@SuppressWarnings("unchecked")
void planRangeQuery(String fieldName, long min, long max) {
    _checkMeta();

    List<IOBlock> blocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String file = _getCompoundFile();

    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    var field = dvFields != null ? (Map<String, Object>) dvFields.get(fieldName) : null;

    if (field == null) {
        warnings.add("Field '" + fieldName + "' not found in docValueFields");
        _printPlan("RangeQuery(" + fieldName + ":[" + min + " TO " + max + "])", blocks, warnings);
        return;
    }

    if (field.containsKey("metaOffset")) {
        blocks.add(new IOBlock(file, ".dvm",
            ((Number) field.get("metaOffset")).longValue(),
            ((Number) field.get("metaLength")).longValue(),
            "DocValues metadata"));
    }

    if (field.containsKey("dataOffset")) {
        blocks.add(new IOBlock(file, ".dvd (" + fieldName + ")",
            ((Number) field.get("dataOffset")).longValue(),
            ((Number) field.get("dataLength")).longValue(),
            "Scan " + field.get("numValues") + " values for range"));
    }

    _printPlan("RangeQuery(" + fieldName + ":[" + min + " TO " + max + "])", blocks, warnings);
}

@SuppressWarnings("unchecked")
void planTermsAgg(String fieldName) {
    _checkMeta();

    List<IOBlock> blocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String file = _getCompoundFile();

    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    var field = dvFields != null ? (Map<String, Object>) dvFields.get(fieldName) : null;

    if (field == null) {
        warnings.add("Field '" + fieldName + "' not found in docValueFields");
        _printPlan("TermsAggregation(" + fieldName + ")", blocks, warnings);
        return;
    }

    String type = (String) field.get("type");
    if (!"SORTED".equals(type) && !"SORTED_SET".equals(type)) {
        warnings.add("Field is " + type + ", expected SORTED or SORTED_SET");
    }

    if (field.containsKey("metaOffset")) {
        blocks.add(new IOBlock(file, ".dvm",
            ((Number) field.get("metaOffset")).longValue(),
            ((Number) field.get("metaLength")).longValue(),
            "DocValues metadata"));
    }

    if (field.containsKey("ordsOffset")) {
        blocks.add(new IOBlock(file, ".dvd (ordinals)",
            ((Number) field.get("ordsOffset")).longValue(),
            ((Number) field.get("ordsLength")).longValue(),
            "Doc -> ordinal mapping"));
    }

    if (field.containsKey("termsOffset")) {
        blocks.add(new IOBlock(file, ".dvd (terms)",
            ((Number) field.get("termsOffset")).longValue(),
            ((Number) field.get("termsLength")).longValue(),
            "Ordinal -> term values"));
    }

    _printPlan("TermsAggregation(" + fieldName + ")", blocks, warnings);
}

@SuppressWarnings("unchecked")
void planGetDoc(int docId) {
    _checkMeta();

    List<IOBlock> blocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String file = _getCompoundFile();

    var storedFields = (Map<String, Object>) _ioMeta.get("storedFields");
    if (storedFields == null) {
        warnings.add("No storedFields in metadata");
        _printPlan("GetDocument(" + docId + ")", blocks, warnings);
        return;
    }

    // Index
    if (storedFields.containsKey("indexOffset")) {
        blocks.add(new IOBlock(file, ".fdx",
            ((Number) storedFields.get("indexOffset")).longValue(),
            ((Number) storedFields.get("indexLength")).longValue(),
            "Stored fields index"));
    }

    // Metadata
    if (storedFields.containsKey("metaOffset")) {
        blocks.add(new IOBlock(file, ".fdm",
            ((Number) storedFields.get("metaOffset")).longValue(),
            ((Number) storedFields.get("metaLength")).longValue(),
            "Stored fields metadata"));
    }

    // Find chunk containing this doc
    var chunks = (List<Object>) storedFields.get("chunks");
    if (chunks != null) {
        Map<String, Object> targetChunk = null;
        for (Object c : chunks) {
            var chunk = (Map<String, Object>) c;
            long firstDoc = ((Number) chunk.get("firstDoc")).longValue();
            if (docId >= firstDoc) {
                targetChunk = chunk;
            } else {
                break;
            }
        }

        if (targetChunk != null) {
            int chunkNum = ((Number) targetChunk.get("chunk")).intValue();
            blocks.add(new IOBlock(file, ".fdt (chunk " + chunkNum + ")",
                ((Number) targetChunk.get("offset")).longValue(),
                ((Number) targetChunk.get("length")).longValue(),
                "Compressed chunk with doc " + docId));
        } else {
            warnings.add("Could not find chunk for doc " + docId);
        }
    }

    _printPlan("GetDocument(" + docId + ")", blocks, warnings);
}

// List available terms for a field
@SuppressWarnings("unchecked")
void listTerms(String fieldName) {
    _checkMeta();

    var indexedFields = (Map<String, Object>) _ioMeta.get("indexedFields");
    if (indexedFields == null) {
        System.out.println("No indexed fields in metadata");
        return;
    }

    var field = (Map<String, Object>) indexedFields.get(fieldName);
    if (field == null) {
        System.out.println("Field '" + fieldName + "' not found");
        return;
    }

    var terms = (Map<String, Object>) field.get("terms");
    if (terms == null) {
        System.out.println("No terms for field '" + fieldName + "'");
        return;
    }

    System.out.println("Terms for field '" + fieldName + "':");
    for (String term : terms.keySet()) {
        var info = (Map<String, Object>) terms.get(term);
        System.out.println("  " + term + " (docFreq: " + info.get("docFreq") + ")");
    }
}

// List available fields
@SuppressWarnings("unchecked")
void listFields() {
    _checkMeta();

    System.out.println("Indexed fields:");
    var indexedFields = (Map<String, Object>) _ioMeta.get("indexedFields");
    if (indexedFields != null) {
        for (String f : indexedFields.keySet()) {
            var field = (Map<String, Object>) indexedFields.get(f);
            System.out.println("  " + f + " (" + field.get("totalTerms") + " terms)");
        }
    }

    System.out.println("\nDocValue fields:");
    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    if (dvFields != null) {
        for (String f : dvFields.keySet()) {
            var field = (Map<String, Object>) dvFields.get(f);
            System.out.println("  " + f + " (" + field.get("type") + ")");
        }
    }
}

// Combined: filtered aggregation (term query + metric)
@SuppressWarnings("unchecked")
void planFilteredSum(String filterField, String filterTerm, String sumField) {
    _checkMeta();

    List<IOBlock> blocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String file = _getCompoundFile();
    long matchingDocs = 0;

    // Part 1: Term query blocks
    var indexedFields = (Map<String, Object>) _ioMeta.get("indexedFields");
    if (indexedFields != null) {
        var field = (Map<String, Object>) indexedFields.get(filterField);
        if (field != null) {
            var terms = (Map<String, Object>) field.get("terms");
            if (terms != null) {
                var termInfo = (Map<String, Object>) terms.get(filterTerm);
                if (termInfo != null) {
                    matchingDocs = ((Number) termInfo.get("docFreq")).longValue();

                    // .tip (term index)
                    var cf = (Map<String, Object>) _ioMeta.get("compoundFile");
                    var entries = (List<Object>) cf.get("entries");
                    for (Object e : entries) {
                        var entry = (Map<String, Object>) e;
                        String comp = (String) entry.get("component");
                        if (comp.contains(".tip")) {
                            blocks.add(new IOBlock(file, ".tip",
                                ((Number) entry.get("offset")).longValue(),
                                ((Number) entry.get("length")).longValue(),
                                "Term index (find '" + filterTerm + "')"));
                            break;
                        }
                    }

                    // .tim (term dictionary block)
                    if (termInfo.containsKey("timOffset") && termInfo.containsKey("timLength")) {
                        blocks.add(new IOBlock(file, ".tim (block)",
                            ((Number) termInfo.get("timOffset")).longValue(),
                            ((Number) termInfo.get("timLength")).longValue(),
                            "Term '" + filterTerm + "' metadata"));
                    }

                    // .doc (postings)
                    if (termInfo.containsKey("docOffset") && termInfo.containsKey("docLength")) {
                        blocks.add(new IOBlock(file, ".doc (postings)",
                            ((Number) termInfo.get("docOffset")).longValue(),
                            ((Number) termInfo.get("docLength")).longValue(),
                            "Doc IDs matching '" + filterTerm + "' (" + matchingDocs + " docs)"));
                    }
                } else {
                    warnings.add("Term '" + filterTerm + "' not found in metadata");
                }
            }
        } else {
            warnings.add("Filter field '" + filterField + "' not found");
        }
    }

    // Part 2: DocValue blocks for SUM
    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    if (dvFields != null) {
        var sumFieldInfo = (Map<String, Object>) dvFields.get(sumField);
        if (sumFieldInfo != null) {
            if (sumFieldInfo.containsKey("metaOffset")) {
                blocks.add(new IOBlock(file, ".dvm",
                    ((Number) sumFieldInfo.get("metaOffset")).longValue(),
                    ((Number) sumFieldInfo.get("metaLength")).longValue(),
                    "DocValues metadata"));
            }

            if (sumFieldInfo.containsKey("dataOffset")) {
                blocks.add(new IOBlock(file, ".dvd (" + sumField + ")",
                    ((Number) sumFieldInfo.get("dataOffset")).longValue(),
                    ((Number) sumFieldInfo.get("dataLength")).longValue(),
                    "Read " + sumField + " for " + matchingDocs + " matching docs"));
            }
        } else {
            warnings.add("Sum field '" + sumField + "' not found in docValueFields");
        }
    }

    String operation = "SUM(" + sumField + ") WHERE " + filterField + ":" + filterTerm;
    _printPlan(operation, blocks, warnings);

    if (matchingDocs > 0) {
        System.out.println("Matching docs: " + matchingDocs);
    }
}

// ============================================================================
// COMPOSABLE I/O PLANNING
// ============================================================================
// Instead of hardcoding every combination, use component-based planning:
//   plan("range:price:10000:*", "terms:color", "histogram:year:1", "avg:mileage")
//
// Component syntax:
//   Filter:  "range:field:min:max" or "term:field:value" or "match_all"
//   Bucket:  "terms:field" or "histogram:field:interval"
//   Metric:  "sum:field", "avg:field", "p95:field", "count", etc.
// ============================================================================

// Track which data blocks we've already added (avoid duplicates)
Set<String> _addedBlocks = new HashSet<>();

// Get blocks for a NUMERIC docvalue field (for range filter, histogram, or metric)
@SuppressWarnings("unchecked")
List<IOBlock> _blocksForNumeric(String field, String purpose) {
    List<IOBlock> blocks = new ArrayList<>();
    String file = _getCompoundFile();

    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    if (dvFields == null) return blocks;

    var fieldInfo = (Map<String, Object>) dvFields.get(field);
    if (fieldInfo == null) return blocks;

    // Add .dvm once
    if (!_addedBlocks.contains(".dvm") && fieldInfo.containsKey("metaOffset")) {
        blocks.add(new IOBlock(file, ".dvm",
            ((Number) fieldInfo.get("metaOffset")).longValue(),
            ((Number) fieldInfo.get("metaLength")).longValue(),
            "DocValues metadata"));
        _addedBlocks.add(".dvm");
    }

    // Add .dvd for this field
    String blockKey = ".dvd:" + field;
    if (!_addedBlocks.contains(blockKey) && fieldInfo.containsKey("dataOffset")) {
        blocks.add(new IOBlock(file, ".dvd (" + field + ")",
            ((Number) fieldInfo.get("dataOffset")).longValue(),
            ((Number) fieldInfo.get("dataLength")).longValue(),
            purpose));
        _addedBlocks.add(blockKey);
    }

    return blocks;
}

// Get blocks for a SORTED docvalue field (for terms bucket)
// ordsOnly=true skips term dictionary (only need ordinals for grouping)
@SuppressWarnings("unchecked")
List<IOBlock> _blocksForSorted(String field, String purpose, boolean ordsOnly) {
    List<IOBlock> blocks = new ArrayList<>();
    String file = _getCompoundFile();

    var dvFields = (Map<String, Object>) _ioMeta.get("docValueFields");
    if (dvFields == null) return blocks;

    var fieldInfo = (Map<String, Object>) dvFields.get(field);
    if (fieldInfo == null) return blocks;

    // Add .dvm once
    if (!_addedBlocks.contains(".dvm") && fieldInfo.containsKey("metaOffset")) {
        blocks.add(new IOBlock(file, ".dvm",
            ((Number) fieldInfo.get("metaOffset")).longValue(),
            ((Number) fieldInfo.get("metaLength")).longValue(),
            "DocValues metadata"));
        _addedBlocks.add(".dvm");
    }

    // Ordinals
    String ordsKey = ".dvd:" + field + ":ords";
    if (!_addedBlocks.contains(ordsKey) && fieldInfo.containsKey("ordsOffset")) {
        blocks.add(new IOBlock(file, ".dvd (" + field + " ords)",
            ((Number) fieldInfo.get("ordsOffset")).longValue(),
            ((Number) fieldInfo.get("ordsLength")).longValue(),
            purpose + ": doc -> ordinal"));
        _addedBlocks.add(ordsKey);
    }

    // Terms (skip if ordsOnly)
    if (!ordsOnly) {
        String termsKey = ".dvd:" + field + ":terms";
        if (!_addedBlocks.contains(termsKey) && fieldInfo.containsKey("termsOffset")) {
            blocks.add(new IOBlock(file, ".dvd (" + field + " terms)",
                ((Number) fieldInfo.get("termsOffset")).longValue(),
                ((Number) fieldInfo.get("termsLength")).longValue(),
                purpose + ": ordinal -> value"));
            _addedBlocks.add(termsKey);
        }
    }

    return blocks;
}

// Get blocks for term query (inverted index)
@SuppressWarnings("unchecked")
List<IOBlock> _blocksForTermQuery(String field, String term) {
    List<IOBlock> blocks = new ArrayList<>();
    String file = _getCompoundFile();

    var indexedFields = (Map<String, Object>) _ioMeta.get("indexedFields");
    if (indexedFields == null) return blocks;

    var fieldInfo = (Map<String, Object>) indexedFields.get(field);
    if (fieldInfo == null) return blocks;

    var terms = (Map<String, Object>) fieldInfo.get("terms");
    if (terms == null) return blocks;

    var termInfo = (Map<String, Object>) terms.get(term);
    if (termInfo == null) return blocks;

    // .tip
    if (!_addedBlocks.contains(".tip")) {
        var cf = (Map<String, Object>) _ioMeta.get("compoundFile");
        var entries = (List<Object>) cf.get("entries");
        for (Object e : entries) {
            var entry = (Map<String, Object>) e;
            String comp = (String) entry.get("component");
            if (comp.contains(".tip")) {
                blocks.add(new IOBlock(file, ".tip",
                    ((Number) entry.get("offset")).longValue(),
                    ((Number) entry.get("length")).longValue(),
                    "Term index"));
                _addedBlocks.add(".tip");
                break;
            }
        }
    }

    // .tim block for this term
    String timKey = ".tim:" + field + ":" + term;
    if (!_addedBlocks.contains(timKey) && termInfo.containsKey("timOffset")) {
        blocks.add(new IOBlock(file, ".tim (block)",
            ((Number) termInfo.get("timOffset")).longValue(),
            ((Number) termInfo.get("timLength")).longValue(),
            "Term '" + term + "' metadata"));
        _addedBlocks.add(timKey);
    }

    // .doc postings
    String docKey = ".doc:" + field + ":" + term;
    if (!_addedBlocks.contains(docKey) && termInfo.containsKey("docOffset")) {
        long docFreq = ((Number) termInfo.get("docFreq")).longValue();
        long length = termInfo.containsKey("docLength") ?
            ((Number) termInfo.get("docLength")).longValue() : docFreq * 2;
        blocks.add(new IOBlock(file, ".doc (postings)",
            ((Number) termInfo.get("docOffset")).longValue(),
            length,
            "Posting list (" + docFreq + " docs)"));
        _addedBlocks.add(docKey);
    }

    return blocks;
}

// Main composable planning function
// Components: "range:field:min:max", "term:field:value", "terms:field",
//            "histogram:field:interval", "sum:field", "avg:field", "p95:field", etc.
@SuppressWarnings("unchecked")
void plan(String... components) {
    _checkMeta();
    _addedBlocks.clear();

    List<IOBlock> allBlocks = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> opParts = new ArrayList<>();

    for (String comp : components) {
        String[] parts = comp.split(":");
        String type = parts[0].toLowerCase();

        switch (type) {
            case "match_all":
                opParts.add("*");
                break;

            case "range":
                if (parts.length >= 3) {
                    String field = parts[1];
                    String minStr = parts.length > 2 ? parts[2] : "*";
                    String maxStr = parts.length > 3 ? parts[3] : "*";
                    String rangeDesc = minStr.equals("*") ? field + " < " + maxStr :
                                      maxStr.equals("*") ? field + " > " + minStr :
                                      field + ":[" + minStr + " TO " + maxStr + "]";
                    allBlocks.addAll(_blocksForNumeric(field, "Filter: " + rangeDesc));
                    opParts.add("WHERE " + rangeDesc);
                }
                break;

            case "term":
                if (parts.length >= 3) {
                    String field = parts[1];
                    String term = parts[2];
                    allBlocks.addAll(_blocksForTermQuery(field, term));
                    opParts.add("WHERE " + field + ":" + term);
                }
                break;

            case "terms":
                if (parts.length >= 2) {
                    String field = parts[1];
                    // "terms:field" includes term dictionary, "terms:field:ords" skips it
                    boolean ordsOnly = parts.length >= 3 && parts[2].equals("ords");
                    allBlocks.addAll(_blocksForSorted(field, "Bucket terms(" + field + ")", ordsOnly));
                    opParts.add(ordsOnly ? "ords(" + field + ")" : "terms(" + field + ")");
                }
                break;

            case "ords":
                // Shorthand for "terms:field:ords" - ordinals only, no term dictionary
                if (parts.length >= 2) {
                    String field = parts[1];
                    allBlocks.addAll(_blocksForSorted(field, "Bucket ords(" + field + ")", true));
                    opParts.add("ords(" + field + ")");
                }
                break;

            case "histogram":
                if (parts.length >= 3) {
                    String field = parts[1];
                    String interval = parts[2];
                    allBlocks.addAll(_blocksForNumeric(field, "Bucket histogram(" + field + ", " + interval + ")"));
                    opParts.add("histogram(" + field + "," + interval + ")");
                }
                break;

            case "sum": case "avg": case "min": case "max": case "count":
            case "p50": case "p75": case "p90": case "p95": case "p99":
                if (parts.length >= 2) {
                    String field = parts[1];
                    allBlocks.addAll(_blocksForNumeric(field, "Metric: " + type + "(" + field + ")"));
                    opParts.add(type + "(" + field + ")");
                } else if (type.equals("count")) {
                    opParts.add("count");
                }
                break;

            default:
                warnings.add("Unknown component type: " + type);
        }
    }

    String operation = String.join(" > ", opParts);
    _printPlan(operation, allBlocks, warnings);
}

System.out.println("Loaded I/O Planner v2 (metadata-only mode).");
System.out.println("");
System.out.println("First, generate metadata: /open skills/io-metadata.jsh && generateIOMetadata()");
System.out.println("Then load it: loadIOMetadata(\"io-metadata.json\")");
System.out.println("");
System.out.println("=== COMPOSABLE PLANNING (recommended) ===");
System.out.println("  plan(components...)  - Build any aggregation from components");
System.out.println("");
System.out.println("  Components:");
System.out.println("    Filter:  \"range:field:min:max\", \"term:field:value\", \"match_all\"");
System.out.println("    Bucket:  \"terms:field\", \"histogram:field:interval\"");
System.out.println("    Metric:  \"sum:field\", \"avg:field\", \"p95:field\", \"count\"");
System.out.println("");
System.out.println("  Examples:");
System.out.println("    plan(\"range:price:10000:*\", \"terms:color\", \"terms:makemodel\", \"p95:price\")");
System.out.println("    plan(\"term:color_indexed:red\", \"histogram:year:1\", \"avg:mileage\")");
System.out.println("    plan(\"match_all\", \"terms:category\", \"sum:price\")");
System.out.println("");
System.out.println("=== SIMPLE FUNCTIONS ===");
System.out.println("  listFields()                - List available fields");
System.out.println("  listTerms(field)            - List terms for a field");
System.out.println("  planSum(field)              - Plan SUM aggregation");
System.out.println("  planTermQuery(field, term)  - Plan term query");
System.out.println("  planRangeQuery(field, min, max) - Plan range query");
System.out.println("  planTermsAgg(field)         - Plan terms aggregation");
System.out.println("  planGetDoc(docId)           - Plan stored fields retrieval");
System.out.println("  planFilteredSum(filter, term, sumField) - Plan filtered SUM");
