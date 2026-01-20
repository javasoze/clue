// field-info-query.jsh
// Query functions for cached field info
//
// Usage:
//   jshell> /open skills/field-info-query.jsh
//   jshell> summary()
//   jshell> listFields()
//   jshell> listFields("user.*")
//   jshell> fieldInfo("fieldName")

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

// Cache file location
var _cacheFile = Path.of("field-info-cache.json");

// Parsed cache data
var _indexPath = "";
var _segmentCount = 0;
var _fieldCount = 0;
var _fields = new LinkedHashMap<String, Map<String, Object>>();

// Simple regex-based JSON parsing for our known format
{
    try {
        String content = Files.readString(_cacheFile);

        // Extract top-level values
        var pathMatch = Pattern.compile("\"indexPath\":\\s*\"([^\"]+)\"").matcher(content);
        if (pathMatch.find()) _indexPath = pathMatch.group(1);

        var segMatch = Pattern.compile("\"segmentCount\":\\s*(\\d+)").matcher(content);
        if (segMatch.find()) _segmentCount = Integer.parseInt(segMatch.group(1));

        var fieldCountMatch = Pattern.compile("\"fieldCount\":\\s*(\\d+)").matcher(content);
        if (fieldCountMatch.find()) _fieldCount = Integer.parseInt(fieldCountMatch.group(1));

        // Extract fields section and parse each field
        int fieldsStart = content.indexOf("\"fields\": {") + 11;
        int fieldsEnd = content.lastIndexOf("}") - 1; // before final closing brace
        String fieldsSection = content.substring(fieldsStart, fieldsEnd);

        // Parse each field block - match field name and content up to segmentCount (before attributes)
        // Since attributes have nested braces, we parse segmentCount from the section before attributes
        var fieldPattern = Pattern.compile("\"([^\"]+)\":\\s*\\{\\s*\"docValuesType\"");
        var fieldMatcher = fieldPattern.matcher(fieldsSection);

        // Get all field start positions
        var fieldStarts = new ArrayList<int[]>(); // [nameStart, nameEnd, contentStart]
        while (fieldMatcher.find()) {
            fieldStarts.add(new int[]{fieldMatcher.start(), fieldMatcher.end(), fieldMatcher.end()});
        }

        for (int idx = 0; idx < fieldStarts.size(); idx++) {
            int start = fieldStarts.get(idx)[0];
            int end = idx + 1 < fieldStarts.size() ? fieldStarts.get(idx + 1)[0] : fieldsSection.length();
            String fieldBlock = fieldsSection.substring(start, end);

            // Extract field name
            var namePattern = Pattern.compile("\"([^\"]+)\":\\s*\\{");
            var nameMatcher = namePattern.matcher(fieldBlock);
            if (!nameMatcher.find()) continue;
            String fieldName = nameMatcher.group(1);

            var props = new LinkedHashMap<String, Object>();

            // Extract string properties
            var strPropPattern = Pattern.compile("\"(docValuesType|indexOptions)\":\\s*\"([^\"]+)\"");
            var strMatcher = strPropPattern.matcher(fieldBlock);
            while (strMatcher.find()) {
                props.put(strMatcher.group(1), strMatcher.group(2));
            }

            // Extract boolean properties
            var boolPropPattern = Pattern.compile("\"(hasNorms|hasPayloads|hasTermVectors|hasVectorValues)\":\\s*(true|false)");
            var boolMatcher = boolPropPattern.matcher(fieldBlock);
            while (boolMatcher.find()) {
                props.put(boolMatcher.group(1), Boolean.parseBoolean(boolMatcher.group(2)));
            }

            // Extract segmentCount
            var segCntPattern = Pattern.compile("\"segmentCount\":\\s*(\\d+)");
            var segCntMatcher = segCntPattern.matcher(fieldBlock);
            if (segCntMatcher.find()) {
                props.put("segmentCount", Integer.parseInt(segCntMatcher.group(1)));
            }

            // Extract attributes (nested object)
            var attrsPattern = Pattern.compile("\"attributes\":\\s*\\{([^}]*)\\}");
            var attrsMatcher = attrsPattern.matcher(fieldBlock);
            if (attrsMatcher.find()) {
                var attrs = new LinkedHashMap<String, String>();
                var attrPattern = Pattern.compile("\"([^\"]+)\":\\s*\"([^\"]+)\"");
                var attrMatcher = attrPattern.matcher(attrsMatcher.group(1));
                while (attrMatcher.find()) {
                    attrs.put(attrMatcher.group(1), attrMatcher.group(2));
                }
                props.put("attributes", attrs);
            }

            _fields.put(fieldName, props);
        }

        System.out.println("Loaded cache: " + _fieldCount + " fields from " + _indexPath);
    } catch (Exception e) {
        System.out.println("No cache found. Run /open skills/field-info-load.jsh first.");
        System.out.println("Error: " + e.getMessage());
    }
}

