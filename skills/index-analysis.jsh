// index-analysis.jsh
// Unified interface for index file analysis - generates comprehensive metadata
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/index-analysis.jsh
//   jshell> analyzeIndex()
//   jshell> generateMeta()
//   jshell> closeAnalysis()
//
// Requires: compound-files.jsh, term-offsets.jsh, docvalue-offsets.jsh
// These will be loaded automatically

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.util.BytesRef;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.lang.reflect.Field;

// Cached resources
Directory _analysisDir = null;
DirectoryReader _analysisReader = null;

// Open index
void _openAnalysis() throws Exception {
    if (_analysisDir != null) return;
    _analysisDir = FSDirectory.open(Path.of(indexPath));
    _analysisReader = DirectoryReader.open(_analysisDir);
}

// Close resources
void closeAnalysis() throws Exception {
    if (_analysisReader != null) {
        _analysisReader.close();
        _analysisReader = null;
    }
    if (_analysisDir != null) {
        _analysisDir.close();
        _analysisDir = null;
    }
    System.out.println("Analysis index closed.");
}

// Reflection helper
Object getFieldValue(Object obj, String fieldName) {
    Class<?> clazz = obj.getClass();
    while (clazz != null) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
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

// Analyze index and print summary
void analyzeIndex() throws Exception {
    _openAnalysis();

    System.out.println("=" .repeat(70));
    System.out.println("INDEX ANALYSIS: " + indexPath);
    System.out.println("=".repeat(70));

    // Basic index info
    System.out.println("\nIndex Statistics:");
    System.out.println("  Total documents: " + _analysisReader.numDocs());
    System.out.println("  Max doc: " + _analysisReader.maxDoc());
    System.out.println("  Deleted docs: " + _analysisReader.numDeletedDocs());
    System.out.println("  Segments: " + _analysisReader.leaves().size());

    // Codec info
    SegmentInfos sis = SegmentInfos.readLatestCommit(_analysisDir);
    for (SegmentCommitInfo sci : sis) {
        Codec codec = sci.info.getCodec();
        System.out.println("  Codec: " + codec.getName() + " (" + codec.getClass().getName() + ")");
        break;
    }

    // List all files
    System.out.println("\nIndex Files:");
    String[] files = _analysisDir.listAll();
    Arrays.sort(files);
    long totalSize = 0;
    for (String file : files) {
        if (file.equals("write.lock")) continue;
        long size = _analysisDir.fileLength(file);
        totalSize += size;
        System.out.printf("  %-30s %,15d bytes%n", file, size);
    }
    System.out.printf("  %-30s %,15d bytes%n", "TOTAL", totalSize);

    // Field summary
    System.out.println("\nFields:");
    Map<String, String[]> fieldInfo = new TreeMap<>();
    for (LeafReaderContext leaf : _analysisReader.leaves()) {
        FieldInfos fis = leaf.reader().getFieldInfos();
        for (FieldInfo fi : fis) {
            String indexed = fi.getIndexOptions() != IndexOptions.NONE ? "indexed" : "";
            String dv = fi.getDocValuesType() != DocValuesType.NONE ? fi.getDocValuesType().name() : "";
            String stored = fi.hasVectorValues() ? "vectors" : "";
            fieldInfo.put(fi.name, new String[]{indexed, dv, stored});
        }
    }
    System.out.printf("  %-25s %-12s %-15s%n", "Field", "Indexed", "DocValues");
    System.out.println("  " + "-".repeat(55));
    for (var entry : fieldInfo.entrySet()) {
        String[] info = entry.getValue();
        System.out.printf("  %-25s %-12s %-15s%n", entry.getKey(), info[0], info[1]);
    }

    System.out.println("\n" + "=".repeat(70));
}

// Generate comprehensive meta JSON file
void generateMeta() throws Exception {
    _openAnalysis();

    var metaFile = Path.of("index-meta.json");
    var sb = new StringBuilder();

    sb.append("{\n");
    sb.append("  \"indexPath\": \"").append(escapeJson(indexPath)).append("\",\n");

    // Codec info
    SegmentInfos sis = SegmentInfos.readLatestCommit(_analysisDir);
    String codecName = "";
    String codecClass = "";
    for (SegmentCommitInfo sci : sis) {
        Codec codec = sci.info.getCodec();
        codecName = codec.getName();
        codecClass = codec.getClass().getName();
        break;
    }
    sb.append("  \"codec\": \"").append(codecName).append("\",\n");
    sb.append("  \"codecClass\": \"").append(codecClass).append("\",\n");
    sb.append("  \"numDocs\": ").append(_analysisReader.numDocs()).append(",\n");
    sb.append("  \"maxDoc\": ").append(_analysisReader.maxDoc()).append(",\n");

    // Segments
    sb.append("  \"segments\": [\n");
    List<LeafReaderContext> leaves = _analysisReader.leaves();
    for (int i = 0; i < leaves.size(); i++) {
        LeafReaderContext leaf = leaves.get(i);
        String segName = "_" + leaf.ord;

        // Try to find actual segment name from files
        for (String file : _analysisDir.listAll()) {
            if (file.endsWith(".si") && !file.startsWith("segments")) {
                segName = file.substring(0, file.length() - 3);
                break;
            }
        }

        sb.append("    {\n");
        sb.append("      \"name\": \"").append(segName).append("\",\n");
        sb.append("      \"docCount\": ").append(leaf.reader().numDocs()).append(",\n");
        sb.append("      \"maxDoc\": ").append(leaf.reader().maxDoc()).append(",\n");

        // Compound file entries if available
        String cfeFile = segName + ".cfe";
        if (Arrays.asList(_analysisDir.listAll()).contains(cfeFile)) {
            sb.append("      \"compoundEntries\": ");
            sb.append(getCompoundEntriesJson(segName));
        }

        sb.append("\n    }");
        if (i < leaves.size() - 1) sb.append(",");
        sb.append("\n");
    }
    sb.append("  ],\n");

    // Fields
    sb.append("  \"fields\": [\n");
    Map<String, FieldInfo> allFields = new TreeMap<>();
    for (LeafReaderContext leaf : _analysisReader.leaves()) {
        for (FieldInfo fi : leaf.reader().getFieldInfos()) {
            allFields.put(fi.name, fi);
        }
    }

    int fieldIdx = 0;
    for (var entry : allFields.entrySet()) {
        FieldInfo fi = entry.getValue();
        sb.append("    {\n");
        sb.append("      \"name\": \"").append(escapeJson(fi.name)).append("\",\n");
        sb.append("      \"indexed\": ").append(fi.getIndexOptions() != IndexOptions.NONE).append(",\n");
        sb.append("      \"indexOptions\": \"").append(fi.getIndexOptions()).append("\",\n");
        sb.append("      \"docValuesType\": \"").append(fi.getDocValuesType()).append("\",\n");
        sb.append("      \"hasNorms\": ").append(fi.hasNorms()).append(",\n");
        sb.append("      \"hasPayloads\": ").append(fi.hasPayloads()).append(",\n");
        sb.append("      \"hasTermVectors\": ").append(fi.hasTermVectors()).append("\n");
        sb.append("    }");
        if (++fieldIdx < allFields.size()) sb.append(",");
        sb.append("\n");
    }
    sb.append("  ]\n");

    sb.append("}\n");

    Files.writeString(metaFile, sb.toString());
    System.out.println("Generated: " + metaFile.toAbsolutePath());
    System.out.println("Size: " + sb.length() + " bytes");
}

// Helper to escape JSON strings
String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
}

