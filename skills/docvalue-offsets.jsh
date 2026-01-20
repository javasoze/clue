// docvalue-offsets.jsh
// Extract docvalue file offsets from a Lucene index
//
// Usage:
//   jshell> var indexPath = "/path/to/index"
//   jshell> /open skills/docvalue-offsets.jsh
//   jshell> docValueOffset("color")
//   jshell> closeDocValueIndex()

import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import java.nio.file.Path;
import java.util.*;
import java.lang.reflect.Field;

// Cached reader and directory
DirectoryReader _dvReader = null;
Directory _dvDir = null;

// Open index
void _openDvIndex() throws Exception {
    if (_dvReader != null) return;
    _dvDir = FSDirectory.open(Path.of(indexPath));
    _dvReader = DirectoryReader.open(_dvDir);
    System.out.println("Opened index: " + indexPath + " (" + _dvReader.leaves().size() + " segments)");
}

// Close resources
void closeDocValueIndex() throws Exception {
    if (_dvReader != null) {
        _dvReader.close();
        _dvReader = null;
    }
    if (_dvDir != null) {
        _dvDir.close();
        _dvDir = null;
    }
    System.out.println("Index closed.");
}

// Helper to get field value via reflection
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

// List all fields on an object
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

// Helper to print NumericEntry fields
void printNumericEntry(Object entry, String indent) {
    Object docsWithFieldOffset = getFieldValue(entry, "docsWithFieldOffset");
    Object docsWithFieldLength = getFieldValue(entry, "docsWithFieldLength");
    Object numDocsWithField = getFieldValue(entry, "numDocsWithField");
    Object numValues = getFieldValue(entry, "numValues");
    Object valuesOffset = getFieldValue(entry, "valuesOffset");
    Object valuesLength = getFieldValue(entry, "valuesLength");

    if (docsWithFieldOffset != null) System.out.println(indent + "docsWithFieldOffset: " + docsWithFieldOffset);
    if (docsWithFieldLength != null) System.out.println(indent + "docsWithFieldLength: " + docsWithFieldLength);
    if (numDocsWithField != null) System.out.println(indent + "numDocsWithField: " + numDocsWithField);
    if (numValues != null) System.out.println(indent + "numValues: " + numValues);
    if (valuesOffset != null) System.out.println(indent + "valuesOffset: " + valuesOffset);
    if (valuesLength != null) System.out.println(indent + "valuesLength: " + valuesLength);
}

// Helper to print TermsDictEntry fields
void printTermsDictEntry(Object entry, String indent) {
    Object termsDictSize = getFieldValue(entry, "termsDictSize");
    Object termsDictBlockShift = getFieldValue(entry, "termsDictBlockShift");
    Object termsDataOffset = getFieldValue(entry, "termsDataOffset");
    Object termsDataLength = getFieldValue(entry, "termsDataLength");
    Object termsAddressesOffset = getFieldValue(entry, "termsAddressesOffset");
    Object termsAddressesLength = getFieldValue(entry, "termsAddressesLength");
    Object termsIndexOffset = getFieldValue(entry, "termsIndexOffset");
    Object termsIndexLength = getFieldValue(entry, "termsIndexLength");

    if (termsDictSize != null) System.out.println(indent + "termsDictSize: " + termsDictSize);
    if (termsDictBlockShift != null) System.out.println(indent + "termsDictBlockShift: " + termsDictBlockShift);
    if (termsDataOffset != null) System.out.println(indent + "termsDataOffset: " + termsDataOffset);
    if (termsDataLength != null) System.out.println(indent + "termsDataLength: " + termsDataLength);
    if (termsAddressesOffset != null) System.out.println(indent + "termsAddressesOffset: " + termsAddressesOffset);
    if (termsAddressesLength != null) System.out.println(indent + "termsAddressesLength: " + termsAddressesLength);
    if (termsIndexOffset != null) System.out.println(indent + "termsIndexOffset: " + termsIndexOffset);
    if (termsIndexLength != null) System.out.println(indent + "termsIndexLength: " + termsIndexLength);
}

