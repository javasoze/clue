// io-metadata.jsh
// Generate comprehensive I/O metadata for an index
// This reads the index ONCE and saves all offset information to a JSON file
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/io-metadata.jsh
//   jshell> generateIOMetadata()    // Creates io-metadata.json
//   jshell> closeMetadataGen()

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.index.*;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.util.BytesRef;
import java.nio.file.*;
import java.util.*;
import java.time.Instant;

Directory _metaDir = null;
DirectoryReader _metaReader = null;

void _openMetaGen() throws Exception {
    if (_metaDir != null) return;
    _metaDir = FSDirectory.open(Path.of(indexPath));
    _metaReader = DirectoryReader.open(_metaDir);
}

void closeMetadataGen() throws Exception {
    if (_metaReader != null) { _metaReader.close(); _metaReader = null; }
    if (_metaDir != null) { _metaDir.close(); _metaDir = null; }
    System.out.println("Metadata generator closed.");
}

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

String escJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
}

void generateIOMetadata() throws Exception {
    _openMetaGen();

    var sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"indexPath\": \"").append(escJson(indexPath)).append("\",\n");
    sb.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
    sb.append("  \"numDocs\": ").append(_metaReader.numDocs()).append(",\n");
    sb.append("  \"maxDoc\": ").append(_metaReader.maxDoc()).append(",\n");

    // === Compound File Entries ===
    System.out.println("Reading compound file entries...");
    sb.append("  \"compoundFile\": {\n");
    sb.append("    \"file\": \"_0.cfs\",\n");
    sb.append("    \"entries\": [\n");

    Map<String, long[]> compoundEntries = new LinkedHashMap<>();
    String cfeFile = "_0.cfe";
    if (Arrays.asList(_metaDir.listAll()).contains(cfeFile)) {
        IndexInput input = _metaDir.openInput(cfeFile, IOContext.DEFAULT);
        try {
            int magic = input.readInt();
            if (magic != CodecUtil.CODEC_MAGIC) magic = Integer.reverseBytes(magic);
            input.readString();
            input.readInt();
            byte[] objectId = new byte[16];
            input.readBytes(objectId, 0, 16);
            int suffixLen = input.readByte() & 0xFF;
            if (suffixLen > 0) input.skipBytes(suffixLen);

            int numEntries = input.readVInt();
            List<String> entryLines = new ArrayList<>();
            for (int i = 0; i < numEntries; i++) {
                String fileName = input.readString();
                long offset = input.readLong();
                long length = input.readLong();
                compoundEntries.put(fileName, new long[]{offset, length});
                entryLines.add(String.format("      {\"component\": \"%s\", \"offset\": %d, \"length\": %d}",
                    fileName, offset, length));
            }
            sb.append(String.join(",\n", entryLines)).append("\n");
        } finally {
            input.close();
        }
    }
    sb.append("    ]\n");
    sb.append("  },\n");

    // === Fields with Terms (Indexed) ===
    System.out.println("Reading indexed fields and terms...");
    sb.append("  \"indexedFields\": {\n");

    var leaf = _metaReader.leaves().get(0).reader();
    List<String> indexedFieldJsons = new ArrayList<>();

    for (FieldInfo fi : leaf.getFieldInfos()) {
        if (fi.getIndexOptions() == IndexOptions.NONE) continue;

        Terms terms = leaf.terms(fi.name);
        if (terms == null) continue;

        System.out.println("  Processing field: " + fi.name);

        var fieldSb = new StringBuilder();
        fieldSb.append("    \"").append(escJson(fi.name)).append("\": {\n");
        fieldSb.append("      \"totalTerms\": ").append(terms.size()).append(",\n");
        fieldSb.append("      \"docCount\": ").append(terms.getDocCount()).append(",\n");
        fieldSb.append("      \"sumDocFreq\": ").append(terms.getSumDocFreq()).append(",\n");
        fieldSb.append("      \"terms\": {\n");

        // Iterate all terms to get exact offsets
        List<String> termJsons = new ArrayList<>();
        TermsEnum te = terms.iterator();
        BytesRef term;

        // Collect all term info first
        List<Map<String, Object>> termInfos = new ArrayList<>();
        while ((term = te.next()) != null) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("term", term.utf8ToString());
            info.put("docFreq", te.docFreq());

            var frame = _getField(te, "currentFrame");
            if (frame != null) {
                var fp = _getField(frame, "fp");
                var fpEnd = _getField(frame, "fpEnd");
                if (fp != null) info.put("timOffset", ((Number)fp).longValue());
                if (fpEnd != null) info.put("timOffsetEnd", ((Number)fpEnd).longValue());

                var state = _getField(frame, "state");
                if (state != null) {
                    var docFP = _getField(state, "docStartFP");
                    var posFP = _getField(state, "posStartFP");
                    var payFP = _getField(state, "payStartFP");
                    if (docFP != null) info.put("docOffset", ((Number)docFP).longValue());
                    if (posFP != null && ((Number)posFP).longValue() > 0)
                        info.put("posOffset", ((Number)posFP).longValue());
                    if (payFP != null && ((Number)payFP).longValue() > 0)
                        info.put("payOffset", ((Number)payFP).longValue());
                }
            }
            termInfos.add(info);
        }

        // Sort by docOffset to calculate lengths
        List<Long> docOffsets = new ArrayList<>();
        for (var info : termInfos) {
            if (info.containsKey("docOffset")) {
                docOffsets.add((Long) info.get("docOffset"));
            }
        }
        Collections.sort(docOffsets);

        // Build offset -> length map
        Map<Long, Long> docOffsetToLen = new HashMap<>();
        for (int i = 0; i < docOffsets.size() - 1; i++) {
            if (!docOffsets.get(i).equals(docOffsets.get(i + 1))) {
                docOffsetToLen.put(docOffsets.get(i), docOffsets.get(i + 1) - docOffsets.get(i));
            }
        }

        // Generate JSON for each term with calculated lengths
        for (var info : termInfos) {
            var termJson = new StringBuilder();
            String termText = (String) info.get("term");
            termJson.append("        \"").append(escJson(termText)).append("\": {");
            termJson.append("\"docFreq\": ").append(info.get("docFreq"));

            if (info.containsKey("timOffset")) {
                long timOff = (Long) info.get("timOffset");
                long timEnd = info.containsKey("timOffsetEnd") ? (Long) info.get("timOffsetEnd") : timOff;
                // Add compound file base offset
                long[] timLoc = compoundEntries.get("_Lucene101_0.tim");
                if (timLoc != null) {
                    termJson.append(", \"timOffset\": ").append(timLoc[0] + timOff);
                    termJson.append(", \"timLength\": ").append(timEnd - timOff);
                }
            }

            if (info.containsKey("docOffset")) {
                long docOff = (Long) info.get("docOffset");
                Long docLen = docOffsetToLen.get(docOff);
                long[] docLoc = compoundEntries.get("_Lucene101_0.doc");
                if (docLoc != null) {
                    termJson.append(", \"docOffset\": ").append(docLoc[0] + docOff);
                    if (docLen != null) {
                        termJson.append(", \"docLength\": ").append(docLen);
                    }
                }
            }

            if (info.containsKey("posOffset")) {
                long posOff = (Long) info.get("posOffset");
                long[] posLoc = compoundEntries.get("_Lucene101_0.pos");
                if (posLoc != null) {
                    termJson.append(", \"posOffset\": ").append(posLoc[0] + posOff);
                }
            }

            termJson.append("}");
            termJsons.add(termJson.toString());
        }

        fieldSb.append(String.join(",\n", termJsons)).append("\n");
        fieldSb.append("      }\n");
        fieldSb.append("    }");
        indexedFieldJsons.add(fieldSb.toString());
    }

    sb.append(String.join(",\n", indexedFieldJsons)).append("\n");
    sb.append("  },\n");

    // === DocValues Fields ===
    System.out.println("Reading docvalue fields...");
    sb.append("  \"docValueFields\": {\n");

    var codecReader = (CodecReader) leaf;
    var dvProducer = codecReader.getDocValuesReader();
    var formats = (java.util.HashMap) _getField(dvProducer, "formats");
    var producer = formats.values().iterator().next();

    long[] dvdLoc = compoundEntries.get("_Lucene90_0.dvd");
    long[] dvmLoc = compoundEntries.get("_Lucene90_0.dvm");

    List<String> dvFieldJsons = new ArrayList<>();

    // Numeric DocValues
    var numerics = _getField(producer, "numerics");
    if (numerics != null) {
        var getMethod = numerics.getClass().getMethod("get", int.class);
        for (FieldInfo fi : leaf.getFieldInfos()) {
            if (fi.getDocValuesType() == DocValuesType.NUMERIC) {
                var entry = getMethod.invoke(numerics, fi.number);
                if (entry != null) {
                    var valOff = _getField(entry, "valuesOffset");
                    var valLen = _getField(entry, "valuesLength");
                    var numVals = _getField(entry, "numValues");

                    var dvJson = new StringBuilder();
                    dvJson.append("    \"").append(escJson(fi.name)).append("\": {\n");
                    dvJson.append("      \"type\": \"NUMERIC\",\n");
                    dvJson.append("      \"numValues\": ").append(numVals).append(",\n");
                    if (dvdLoc != null && valOff != null) {
                        dvJson.append("      \"dataOffset\": ").append(dvdLoc[0] + ((Number)valOff).longValue()).append(",\n");
                        dvJson.append("      \"dataLength\": ").append(valLen).append(",\n");
                    }
                    if (dvmLoc != null) {
                        dvJson.append("      \"metaOffset\": ").append(dvmLoc[0]).append(",\n");
                        dvJson.append("      \"metaLength\": ").append(dvmLoc[1]).append("\n");
                    }
                    dvJson.append("    }");
                    dvFieldJsons.add(dvJson.toString());
                }
            }
        }
    }

    // Sorted DocValues
    var sorted = _getField(producer, "sorted");
    if (sorted != null) {
        var getMethod = sorted.getClass().getMethod("get", int.class);
        for (FieldInfo fi : leaf.getFieldInfos()) {
            if (fi.getDocValuesType() == DocValuesType.SORTED) {
                var entry = getMethod.invoke(sorted, fi.number);
                if (entry != null) {
                    var dvJson = new StringBuilder();
                    dvJson.append("    \"").append(escJson(fi.name)).append("\": {\n");
                    dvJson.append("      \"type\": \"SORTED\",\n");

                    var ordsEntry = _getField(entry, "ordsEntry");
                    if (ordsEntry != null && dvdLoc != null) {
                        var ordsOff = _getField(ordsEntry, "valuesOffset");
                        var ordsLen = _getField(ordsEntry, "valuesLength");
                        if (ordsOff != null) {
                            dvJson.append("      \"ordsOffset\": ").append(dvdLoc[0] + ((Number)ordsOff).longValue()).append(",\n");
                            dvJson.append("      \"ordsLength\": ").append(ordsLen).append(",\n");
                        }
                    }

                    var termsEntry = _getField(entry, "termsDictEntry");
                    if (termsEntry != null && dvdLoc != null) {
                        var termsOff = _getField(termsEntry, "termsDataOffset");
                        var termsLen = _getField(termsEntry, "termsDataLength");
                        if (termsOff != null) {
                            dvJson.append("      \"termsOffset\": ").append(dvdLoc[0] + ((Number)termsOff).longValue()).append(",\n");
                            dvJson.append("      \"termsLength\": ").append(termsLen).append(",\n");
                        }
                    }

                    if (dvmLoc != null) {
                        dvJson.append("      \"metaOffset\": ").append(dvmLoc[0]).append(",\n");
                        dvJson.append("      \"metaLength\": ").append(dvmLoc[1]).append("\n");
                    }
                    dvJson.append("    }");
                    dvFieldJsons.add(dvJson.toString());
                }
            }
        }
    }

    // SortedSet DocValues
    var sortedSets = _getField(producer, "sortedSets");
    if (sortedSets != null) {
        var getMethod = sortedSets.getClass().getMethod("get", int.class);
        for (FieldInfo fi : leaf.getFieldInfos()) {
            if (fi.getDocValuesType() == DocValuesType.SORTED_SET) {
                var entry = getMethod.invoke(sortedSets, fi.number);
                if (entry != null) {
                    var dvJson = new StringBuilder();
                    dvJson.append("    \"").append(escJson(fi.name)).append("\": {\n");
                    dvJson.append("      \"type\": \"SORTED_SET\",\n");
                    if (dvmLoc != null) {
                        dvJson.append("      \"metaOffset\": ").append(dvmLoc[0]).append(",\n");
                        dvJson.append("      \"metaLength\": ").append(dvmLoc[1]).append("\n");
                    }
                    dvJson.append("    }");
                    dvFieldJsons.add(dvJson.toString());
                }
            }
        }
    }

    // Binary DocValues
    var binaries = _getField(producer, "binaries");
    if (binaries != null) {
        var getMethod = binaries.getClass().getMethod("get", int.class);
        for (FieldInfo fi : leaf.getFieldInfos()) {
            if (fi.getDocValuesType() == DocValuesType.BINARY) {
                var entry = getMethod.invoke(binaries, fi.number);
                if (entry != null) {
                    var dvJson = new StringBuilder();
                    dvJson.append("    \"").append(escJson(fi.name)).append("\": {\n");
                    dvJson.append("      \"type\": \"BINARY\",\n");

                    var dataOff = _getField(entry, "dataOffset");
                    var dataLen = _getField(entry, "dataLength");
                    if (dvdLoc != null && dataOff != null) {
                        dvJson.append("      \"dataOffset\": ").append(dvdLoc[0] + ((Number)dataOff).longValue()).append(",\n");
                        dvJson.append("      \"dataLength\": ").append(dataLen).append(",\n");
                    }
                    if (dvmLoc != null) {
                        dvJson.append("      \"metaOffset\": ").append(dvmLoc[0]).append(",\n");
                        dvJson.append("      \"metaLength\": ").append(dvmLoc[1]).append("\n");
                    }
                    dvJson.append("    }");
                    dvFieldJsons.add(dvJson.toString());
                }
            }
        }
    }

    sb.append(String.join(",\n", dvFieldJsons)).append("\n");
    sb.append("  },\n");

    // === Stored Fields Chunks ===
    System.out.println("Reading stored fields chunks...");
    sb.append("  \"storedFields\": {\n");

    var fieldsReader = codecReader.getFieldsReader();
    var indexReader = _getField(fieldsReader, "indexReader");
    var startPointers = _getField(indexReader, "startPointers");
    var docs = _getField(indexReader, "docs");
    var numChunks = ((Number) _getField(indexReader, "numChunks")).intValue();
    var maxPointer = ((Number) _getField(fieldsReader, "maxPointer")).longValue();

    long[] fdtLoc = compoundEntries.get(".fdt");
    long[] fdxLoc = compoundEntries.get(".fdx");
    long[] fdmLoc = compoundEntries.get(".fdm");

    if (fdxLoc != null) {
        sb.append("    \"indexOffset\": ").append(fdxLoc[0]).append(",\n");
        sb.append("    \"indexLength\": ").append(fdxLoc[1]).append(",\n");
    }
    if (fdmLoc != null) {
        sb.append("    \"metaOffset\": ").append(fdmLoc[0]).append(",\n");
        sb.append("    \"metaLength\": ").append(fdmLoc[1]).append(",\n");
    }

    sb.append("    \"chunks\": [\n");
    var getMethod = startPointers.getClass().getMethod("get", long.class);
    var getDocsMethod = docs.getClass().getMethod("get", long.class);

    List<String> chunkJsons = new ArrayList<>();
    for (int c = 0; c < numChunks; c++) {
        long chunkOffset = ((Number) getMethod.invoke(startPointers, (long) c)).longValue();
        long firstDoc = ((Number) getDocsMethod.invoke(docs, (long) c)).longValue();
        long chunkEnd = (c < numChunks - 1) ?
            ((Number) getMethod.invoke(startPointers, (long)(c + 1))).longValue() : maxPointer;
        long chunkLen = chunkEnd - chunkOffset;

        long absOffset = fdtLoc != null ? fdtLoc[0] + chunkOffset : chunkOffset;
        chunkJsons.add(String.format("      {\"chunk\": %d, \"firstDoc\": %d, \"offset\": %d, \"length\": %d}",
            c, firstDoc, absOffset, chunkLen));
    }
    sb.append(String.join(",\n", chunkJsons)).append("\n");
    sb.append("    ]\n");
    sb.append("  }\n");

    sb.append("}\n");

    // Write to file
    var metaFile = Path.of("io-metadata.json");
    Files.writeString(metaFile, sb.toString());
    System.out.println("\nGenerated: " + metaFile.toAbsolutePath());
    System.out.println("Size: " + sb.length() + " bytes");
}

System.out.println("Loaded I/O Metadata Generator.");
System.out.println("Functions:");
System.out.println("  generateIOMetadata()  - Generate io-metadata.json with exact offsets");
System.out.println("  closeMetadataGen()    - Close resources");