// Query functions

void summary() {
    System.out.println("Index: " + _indexPath);
    System.out.println("Segments: " + _segmentCount);
    System.out.println("Total fields: " + _fieldCount);
    System.out.println();

    // Count by docValuesType
    var dvCounts = new TreeMap<String, Integer>();
    var idxCounts = new TreeMap<String, Integer>();
    int withNorms = 0, withPayloads = 0, withTermVectors = 0, withVectors = 0;

    for (var entry : _fields.entrySet()) {
        var props = entry.getValue();
        String dv = (String) props.get("docValuesType");
        dvCounts.merge(dv, 1, Integer::sum);

        String idx = (String) props.get("indexOptions");
        idxCounts.merge(idx, 1, Integer::sum);

        if (Boolean.TRUE.equals(props.get("hasNorms"))) withNorms++;
        if (Boolean.TRUE.equals(props.get("hasPayloads"))) withPayloads++;
        if (Boolean.TRUE.equals(props.get("hasTermVectors"))) withTermVectors++;
        if (Boolean.TRUE.equals(props.get("hasVectorValues"))) withVectors++;
    }

    System.out.println("By DocValues type:");
    for (var e : dvCounts.entrySet()) {
        System.out.println("  " + e.getKey() + ": " + e.getValue());
    }

    System.out.println("\nBy Index options:");
    for (var e : idxCounts.entrySet()) {
        System.out.println("  " + e.getKey() + ": " + e.getValue());
    }

    System.out.println("\nFeatures:");
    System.out.println("  with norms: " + withNorms);
    System.out.println("  with payloads: " + withPayloads);
    System.out.println("  with term vectors: " + withTermVectors);
    System.out.println("  with vector values: " + withVectors);
}

void listFieldsImpl(String pattern, int limit) {
    var regex = Pattern.compile(pattern);
    var matches = _fields.keySet().stream()
        .filter(name -> regex.matcher(name).matches())
        .sorted()
        .collect(Collectors.toList());

    System.out.println("Fields matching '" + pattern + "': " + matches.size());
    int shown = 0;
    for (var name : matches) {
        System.out.println("  " + name);
        if (++shown >= limit) {
            System.out.println("  ... (" + (matches.size() - limit) + " more, use listFields(pattern, limit) to see more)");
            break;
        }
    }
}

void listFields() {
    listFieldsImpl(".*", 100);
}

void listFields(String pattern) {
    listFieldsImpl(pattern, 100);
}

void listFields(String pattern, int limit) {
    listFieldsImpl(pattern, limit);
}

void fieldInfo(String fieldName) {
    var props = _fields.get(fieldName);
    if (props == null) {
        System.out.println("Field not found: " + fieldName);
        return;
    }
    System.out.println("Field: " + fieldName);
    System.out.println("  docValuesType:   " + props.get("docValuesType"));
    System.out.println("  indexOptions:    " + props.get("indexOptions"));
    System.out.println("  hasNorms:        " + props.get("hasNorms"));
    System.out.println("  hasPayloads:     " + props.get("hasPayloads"));
    System.out.println("  hasTermVectors:  " + props.get("hasTermVectors"));
    System.out.println("  hasVectorValues: " + props.get("hasVectorValues"));
    System.out.println("  segmentCount:    " + props.get("segmentCount"));
    @SuppressWarnings("unchecked")
    var attrs = (Map<String, String>) props.get("attributes");
    if (attrs != null && !attrs.isEmpty()) {
        System.out.println("  attributes:      " + attrs);
    }
}

System.out.println("\nAvailable functions:");
System.out.println("  summary()           - show index stats");
System.out.println("  listFields()        - list all field names");
System.out.println("  listFields(pattern) - list fields matching regex");
System.out.println("  fieldInfo(name)     - show details for a field");