// List all fields with docvalues
void listDocValueFields() throws Exception {
    _openDvIndex();

    Map<String, String> fieldTypes = new TreeMap<>();

    for (LeafReaderContext leaf : _dvReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfos fieldInfos = reader.getFieldInfos();

        for (FieldInfo fi : fieldInfos) {
            DocValuesType dvType = fi.getDocValuesType();
            if (dvType != DocValuesType.NONE) {
                fieldTypes.put(fi.name, dvType.name());
            }
        }
    }

    System.out.println("Fields with DocValues:");
    System.out.println("-".repeat(50));
    for (var entry : fieldTypes.entrySet()) {
        System.out.printf("  %-30s %s%n", entry.getKey(), entry.getValue());
    }
    System.out.println("-".repeat(50));
    System.out.println("Total: " + fieldTypes.size() + " fields");
}

// Get docvalue offset for a field
void docValueOffset(String fieldName) throws Exception {
    _openDvIndex();

    System.out.println("DocValue info for field: " + fieldName);
    System.out.println();

    for (LeafReaderContext leaf : _dvReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfos fieldInfos = reader.getFieldInfos();
        FieldInfo fi = fieldInfos.fieldInfo(fieldName);

        if (fi == null) {
            System.out.println("Segment " + leaf.ord + ": Field '" + fieldName + "' not found");
            continue;
        }

        DocValuesType dvType = fi.getDocValuesType();
        if (dvType == DocValuesType.NONE) {
            System.out.println("Segment " + leaf.ord + ": Field '" + fieldName + "' has no DocValues");
            continue;
        }

        System.out.println("Segment " + leaf.ord + ":");
        System.out.println("  Field: " + fieldName);
        System.out.println("  DocValuesType: " + dvType);

        // Get the appropriate DocValues reader
        Object dv = null;
        switch (dvType) {
            case NUMERIC:
                dv = reader.getNumericDocValues(fieldName);
                break;
            case BINARY:
                dv = reader.getBinaryDocValues(fieldName);
                break;
            case SORTED:
                dv = reader.getSortedDocValues(fieldName);
                break;
            case SORTED_SET:
                dv = reader.getSortedSetDocValues(fieldName);
                break;
            case SORTED_NUMERIC:
                dv = reader.getSortedNumericDocValues(fieldName);
                break;
        }

        if (dv == null) {
            System.out.println("  Could not get DocValues reader");
            continue;
        }

        System.out.println("  Reader class: " + dv.getClass().getName());

        // Try to extract internal state via reflection
        // Look for common field names that might contain offset info
        String[] offsetFields = {"dataIn", "data", "bytesReader", "in", "input",
                                 "dataOffset", "offset", "startOffset"};

        for (String fname : offsetFields) {
            Object val = getFieldValue(dv, fname);
            if (val != null) {
                System.out.println("  " + fname + ": " + val.getClass().getSimpleName());

                // If it's an IndexInput, try to get file pointer
                if (val.getClass().getName().contains("IndexInput")) {
                    try {
                        java.lang.reflect.Method m = val.getClass().getMethod("getFilePointer");
                        Object fp = m.invoke(val);
                        System.out.println("    filePointer: " + fp);
                    } catch (Exception e) {}

                    try {
                        java.lang.reflect.Method m = val.getClass().getMethod("length");
                        Object len = m.invoke(val);
                        System.out.println("    length: " + len);
                    } catch (Exception e) {}
                }
            }
        }

        // Check for entry field which might contain metadata
        Object entry = getFieldValue(dv, "entry");
        if (entry != null) {
            System.out.println("\n  Entry: " + entry.getClass().getSimpleName());

            // For SortedEntry: look at ordsEntry and termsDictEntry
            Object ordsEntry = getFieldValue(entry, "ordsEntry");
            Object termsDictEntry = getFieldValue(entry, "termsDictEntry");

            if (ordsEntry != null) {
                System.out.println("\n  Ordinals entry:");
                printNumericEntry(ordsEntry, "    ");
            }

            if (termsDictEntry != null) {
                System.out.println("\n  Terms dictionary entry:");
                printTermsDictEntry(termsDictEntry, "    ");
            }

            // For NumericEntry directly (NUMERIC type)
            if (ordsEntry == null && termsDictEntry == null) {
                printNumericEntry(entry, "    ");
            }
        }

        System.out.println();
    }
}

