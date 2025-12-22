package io.dashbase.clue.util;

import java.nio.file.Path;
import java.util.Map;

public class OptionsParsingUtil {
    public static int parseIntOption(Map<String, String> options, String key, int defaultValue) {
        if (options == null) {
            return defaultValue;
        }
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String parseStringOption(Map<String, String> options, String key,
                                            String defaultValue) {
        if (options == null) {
            return defaultValue;
        }
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    public static Path parsePathOption(Map<String, String> options, String key, Path defaultValue) {
        String value = parseStringOption(options, key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Path.of(value.trim());
    }
}
