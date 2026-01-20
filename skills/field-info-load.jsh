// field-info-load.jsh
// Loads field info from a Lucene index and saves to a cache file
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/codec-support.jsh  (optional, for custom codec support)
//   jshell> /open skills/field-info-load.jsh

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexOptions;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cache file location
var cacheFile = Path.of("field-info-cache.json");

// Field info storage: fieldName -> properties map
var fieldsMap = new LinkedHashMap<String, Map<String, Object>>();

// Codec error detection helper
String _detectCodecError(Exception e) {
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

// Open the index with codec error handling
Directory dir = null;
DirectoryReader reader = null;
int segmentCount = 0;

try {
    dir = FSDirectory.open(Path.of(indexPath));
    reader = DirectoryReader.open(dir);
    segmentCount = reader.leaves().size();
    System.out.println("Reading index: " + indexPath);
    System.out.println("Segments: " + segmentCount);
} catch (Exception e) {
    String codecName = _detectCodecError(e);
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

List<LeafReaderContext> leaves = reader.leaves();

// Iterate through all segments
for (LeafReaderContext leaf : leaves) {
    LeafReader leafReader = leaf.reader();
    FieldInfos fieldInfos = leafReader.getFieldInfos();

    for (FieldInfo fi : fieldInfos) {
        var props = fieldsMap.computeIfAbsent(fi.name, k -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("docValuesType", "NONE");
            m.put("indexOptions", "NONE");
            m.put("hasNorms", false);
            m.put("hasPayloads", false);
            m.put("hasTermVectors", false);
            m.put("hasVectorValues", false);
            m.put("segmentCount", 0);
            m.put("attributes", new LinkedHashMap<String, String>());
            return m;
        });

        // Update with this segment's info (use most specific non-NONE value)
        DocValuesType dvType = fi.getDocValuesType();
        if (dvType != DocValuesType.NONE) {
            props.put("docValuesType", dvType.name());
        }

        IndexOptions idxOpts = fi.getIndexOptions();
        if (idxOpts != IndexOptions.NONE) {
            props.put("indexOptions", idxOpts.name());
        }

        if (fi.hasNorms()) props.put("hasNorms", true);
        if (fi.hasPayloads()) props.put("hasPayloads", true);
        if (fi.hasTermVectors()) props.put("hasTermVectors", true);
        if (fi.hasVectorValues()) props.put("hasVectorValues", true);

        // Merge attributes
        @SuppressWarnings("unchecked")
        var attrs = (Map<String, String>) props.get("attributes");
        attrs.putAll(fi.attributes());

        // Increment segment count
        props.put("segmentCount", (Integer) props.get("segmentCount") + 1);
    }
}

reader.close();
dir.close();

// Simple JSON serialization
String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
}

String toJson(Object obj) {
    if (obj == null) return "null";
    if (obj instanceof Boolean || obj instanceof Number) return obj.toString();
    if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
    if (obj instanceof Map) {
        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) obj;
        var sb = new StringBuilder("{");
        var first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\n  \"").append(escapeJson(entry.getKey())).append("\": ");
            sb.append(toJson(entry.getValue()).replace("\n", "\n  "));
        }
        if (!map.isEmpty()) sb.append("\n");
        sb.append("}");
        return sb.toString();
    }
    return "\"" + escapeJson(obj.toString()) + "\"";
}

// Build cache object
var cache = new LinkedHashMap<String, Object>();
cache.put("indexPath", indexPath);
cache.put("segmentCount", segmentCount);
cache.put("fieldCount", fieldsMap.size());
cache.put("fields", fieldsMap);

// Write to file
String json = toJson(cache);
Files.writeString(cacheFile, json);

System.out.println("Loaded " + fieldsMap.size() + " fields from " + segmentCount + " segments");
System.out.println("Cache saved to: " + cacheFile.toAbsolutePath());
System.out.println("\nNext: /open skills/field-info-query.jsh");