// Get docvalue offset as JSON
void docValueOffsetJson(String fieldName) throws Exception {
    _openDvIndex();

    for (LeafReaderContext leaf : _dvReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfos fieldInfos = reader.getFieldInfos();
        FieldInfo fi = fieldInfos.fieldInfo(fieldName);

        if (fi == null || fi.getDocValuesType() == DocValuesType.NONE) {
            continue;
        }

        DocValuesType dvType = fi.getDocValuesType();

        // Get the DocValues reader
        Object dv = null;
        switch (dvType) {
            case NUMERIC:
                dv = reader.getNumericDocValues(fieldName);
                break;
            case BINARY:
                dv = reader.getBinaryDocValues(fieldName);
                break;
            case SORTED:
                dv = reader.getSortedDocValues(fieldName);
                break;
            case SORTED_SET:
                dv = reader.getSortedSetDocValues(fieldName);
                break;
            case SORTED_NUMERIC:
                dv = reader.getSortedNumericDocValues(fieldName);
                break;
        }

        if (dv == null) continue;

        System.out.println("{");
        System.out.println("  \"field\": \"" + fieldName + "\",");
        System.out.println("  \"segment\": " + leaf.ord + ",");
        System.out.println("  \"docValuesType\": \"" + dvType + "\",");

        // Extract offsets from entry metadata
        Object entry = getFieldValue(dv, "entry");
        if (entry != null) {
            System.out.println("  \"locations\": {");

            Object dataOffset = getFieldValue(entry, "dataOffset");
            Object dataLength = getFieldValue(entry, "dataLength");
            Object termsDataOffset = getFieldValue(entry, "termsDataOffset");
            Object termsDataLength = getFieldValue(entry, "termsDataLength");
            Object ordsOffset = getFieldValue(entry, "ordsOffset");
            Object ordsLength = getFieldValue(entry, "ordsLength");

            List<String> parts = new ArrayList<>();

            if (dataOffset != null && dataLength != null) {
                parts.add("    \"data\": {\"file\": \".dvd\", \"offset\": " + dataOffset + ", \"length\": " + dataLength + "}");
            } else if (dataOffset != null) {
                parts.add("    \"data\": {\"file\": \".dvd\", \"offset\": " + dataOffset + "}");
            }

            if (termsDataOffset != null && termsDataLength != null) {
                parts.add("    \"termsData\": {\"file\": \".dvd\", \"offset\": " + termsDataOffset + ", \"length\": " + termsDataLength + "}");
            }

            if (ordsOffset != null && ordsLength != null) {
                parts.add("    \"ordinals\": {\"file\": \".dvd\", \"offset\": " + ordsOffset + ", \"length\": " + ordsLength + "}");
            }

            System.out.println(String.join(",\n", parts));
            System.out.println("  }");
        }

        System.out.println("}");
        return;
    }

    System.out.println("{\"error\": \"Field not found or has no DocValues: " + fieldName + "\"}");
}

// Debug: explore DocValues reader structure
void exploreDocValues(String fieldName) throws Exception {
    _openDvIndex();

    for (LeafReaderContext leaf : _dvReader.leaves()) {
        LeafReader reader = leaf.reader();
        FieldInfo fi = reader.getFieldInfos().fieldInfo(fieldName);
        if (fi == null || fi.getDocValuesType() == DocValuesType.NONE) continue;

        Object dv = null;
        switch (fi.getDocValuesType()) {
            case NUMERIC: dv = reader.getNumericDocValues(fieldName); break;
            case BINARY: dv = reader.getBinaryDocValues(fieldName); break;
            case SORTED: dv = reader.getSortedDocValues(fieldName); break;
            case SORTED_SET: dv = reader.getSortedSetDocValues(fieldName); break;
            case SORTED_NUMERIC: dv = reader.getSortedNumericDocValues(fieldName); break;
        }

        if (dv != null) {
            System.out.println("DocValues structure for: " + fieldName);
            listFields(dv);

            Object entry = getFieldValue(dv, "entry");
            if (entry != null) {
                System.out.println("\n\nEntry structure:");
                listFields(entry);
            }
        }
        return;
    }
}

System.out.println("Loaded docvalue-offsets skill.");
System.out.println("Functions: listDocValueFields(), docValueOffset(field), docValueOffsetJson(field), exploreDocValues(field), closeDocValueIndex()");
