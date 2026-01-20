// doc-values.jsh
// Output all field values for a given docid (stored fields + docvalues)
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/codec-support.jsh  (optional, for custom codec support)
//   jshell> /open skills/doc-values.jsh
//   jshell> docValues(0)

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.util.BytesRef;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cached reader
DirectoryReader _docReader = null;
Directory _docDir = null;

// Codec error detection helper
String _detectCodecErrorDocValues(Exception e) {
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

void _openDocIndex() throws Exception {
    if (_docReader != null) return;
    try {
        _docDir = FSDirectory.open(Path.of(indexPath));
        _docReader = DirectoryReader.open(_docDir);
        System.out.println("Opened index: " + indexPath + " (maxDoc: " + _docReader.maxDoc() + ")");
    } catch (Exception e) {
        String codecName = _detectCodecErrorDocValues(e);
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

void closeDocIndex() throws Exception {
    if (_docReader != null) {
        _docReader.close();
        _docReader = null;
    }
    if (_docDir != null) {
        _docDir.close();
        _docDir = null;
    }
    System.out.println("Index closed.");
}

String bytesToString(BytesRef bytes) {
    if (bytes == null) return "null";
    try {
        // Try UTF-8 decoding
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer buf = ByteBuffer.wrap(bytes.bytes, bytes.offset, bytes.length);
        return decoder.decode(buf).toString();
    } catch (Exception e) {
        // Fall back to Base64
        byte[] data = new byte[bytes.length];
        System.arraycopy(bytes.bytes, bytes.offset, data, 0, bytes.length);
        return "[base64] " + Base64.getEncoder().encodeToString(data);
    }
}

void docValues(int docId) throws Exception {
    _openDocIndex();

    if (docId < 0 || docId >= _docReader.maxDoc()) {
        System.out.println("Error: docId " + docId + " out of range [0, " + _docReader.maxDoc() + ")");
        return;
    }

    System.out.println("Document: " + docId);
    System.out.println("=".repeat(60));

    // Find the leaf reader containing this doc
    List<LeafReaderContext> leaves = _docReader.leaves();
    LeafReaderContext targetLeaf = null;
    int localDocId = docId;

    for (LeafReaderContext leaf : leaves) {
        if (docId < leaf.docBase + leaf.reader().maxDoc()) {
            targetLeaf = leaf;
            localDocId = docId - leaf.docBase;
            break;
        }
    }

    if (targetLeaf == null) {
        System.out.println("Error: could not find leaf for docId " + docId);
        return;
    }

    LeafReader reader = targetLeaf.reader();

    // Check if doc is deleted
    if (reader.getLiveDocs() != null && !reader.getLiveDocs().get(localDocId)) {
        System.out.println("(Document is deleted)");
    }

    // Get stored fields
    Document storedDoc = reader.storedFields().document(localDocId);
    Set<String> printedFields = new HashSet<>();

    // Collect all field names
    Set<String> allFields = new TreeSet<>();
    for (FieldInfo fi : reader.getFieldInfos()) {
        allFields.add(fi.name);
    }

    for (String fieldName : allFields) {
        FieldInfo finfo = reader.getFieldInfos().fieldInfo(fieldName);
        List<String> values = new ArrayList<>();

        // 1. Stored fields
        String[] storedValues = storedDoc.getValues(fieldName);
        if (storedValues != null && storedValues.length > 0) {
            for (String v : storedValues) {
                values.add(v);
            }
        }

        // 2. DocValues
        DocValuesType dvType = finfo.getDocValuesType();
        if (dvType != DocValuesType.NONE) {
            switch (dvType) {
                case NUMERIC:
                    NumericDocValues ndv = reader.getNumericDocValues(fieldName);
                    if (ndv != null && ndv.advanceExact(localDocId)) {
                        values.add(String.valueOf(ndv.longValue()));
                    }
                    break;
                case BINARY:
                    BinaryDocValues bdv = reader.getBinaryDocValues(fieldName);
                    if (bdv != null && bdv.advanceExact(localDocId)) {
                        values.add(bytesToString(bdv.binaryValue()));
                    }
                    break;
                case SORTED:
                    SortedDocValues sdv = reader.getSortedDocValues(fieldName);
                    if (sdv != null && sdv.advanceExact(localDocId)) {
                        values.add(bytesToString(sdv.lookupOrd(sdv.ordValue())));
                    }
                    break;
                case SORTED_SET:
                    SortedSetDocValues ssdv = reader.getSortedSetDocValues(fieldName);
                    if (ssdv != null && ssdv.advanceExact(localDocId)) {
                        for (int i = 0; i < ssdv.docValueCount(); i++) {
                            long ord = ssdv.nextOrd();
                            values.add(bytesToString(ssdv.lookupOrd(ord)));
                        }
                    }
                    break;
                case SORTED_NUMERIC:
                    SortedNumericDocValues sndv = reader.getSortedNumericDocValues(fieldName);
                    if (sndv != null && sndv.advanceExact(localDocId)) {
                        for (int i = 0; i < sndv.docValueCount(); i++) {
                            values.add(String.valueOf(sndv.nextValue()));
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        // Skip if no values
        if (values.isEmpty()) continue;

        // Print field and values
        if (values.size() == 1) {
            System.out.println(fieldName + ": " + values.get(0));
        } else {
            System.out.println(fieldName + ":");
            for (String v : values) {
                System.out.println("  - " + v);
            }
        }
    }

    System.out.println("=".repeat(60));
}

System.out.println("Loaded doc-values skill.");
System.out.println("Usage: docValues(docId)");
System.out.println("       closeDocIndex() - close when done");
