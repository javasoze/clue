// term-offsets.jsh
// Extract term and posting offsets from a Lucene index using reflection
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/term-offsets.jsh
//   jshell> termOffset("color", "red")
//   jshell> closeTermIndex()

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.util.BytesRef;
import java.nio.file.Path;
import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Cached reader and directory
DirectoryReader _termReader = null;
Directory _termDir = null;

// Open index
void _openTermIndex() throws Exception {
    if (_termReader != null) return;
    _termDir = FSDirectory.open(Path.of(indexPath));
    _termReader = DirectoryReader.open(_termDir);
    System.out.println("Opened index: " + indexPath + " (" + _termReader.leaves().size() + " segments)");
}

// Close resources
void closeTermIndex() throws Exception {
    if (_termReader != null) {
        _termReader.close();
        _termReader = null;
    }
    if (_termDir != null) {
        _termDir.close();
        _termDir = null;
    }
    System.out.println("Index closed.");
}

// Helper to get field value via reflection, searching up the class hierarchy
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

// List all fields on an object (for debugging)
void listFields(Object obj) {
    Class<?> clazz = obj.getClass();
    System.out.println("Class hierarchy for: " + clazz.getName());
    while (clazz != null) {
        System.out.println("\n  " + clazz.getName() + ":");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println("    " + f.getType().getSimpleName() + " " + f.getName());
        }
        clazz = clazz.getSuperclass();
    }
}

// Get term offset information
void termOffset(String fieldName, String termValue) throws Exception {
    _openTermIndex();

    System.out.println("Looking up term: " + fieldName + ":" + termValue);
    System.out.println();

    for (LeafReaderContext leaf : _termReader.leaves()) {
        LeafReader reader = leaf.reader();
        Terms terms = reader.terms(fieldName);

        if (terms == null) {
            System.out.println("Segment " + leaf.ord + ": Field '" + fieldName + "' not found");
            continue;
        }

        TermsEnum te = terms.iterator();
        BytesRef termBytes = new BytesRef(termValue);

        if (!te.seekExact(termBytes)) {
            System.out.println("Segment " + leaf.ord + ": Term '" + termValue + "' not found in field '" + fieldName + "'");
            continue;
        }

        System.out.println("Segment " + leaf.ord + ":");
        System.out.println("  Term: " + fieldName + ":" + termValue);
        System.out.println("  docFreq: " + te.docFreq());
        System.out.println("  totalTermFreq: " + te.totalTermFreq());

        // Try to extract internal state via reflection
        // The TermsEnum is likely a SegmentTermsEnum from BlockTreeTermsReader
        System.out.println("\n  TermsEnum class: " + te.getClass().getName());

        // Try to find the current frame or state object
        Object currentFrame = getFieldValue(te, "currentFrame");
        if (currentFrame != null) {
            System.out.println("  Found currentFrame: " + currentFrame.getClass().getName());

            // Try to get file pointer (fp) from the frame
            Object fp = getFieldValue(currentFrame, "fp");
            if (fp != null) {
                System.out.println("  Frame fp (file pointer): " + fp);
            }

            Object fpOrig = getFieldValue(currentFrame, "fpOrig");
            if (fpOrig != null) {
                System.out.println("  Frame fpOrig: " + fpOrig);
            }

            Object fpEnd = getFieldValue(currentFrame, "fpEnd");
            if (fpEnd != null) {
                System.out.println("  Frame fpEnd: " + fpEnd);
            }

            // Get term state if available
            Object state = getFieldValue(currentFrame, "state");
            if (state != null) {
                System.out.println("\n  BlockTermState: " + state.getClass().getName());

                // Common BlockTermState fields
                Object docStartFP = getFieldValue(state, "docStartFP");
                Object posStartFP = getFieldValue(state, "posStartFP");
                Object payStartFP = getFieldValue(state, "payStartFP");
                Object singletonDocID = getFieldValue(state, "singletonDocID");

                if (docStartFP != null) System.out.println("    docStartFP: " + docStartFP);
                if (posStartFP != null) System.out.println("    posStartFP: " + posStartFP);
                if (payStartFP != null) System.out.println("    payStartFP: " + payStartFP);
                if (singletonDocID != null) System.out.println("    singletonDocID: " + singletonDocID);
            }
        }

        // Try alternative: look for termState directly
        Object termState = getFieldValue(te, "termState");
        if (termState == null) {
            termState = getFieldValue(te, "state");
        }
        if (termState != null && currentFrame == null) {
            System.out.println("\n  TermState: " + termState.getClass().getName());
            Object docStartFP = getFieldValue(termState, "docStartFP");
            Object posStartFP = getFieldValue(termState, "posStartFP");
            if (docStartFP != null) System.out.println("    docStartFP: " + docStartFP);
            if (posStartFP != null) System.out.println("    posStartFP: " + posStartFP);
        }

        // List all fields for debugging if we couldn't find expected fields
        if (currentFrame == null && termState == null) {
            System.out.println("\n  Could not find internal state. Available fields:");
            listFields(te);
        }

        System.out.println();
    }
}

