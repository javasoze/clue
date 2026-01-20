// codec-info.jsh
// Extracts codec name and class name from a Lucene index
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/codec-info.jsh
//   jshell> codecInfo()
//   jshell> closeCodecIndex()

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.codecs.Codec;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cached directory
Directory _codecDir = null;

// Codec error detection helper
String _detectCodecErrorCodec(Exception e) {
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

// Open directory with codec error handling
void _openCodecDir() throws Exception {
    if (_codecDir != null) return;
    try {
        _codecDir = FSDirectory.open(Path.of(indexPath));
    } catch (Exception e) {
        String codecName = _detectCodecErrorCodec(e);
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

// Show codec info for all segments
void codecInfo() throws Exception {
    _openCodecDir();

    SegmentInfos sis;
    try {
        sis = SegmentInfos.readLatestCommit(_codecDir);
    } catch (Exception e) {
        String codecName = _detectCodecErrorCodec(e);
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

    System.out.println("Reading index: " + indexPath);
    System.out.println("Segments: " + sis.size());
    System.out.println();

    // Track codec usage for summary
    Map<String, List<String>> codecToSegments = new LinkedHashMap<>();
    Map<String, String> codecToClass = new LinkedHashMap<>();

    for (SegmentCommitInfo commitInfo : sis) {
        if (commitInfo == null) continue;

        String segmentName = commitInfo.info.name;
        Codec codec = commitInfo.info.getCodec();
        String codecName = codec.getName();
        String codecClass = codec.getClass().getName();

        System.out.println("Segment: " + segmentName);
        System.out.println("  Codec name:  " + codecName);
        System.out.println("  Codec class: " + codecClass);
        System.out.println();

        // Track for summary
        codecToSegments.computeIfAbsent(codecName, k -> new ArrayList<>()).add(segmentName);
        codecToClass.put(codecName, codecClass);
    }

    // Print summary
    System.out.println("Summary:");
    System.out.println("  Total segments: " + sis.size());
    System.out.println("  Unique codecs: " + codecToSegments.size());

    for (var entry : codecToSegments.entrySet()) {
        String codecName = entry.getKey();
        List<String> segments = entry.getValue();
        String codecClass = codecToClass.get(codecName);
        System.out.println("  - " + codecName + " (" + codecClass + "): " + segments.size() + " segment(s)");
    }
}

// Close resources
void closeCodecIndex() throws Exception {
    if (_codecDir != null) {
        _codecDir.close();
        _codecDir = null;
    }
    System.out.println("Index closed.");
}

System.out.println("Loaded codec-info skill. Functions: codecInfo(), closeCodecIndex()");
