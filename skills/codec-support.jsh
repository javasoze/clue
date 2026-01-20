// codec-support.jsh
// Shared codec handling utilities for all skills
//
// This file is automatically loaded by other skills.
// It provides functions to handle custom codec detection and configuration.

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Config file location
var _codecConfigFile = Path.of("codec-config.json");

// Loaded codec paths: codecName -> jarPath
var _codecPaths = new LinkedHashMap<String, String>();

// Load codec config from file
void _loadCodecConfig() {
    try {
        if (Files.exists(_codecConfigFile)) {
            String content = Files.readString(_codecConfigFile);
            // Parse codecs section
            var codecPattern = Pattern.compile("\"([^\"]+)\":\\s*\"([^\"]+)\"");
            var matcher = codecPattern.matcher(content);
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = matcher.group(2);
                if (!key.equals("codecs")) {
                    _codecPaths.put(key, value);
                }
            }
            if (!_codecPaths.isEmpty()) {
                System.out.println("Loaded codec config: " + _codecPaths.keySet());
            }
        }
    } catch (Exception e) {
        // Ignore errors loading config
    }
}

// Save codec config to file
void _saveCodecConfig() {
    try {
        var sb = new StringBuilder();
        sb.append("{\n  \"codecs\": {");
        var first = true;
        for (var entry : _codecPaths.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\n    \"").append(entry.getKey()).append("\": \"");
            sb.append(entry.getValue().replace("\\", "\\\\")).append("\"");
        }
        if (!_codecPaths.isEmpty()) sb.append("\n  ");
        sb.append("}\n}\n");
        Files.writeString(_codecConfigFile, sb.toString());
    } catch (Exception e) {
        System.err.println("Warning: could not save codec config: " + e.getMessage());
    }
}

// Register a codec path
void registerCodec(String codecName, String jarPath) {
    _codecPaths.put(codecName, jarPath);
    _saveCodecConfig();
    System.out.println("Registered codec '" + codecName + "' -> " + jarPath);
    _printClasspathInstructions();
}

// Get classpath string including all registered codecs
String _getFullClasspath() {
    var cp = new StringBuilder("build/libs/*");
    for (String jar : _codecPaths.values()) {
        cp.append(":").append(jar);
    }
    return cp.toString();
}

// Print instructions to update classpath
void _printClasspathInstructions() {
    System.out.println("\n" + "=".repeat(60));
    System.out.println("To add codec(s) to classpath, run:");
    System.out.println("/env -class-path " + _getFullClasspath());
    System.out.println("\nThen reload the skill.");
    System.out.println("=".repeat(60) + "\n");
}

// Check if exception is a codec not found error, return codec name or null
String _extractCodecName(Exception e) {
    String msg = e.getMessage();
    if (msg != null && msg.contains("Could not load codec")) {
        // Extract codec name from message like "Could not load codec 'XYZ'"
        var pattern = Pattern.compile("Could not load codec '([^']+)'");
        var matcher = pattern.matcher(msg);
        if (matcher.find()) {
            return matcher.group(1);
        }
    }
    // Also check for cause
    Throwable cause = e.getCause();
    while (cause != null) {
        String causeMsg = cause.getMessage();
        if (causeMsg != null && causeMsg.contains("Could not load codec")) {
            var pattern = Pattern.compile("Could not load codec '([^']+)'");
            var matcher = pattern.matcher(causeMsg);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        cause = cause.getCause();
    }
    return null;
}

// Handle codec error - returns true if it was a codec error
boolean handleCodecError(Exception e) {
    String codecName = _extractCodecName(e);
    if (codecName == null) {
        return false; // Not a codec error
    }

    System.out.println("\n" + "!".repeat(60));
    System.out.println("ERROR: Codec '" + codecName + "' not found!");
    System.out.println("!".repeat(60));

    // Check if we already have a path for this codec
    if (_codecPaths.containsKey(codecName)) {
        System.out.println("\nCodec is registered but not in classpath.");
        _printClasspathInstructions();
    } else {
        System.out.println("\nTo fix this, provide the path to the codec JAR:");
        System.out.println("  var codecPath = \"/path/to/codec.jar\"");
        System.out.println("  registerCodec(\"" + codecName + "\", codecPath)");
        System.out.println("\nOr if you know the path, run directly:");
        System.out.println("  registerCodec(\"" + codecName + "\", \"/path/to/codec.jar\")");
    }

    return true;
}

// Load config on script load
_loadCodecConfig();

// Print startup message if codecs are configured
if (!_codecPaths.isEmpty()) {
    System.out.println("\nConfigured codecs found. Suggested classpath:");
    System.out.println("/env -class-path " + _getFullClasspath());
    System.out.println();
}
