// compound-files.jsh
// Parse Lucene compound files (.cfe/.cfs) to extract embedded file offsets
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/compound-files.jsh
//   jshell> compoundEntries("_0")
//   jshell> closeCompoundIndex()

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.codecs.CodecUtil;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cached directory
Directory _cmpDir = null;

// Codec error detection helper
String _detectCodecErrorCmp(Exception e) {
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

// Open directory
void _openCmpDir() throws Exception {
    if (_cmpDir != null) return;
    _cmpDir = FSDirectory.open(Path.of(indexPath));
}

// Entry class to hold file info
record CompoundEntry(String fileName, long offset, long length) {}

// Parse .cfe file to get compound file entries
List<CompoundEntry> parseCompoundEntries(String segment) throws Exception {
    _openCmpDir();

    String cfeFile = segment + ".cfe";
    String cfsFile = segment + ".cfs";

    // Check files exist
    boolean hasCfe = Arrays.asList(_cmpDir.listAll()).contains(cfeFile);
    boolean hasCfs = Arrays.asList(_cmpDir.listAll()).contains(cfsFile);

    if (!hasCfe || !hasCfs) {
        System.out.println("No compound files for segment: " + segment);
        System.out.println("Available files: " + Arrays.toString(_cmpDir.listAll()));
        return Collections.emptyList();
    }

    List<CompoundEntry> entries = new ArrayList<>();

    IndexInput input = _cmpDir.openInput(cfeFile, IOContext.DEFAULT);
    try {
        // Read the header - Lucene uses big-endian for ints
        // Handle potential byte order issues with MemorySegmentIndexInput
        int magic = input.readInt();
        if (magic != CodecUtil.CODEC_MAGIC) {
            int reversed = Integer.reverseBytes(magic);
            if (reversed == CodecUtil.CODEC_MAGIC) {
                magic = reversed;
            }
        }
        if (magic != CodecUtil.CODEC_MAGIC) {
            throw new RuntimeException("Invalid .cfe magic: expected " +
                Integer.toHexString(CodecUtil.CODEC_MAGIC) + ", got " + Integer.toHexString(magic));
        }

        String codecName = input.readString();
        int version = input.readInt();

        // Read 16-byte object ID
        byte[] objectId = new byte[16];
        input.readBytes(objectId, 0, 16);

        // Read suffix
        int suffixLength = input.readByte() & 0xFF;
        if (suffixLength > 0) {
            byte[] suffixBytes = new byte[suffixLength];
            input.readBytes(suffixBytes, 0, suffixLength);
        }

        // Now read the number of entries (VInt encoded)
        int numEntries = input.readVInt();

        // Read each entry
        // Format: filename (string), dataOffset (long), dataLength (long)
        for (int i = 0; i < numEntries; i++) {
            String fileName = input.readString();
            long offset = input.readLong();
            long length = input.readLong();

            entries.add(new CompoundEntry(fileName, offset, length));
        }

        // Skip footer validation (requires ChecksumIndexInput)
        // Footer is: algorithm(1) + checksum(8) + magic(4) = 13 bytes at end

    } finally {
        input.close();
    }

    return entries;
}

// Main function: list compound file entries with offsets
void compoundEntries(String segment) throws Exception {
    List<CompoundEntry> entries = parseCompoundEntries(segment);

    if (entries.isEmpty()) {
        return;
    }

    String cfsFile = segment + ".cfs";
    long cfsSize = _cmpDir.fileLength(cfsFile);

    System.out.println("Compound file: " + cfsFile + " (" + cfsSize + " bytes)");
    System.out.println("=" .repeat(70));
    System.out.printf("%-35s %15s %15s%n", "File", "Offset", "Length");
    System.out.println("-".repeat(70));

    // Sort by offset for readability
    entries.sort(Comparator.comparingLong(CompoundEntry::offset));

    for (CompoundEntry entry : entries) {
        System.out.printf("%-35s %15d %15d%n",
            entry.fileName(), entry.offset(), entry.length());
    }
    System.out.println("-".repeat(70));
    System.out.println("Total: " + entries.size() + " files");
}

// Get entries as JSON
void compoundEntriesJson(String segment) throws Exception {
    List<CompoundEntry> entries = parseCompoundEntries(segment);

    if (entries.isEmpty()) {
        return;
    }

    String cfsFile = segment + ".cfs";
    long cfsSize = _cmpDir.fileLength(cfsFile);

    System.out.println("{");
    System.out.println("  \"segment\": \"" + segment + "\",");
    System.out.println("  \"compoundFile\": \"" + cfsFile + "\",");
    System.out.println("  \"compoundFileSize\": " + cfsSize + ",");
    System.out.println("  \"entries\": [");

    entries.sort(Comparator.comparingLong(CompoundEntry::offset));

    for (int i = 0; i < entries.size(); i++) {
        CompoundEntry entry = entries.get(i);
        String comma = (i < entries.size() - 1) ? "," : "";
        System.out.printf("    {\"file\": \"%s\", \"offset\": %d, \"length\": %d}%s%n",
            entry.fileName(), entry.offset(), entry.length(), comma);
    }

    System.out.println("  ]");
    System.out.println("}");
}

// List all segments in the index
void listSegments() throws Exception {
    _openCmpDir();

    String[] files = _cmpDir.listAll();
    Set<String> segments = new TreeSet<>();

    for (String file : files) {
        if (file.endsWith(".cfe") || file.endsWith(".cfs") || file.endsWith(".si")) {
            // Extract segment name (everything before the extension)
            int dotPos = file.lastIndexOf('.');
            if (dotPos > 0) {
                segments.add(file.substring(0, dotPos));
            }
        }
    }

    System.out.println("Segments in index: " + indexPath);
    for (String seg : segments) {
        boolean hasCompound = Arrays.asList(files).contains(seg + ".cfe");
        System.out.println("  " + seg + (hasCompound ? " (compound)" : " (non-compound)"));
    }
}

// Close resources
void closeCompoundIndex() throws Exception {
    if (_cmpDir != null) {
        _cmpDir.close();
        _cmpDir = null;
    }
    System.out.println("Index closed.");
}

System.out.println("Loaded compound-files skill.");
System.out.println("Functions: listSegments(), compoundEntries(segment), compoundEntriesJson(segment), closeCompoundIndex()");