// Debug function to explore TermsEnum structure
void exploreTermsEnum(String fieldName, String termValue) throws Exception {
    _openTermIndex();

    for (LeafReaderContext leaf : _termReader.leaves()) {
        LeafReader reader = leaf.reader();
        Terms terms = reader.terms(fieldName);
        if (terms == null) continue;

        TermsEnum te = terms.iterator();
        if (!te.seekExact(new BytesRef(termValue))) continue;

        System.out.println("TermsEnum structure for: " + fieldName + ":" + termValue);
        listFields(te);
        return;
    }
    System.out.println("Term not found: " + fieldName + ":" + termValue);
}

// Get posting locations for a term
void postingOffset(String fieldName, String termValue) throws Exception {
    _openTermIndex();

    System.out.println("Posting info for term: " + fieldName + ":" + termValue);
    System.out.println();

    for (LeafReaderContext leaf : _termReader.leaves()) {
        LeafReader reader = leaf.reader();
        Terms terms = reader.terms(fieldName);
        if (terms == null) continue;

        TermsEnum te = terms.iterator();
        if (!te.seekExact(new BytesRef(termValue))) continue;

        PostingsEnum postings = te.postings(null, PostingsEnum.ALL);

        System.out.println("Segment " + leaf.ord + ":");
        System.out.println("  docFreq: " + te.docFreq());

        // Try to get internal state from PostingsEnum
        System.out.println("  PostingsEnum class: " + postings.getClass().getName());

        // Look for file pointer fields
        Object docIn = getFieldValue(postings, "docIn");
        if (docIn != null) {
            System.out.println("  Has docIn (doc file input)");
            Object filePointer = null;
            try {
                Method m = docIn.getClass().getMethod("getFilePointer");
                filePointer = m.invoke(docIn);
            } catch (Exception e) {}
            if (filePointer != null) {
                System.out.println("    docIn filePointer: " + filePointer);
            }
        }

        Object posIn = getFieldValue(postings, "posIn");
        if (posIn != null) {
            System.out.println("  Has posIn (positions file input)");
        }

        Object payIn = getFieldValue(postings, "payIn");
        if (payIn != null) {
            System.out.println("  Has payIn (payloads file input)");
        }

        // Get starting file pointers
        Object docTermStartFP = getFieldValue(postings, "docTermStartFP");
        Object posTermStartFP = getFieldValue(postings, "posTermStartFP");
        Object payTermStartFP = getFieldValue(postings, "payTermStartFP");

        if (docTermStartFP != null) System.out.println("  docTermStartFP: " + docTermStartFP);
        if (posTermStartFP != null) System.out.println("  posTermStartFP: " + posTermStartFP);
        if (payTermStartFP != null) System.out.println("  payTermStartFP: " + payTermStartFP);

        System.out.println();
    }
}

