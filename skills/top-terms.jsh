// top-terms.jsh
// Output top N terms for a field ordered by docFreq descending
//
// Usage:
//   jshell> var indexPath = "myidx"
//   jshell> /open skills/codec-support.jsh  (optional, for custom codec support)
//   jshell> /open skills/top-terms.jsh
//   jshell> topTerms("fieldName", 10)

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.util.BytesRef;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Cached reader
DirectoryReader _reader = null;
Directory _dir = null;

// Codec error detection helper
String _detectCodecErrorTopTerms(Exception e) {
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

void _openIndex() throws Exception {
    if (_reader != null) return;
    try {
        _dir = FSDirectory.open(Path.of(indexPath));
        _reader = DirectoryReader.open(_dir);
        System.out.println("Opened index: " + indexPath + " (" + _reader.leaves().size() + " segments)");
    } catch (Exception e) {
        String codecName = _detectCodecErrorTopTerms(e);
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

void closeIndex() throws Exception {
    if (_reader != null) {
        _reader.close();
        _reader = null;
    }
    if (_dir != null) {
        _dir.close();
        _dir = null;
    }
    System.out.println("Index closed.");
}

void topTerms(String fieldName, int n) throws Exception {
    _openIndex();

    // Check if field is indexed
    boolean isIndexed = false;
    for (LeafReaderContext leaf : _reader.leaves()) {
        FieldInfo finfo = leaf.reader().getFieldInfos().fieldInfo(fieldName);
        if (finfo != null && finfo.getIndexOptions() != IndexOptions.NONE) {
            isIndexed = true;
            break;
        }
    }

    if (!isIndexed) {
        System.out.println("Error: field '" + fieldName + "' is not indexed (no term dictionary)");
        return;
    }

    // Collect terms and aggregate docFreq across segments
    var termFreqs = new HashMap<String, Long>();

    for (LeafReaderContext leaf : _reader.leaves()) {
        LeafReader reader = leaf.reader();
        Terms terms = reader.terms(fieldName);
        if (terms == null) continue;

        TermsEnum termsEnum = terms.iterator();
        BytesRef term;
        while ((term = termsEnum.next()) != null) {
            String termStr = term.utf8ToString();
            long docFreq = termsEnum.docFreq();
            termFreqs.merge(termStr, docFreq, Long::sum);
        }
    }

    // Sort by docFreq descending and take top N
    var sorted = new ArrayList<>(termFreqs.entrySet());
    sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

    System.out.println("Top " + n + " terms for field '" + fieldName + "' (by docFreq):");
    System.out.println(String.format("%-40s %s", "term", "docFreq"));
    System.out.println("-".repeat(50));

    int count = 0;
    for (var entry : sorted) {
        if (count >= n) break;
        System.out.println(String.format("%-40s %d", entry.getKey(), entry.getValue()));
        count++;
    }

    System.out.println("-".repeat(50));
    System.out.println("Total unique terms: " + termFreqs.size());
}

System.out.println("Loaded top-terms skill.");
System.out.println("Usage: topTerms(\"fieldName\", N)");
System.out.println("       closeIndex() - close when done");