// Get compound entries as JSON array
String getCompoundEntriesJson(String segment) throws Exception {
    String cfeFile = segment + ".cfe";
    if (!Arrays.asList(_analysisDir.listAll()).contains(cfeFile)) {
        return "[]";
    }

    var sb = new StringBuilder();
    sb.append("[\n");

    IndexInput input = _analysisDir.openInput(cfeFile, IOContext.DEFAULT);
    try {
        // Read header
        int magic = input.readInt();
        if (magic != CodecUtil.CODEC_MAGIC) {
            int reversed = Integer.reverseBytes(magic);
            if (reversed != CodecUtil.CODEC_MAGIC) {
                return "[]";
            }
        }

        input.readString(); // codec name
        input.readInt(); // version

        byte[] objectId = new byte[16];
        input.readBytes(objectId, 0, 16);

        int suffixLength = input.readByte() & 0xFF;
        if (suffixLength > 0) {
            byte[] suffix = new byte[suffixLength];
            input.readBytes(suffix, 0, suffixLength);
        }

        int numEntries = input.readVInt();
        List<String> entries = new ArrayList<>();

        for (int i = 0; i < numEntries; i++) {
            String fileName = input.readString();
            long offset = input.readLong();
            long length = input.readLong();
            entries.add(String.format("        {\"file\": \"%s\", \"offset\": %d, \"length\": %d}",
                fileName, offset, length));
        }

        sb.append(String.join(",\n", entries));
        sb.append("\n      ]");

    } finally {
        input.close();
    }

    return sb.toString();
}

// Quick lookup functions
void fieldFiles(String fieldName) throws Exception {
    _openAnalysis();

    for (LeafReaderContext leaf : _analysisReader.leaves()) {
        FieldInfo fi = leaf.reader().getFieldInfos().fieldInfo(fieldName);
        if (fi == null) {
            System.out.println("Field not found: " + fieldName);
            return;
        }

        System.out.println("Field: " + fieldName);
        System.out.println("  Index options: " + fi.getIndexOptions());
        System.out.println("  DocValues type: " + fi.getDocValuesType());
        System.out.println();
        System.out.println("Associated file types:");
        if (fi.getIndexOptions() != IndexOptions.NONE) {
            System.out.println("  .tim - terms dictionary");
            System.out.println("  .tip - terms index");
            System.out.println("  .doc - postings (frequencies)");
            if (fi.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) {
                System.out.println("  .pos - positions");
            }
            if (fi.hasPayloads()) {
                System.out.println("  .pay - payloads");
            }
        }
        if (fi.getDocValuesType() != DocValuesType.NONE) {
            System.out.println("  .dvd - docvalues data");
            System.out.println("  .dvm - docvalues metadata");
        }
        if (fi.hasNorms()) {
            System.out.println("  .nvd - norms data");
            System.out.println("  .nvm - norms metadata");
        }
        return;
    }
}

System.out.println("Loaded index-analysis skill.");
System.out.println("Functions: analyzeIndex(), generateMeta(), fieldFiles(field), closeAnalysis()");
System.out.println("\nFor detailed offsets, also load:");
System.out.println("  /open skills/compound-files.jsh");
System.out.println("  /open skills/term-offsets.jsh");
System.out.println("  /open skills/docvalue-offsets.jsh");