// Get term offset as JSON
void termOffsetJson(String fieldName, String termValue) throws Exception {
    _openTermIndex();

    for (LeafReaderContext leaf : _termReader.leaves()) {
        LeafReader reader = leaf.reader();
        Terms terms = reader.terms(fieldName);
        if (terms == null) continue;

        TermsEnum te = terms.iterator();
        if (!te.seekExact(new BytesRef(termValue))) continue;

        System.out.println("{");
        System.out.println("  \"term\": \"" + fieldName + ":" + termValue + "\",");
        System.out.println("  \"segment\": " + leaf.ord + ",");
        System.out.println("  \"docFreq\": " + te.docFreq() + ",");
        System.out.println("  \"totalTermFreq\": " + te.totalTermFreq() + ",");

        // Extract offsets via reflection
        Object currentFrame = getFieldValue(te, "currentFrame");
        if (currentFrame != null) {
            Object fp = getFieldValue(currentFrame, "fp");
            Object fpOrig = getFieldValue(currentFrame, "fpOrig");
            Object fpEnd = getFieldValue(currentFrame, "fpEnd");

            System.out.println("  \"locations\": {");

            // Term location in .tim file
            if (fp != null) {
                long fpLong = ((Number)fp).longValue();
                long fpEndLong = fpEnd != null ? ((Number)fpEnd).longValue() : fpLong;
                System.out.println("    \"term\": {\"file\": \".tim\", \"offset\": " + fpLong +
                    ", \"length\": " + (fpEndLong - fpLong) + "},");
            }

            // Posting locations from BlockTermState
            Object state = getFieldValue(currentFrame, "state");
            if (state != null) {
                Object docStartFP = getFieldValue(state, "docStartFP");
                Object posStartFP = getFieldValue(state, "posStartFP");
                Object payStartFP = getFieldValue(state, "payStartFP");

                if (docStartFP != null) {
                    System.out.println("    \"postings\": {\"file\": \".doc\", \"offset\": " + docStartFP + "},");
                }
                if (posStartFP != null && ((Number)posStartFP).longValue() > 0) {
                    System.out.println("    \"positions\": {\"file\": \".pos\", \"offset\": " + posStartFP + "},");
                }
                if (payStartFP != null && ((Number)payStartFP).longValue() > 0) {
                    System.out.println("    \"payloads\": {\"file\": \".pay\", \"offset\": " + payStartFP + "}");
                }
            }
            // Remove trailing comma if needed by checking last char
            System.out.println("  }");
        }

        System.out.println("}");
        return;
    }
    System.out.println("{\"error\": \"Term not found: " + fieldName + ":" + termValue + "\"}");
}

// Data class for term offset info
record TermOffsetInfo(String term, int docFreq, long totalTermFreq, long docStartFP, long posStartFP, long payStartFP) {}

// Get all terms with offsets for a field, with calculated lengths
void fieldTermOffsets(String fieldName) throws Exception {
    _openTermIndex();

    System.out.println("Term offsets for field: " + fieldName);
    System.out.println();

    for (LeafReaderContext leaf : _termReader.leaves()) {
        LeafReader reader = leaf.reader();
        Terms terms = reader.terms(fieldName);
        if (terms == null) {
            System.out.println("Field '" + fieldName + "' not found");
            continue;
        }

        // Collect all term offsets
        List<TermOffsetInfo> termInfos = new ArrayList<>();
        TermsEnum te = terms.iterator();
        BytesRef term;

        while ((term = te.next()) != null) {
            String termText = term.utf8ToString();
            int docFreq = te.docFreq();
            long totalTermFreq = te.totalTermFreq();

            // Extract offsets via reflection
            long docStartFP = -1, posStartFP = -1, payStartFP = -1;

            Object currentFrame = getFieldValue(te, "currentFrame");
            if (currentFrame != null) {
                Object state = getFieldValue(currentFrame, "state");
                if (state != null) {
                    Object dsfp = getFieldValue(state, "docStartFP");
                    Object psfp = getFieldValue(state, "posStartFP");
                    Object payfp = getFieldValue(state, "payStartFP");

                    if (dsfp != null) docStartFP = ((Number)dsfp).longValue();
                    if (psfp != null) posStartFP = ((Number)psfp).longValue();
                    if (payfp != null) payStartFP = ((Number)payfp).longValue();
                }
            }

            termInfos.add(new TermOffsetInfo(termText, docFreq, totalTermFreq, docStartFP, posStartFP, payStartFP));
        }

        // Sort by docStartFP to calculate lengths
        List<TermOffsetInfo> sortedByDoc = new ArrayList<>(termInfos);
        sortedByDoc.sort((a, b) -> Long.compare(a.docStartFP(), b.docStartFP()));

        // Calculate lengths
        System.out.println("Segment " + leaf.ord + ": " + termInfos.size() + " terms");
        System.out.println();
        System.out.printf("%-20s %8s %12s %12s %12s%n", "Term", "docFreq", "docStartFP", "docLen", "posStartFP");
        System.out.println("-".repeat(70));

        for (int i = 0; i < sortedByDoc.size(); i++) {
            TermOffsetInfo info = sortedByDoc.get(i);
            long docLen = -1;

            // Find next term with different docStartFP
            for (int j = i + 1; j < sortedByDoc.size(); j++) {
                if (sortedByDoc.get(j).docStartFP() > info.docStartFP()) {
                    docLen = sortedByDoc.get(j).docStartFP() - info.docStartFP();
                    break;
                }
            }

            System.out.printf("%-20s %8d %12d %12s %12d%n",
                info.term().length() > 20 ? info.term().substring(0, 17) + "..." : info.term(),
                info.docFreq(),
                info.docStartFP(),
                docLen > 0 ? String.valueOf(docLen) : "?",
                info.posStartFP());
        }
        System.out.println();
    }
}

