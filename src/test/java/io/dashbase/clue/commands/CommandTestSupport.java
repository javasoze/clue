package io.dashbase.clue.commands;

import io.dashbase.clue.ClueAppConfiguration;
import io.dashbase.clue.ClueApplication;
import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.test.BuildSampleIndex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class CommandTestSupport {
  private static final Pattern INT_PATTERN = Pattern.compile("(\\d+)");

  private CommandTestSupport() {
  }

  static Path buildSampleIndex(Path baseDir) throws Exception {
    Path indexDir = baseDir.resolve("index");
    Files.createDirectories(indexDir);
    Path carsJson = Path.of("src/main/resources/cars.json").toAbsolutePath();
    BuildSampleIndex.main(new String[]{carsJson.toString(), indexDir.toString()});
    return indexDir;
  }

  static LuceneContext newContext(Path indexDir) throws Exception {
    return new LuceneContext(indexDir.toString(), new ClueAppConfiguration(), false);
  }

  static String runCommand(LuceneContext ctx, String command, String... args) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
      ClueApplication.handleCommand(ctx, command, args, out);
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }

  static void shutdown(LuceneContext ctx) {
    if (ctx == null) {
      return;
    }
    try {
      ctx.shutdown();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static void copyDirectory(Path source, Path target) throws IOException {
    try (Stream<Path> paths = Files.walk(source)) {
      paths.forEach(path -> {
        Path relative = source.relativize(path);
        Path dest = target.resolve(relative);
        try {
          if (Files.isDirectory(path)) {
            Files.createDirectories(dest);
          } else {
            Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  static int extractIntAfter(String output, String label) {
    int idx = output.indexOf(label);
    if (idx < 0) {
      throw new IllegalArgumentException("Missing label: " + label);
    }
    String tail = output.substring(idx + label.length());
    Matcher matcher = INT_PATTERN.matcher(tail);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Missing integer after label: " + label);
    }
    return Integer.parseInt(matcher.group(1));
  }
}
