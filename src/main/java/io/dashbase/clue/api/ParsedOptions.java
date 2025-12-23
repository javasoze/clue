package io.dashbase.clue.api;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ParsedOptions {
    private final Map<String, Object> options;
    private ParsedOptions(Map<String, Object> options) {
        this.options = options == null ? Collections.emptyMap() : options;
    }

    public static ParsedOptions parse(Map<String, Object> options) {
        return new ParsedOptions(options);
    }

    public int getInt(String option, int defaultValue) {
        Object value = getOption(option);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        return defaultValue;
    }

    public String getString(String option, String defaultValue) {
        Object value = getOption(option);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }

    public boolean getBoolean(String option, boolean defaultValue) {
        Object value = getOption(option);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            String normalized = text.toLowerCase();
            if ("true".equals(normalized) || "1".equals(normalized)
                || "yes".equals(normalized) || "y".equals(normalized)
                || "on".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized)
                || "no".equals(normalized) || "n".equals(normalized)
                || "off".equals(normalized)) {
                return false;
            }
        }
        return defaultValue;
    }

    public String[] getStringArray(String option) {
        return getStringArray(option, new String[0]);
    }

    public String[] getStringArray(String option, String[] defaultValue) {
        Object value = getOption(option);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String[]) {
            return (String[]) value;
        }
        if (value instanceof Iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                if (item == null) {
                    continue;
                }
                values.add(String.valueOf(item));
            }
            return values.toArray(new String[0]);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            String[] values = new String[length];
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                values[i] = item == null ? null : String.valueOf(item);
            }
            return values;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            return new String[] {text};
        }
        return new String[] {String.valueOf(value)};
    }

    public double getDouble(String option, double defaultValue) {
        Object value = getOption(option);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1.0d : 0.0d;
        }
        return defaultValue;
    }

    public long getLong(String option, long defaultValue) {
        Object value = getOption(option);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? 1L : 0L;
        }
        return defaultValue;
    }

    private Object getOption(String option) {
        if (option == null || option.isBlank()) {
            return null;
        }
        return options.get(option);
    }


    public Path getPath(String option, Path defaultValue) {
        var obj = getOption(option);
        if (obj == null) {
            return defaultValue;
        } else {
            return Path.of(String.valueOf(obj));
        }
    }
}