// Get term offsets with lengths as JSON for specific terms
void termOffsetsWithLength(String fieldName, String... termValues) throws Exception {
    _openTermIndex();

    for (LeafReaderContext leaf : _termReader.leaves()) {
        LeafReader reader = leaf.reader();
        Terms terms = reader.terms(fieldName);
        if (terms == null) continue;

        // First pass: collect all term offsets to calculate lengths
        Map<Long, Long> docFPtoNextFP = new TreeMap<>();
        List<Long> allDocFPs = new ArrayList<>();

        TermsEnum te = terms.iterator();
        BytesRef term;
        while ((term = te.next()) != null) {
            Object currentFrame = getFieldValue(te, "currentFrame");
            if (currentFrame != null) {
                Object state = getFieldValue(currentFrame, "state");
                if (state != null) {
                    Object dsfp = getFieldValue(state, "docStartFP");
                    if (dsfp != null) {
                        allDocFPs.add(((Number)dsfp).longValue());
                    }
                }
            }
        }

        // Sort and build next-FP map
        Collections.sort(allDocFPs);
        for (int i = 0; i < allDocFPs.size() - 1; i++) {
            if (!allDocFPs.get(i).equals(allDocFPs.get(i + 1))) {
                docFPtoNextFP.put(allDocFPs.get(i), allDocFPs.get(i + 1));
            }
        }

        // Second pass: get info for requested terms
        System.out.println("[");
        boolean first = true;
        for (String termValue : termValues) {
            te = terms.iterator();
            if (!te.seekExact(new BytesRef(termValue))) continue;

            if (!first) System.out.println(",");
            first = false;

            System.out.println("  {");
            System.out.println("    \"term\": \"" + fieldName + ":" + termValue + "\",");
            System.out.println("    \"docFreq\": " + te.docFreq() + ",");

            Object currentFrame = getFieldValue(te, "currentFrame");
            if (currentFrame != null) {
                Object state = getFieldValue(currentFrame, "state");
                if (state != null) {
                    Object dsfp = getFieldValue(state, "docStartFP");
                    Object psfp = getFieldValue(state, "posStartFP");

                    if (dsfp != null) {
                        long docStartFP = ((Number)dsfp).longValue();
                        Long nextFP = docFPtoNextFP.get(docStartFP);
                        long docLen = nextFP != null ? nextFP - docStartFP : -1;

                        System.out.println("    \"postings\": {");
                        System.out.println("      \"file\": \".doc\",");
                        System.out.println("      \"offset\": " + docStartFP + ",");
                        System.out.println("      \"length\": " + (docLen > 0 ? docLen : "\"unknown\""));
                        System.out.println("    },");
                    }
                    if (psfp != null && ((Number)psfp).longValue() > 0) {
                        System.out.println("    \"positions\": {\"file\": \".pos\", \"offset\": " + psfp + "}");
                    }
                }
            }
            System.out.print("  }");
        }
        System.out.println("\n]");
        return;
    }
}

System.out.println("Loaded term-offsets skill.");
System.out.println("Functions: termOffset(field, term), termOffsetJson(field, term), postingOffset(field, term),");
System.out.println("           fieldTermOffsets(field), termOffsetsWithLength(field, terms...), closeTermIndex()");
