package io.dashbase.clue.util;

import java.util.ArrayList;
import java.util.List;

public final class CommandLineParser {
  private CommandLineParser() {
  }

  public static String[] splitArgs(String line) {
    if (line == null || line.isBlank()) {
      return new String[0];
    }
    List<String> args = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;

    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (escaping) {
        current.append(ch);
        escaping = false;
        continue;
      }
      if (ch == '\\' && !inSingle) {
        escaping = true;
        continue;
      }
      if (ch == '"' && !inSingle) {
        inDouble = !inDouble;
        continue;
      }
      if (ch == '\'' && !inDouble) {
        inSingle = !inSingle;
        continue;
      }
      if (Character.isWhitespace(ch) && !inSingle && !inDouble) {
        if (current.length() > 0) {
          args.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(ch);
    }

    if (escaping) {
      current.append('\\');
    }
    if (current.length() > 0) {
      args.add(current.toString());
    }

    return args.toArray(new String[0]);
  }

  public static String joinArgs(String[] args) {
    if (args == null || args.length == 0) {
      return "";
    }
    StringBuilder joined = new StringBuilder();
    for (String arg : args) {
      if (arg == null) {
        continue;
      }
      if (joined.length() > 0) {
        joined.append(' ');
      }
      if (arg.isEmpty() || needsQuoting(arg)) {
        joined.append('"');
        for (int i = 0; i < arg.length(); i++) {
          char ch = arg.charAt(i);
          if (ch == '"' || ch == '\\') {
            joined.append('\\');
          }
          joined.append(ch);
        }
        joined.append('"');
      } else {
        joined.append(arg);
      }
    }
    return joined.toString();
  }

  private static boolean needsQuoting(String value) {
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (Character.isWhitespace(ch) || ch == '"' || ch == '\\') {
        return true;
      }
    }
    return false;
  }
}
