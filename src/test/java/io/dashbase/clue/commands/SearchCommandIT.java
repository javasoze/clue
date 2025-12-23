package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchCommandIT {
  private static final Pattern DOC_PATTERN = Pattern.compile("doc: (\\d+),");

  private Path baseIndexDir;

  @BeforeAll
  void buildIndex(@TempDir Path tempDir) throws Exception {
    baseIndexDir = CommandTestSupport.buildSampleIndex(tempDir);
  }

  @Test
  void searchSupportsSortingByDocId() throws Exception {
    LuceneContext ctx = CommandTestSupport.newContext(baseIndexDir);
    try {
      String ascOutput = CommandTestSupport.runCommand(ctx, "search", "-q", "*", "-n", "5", "-s", "doc");
      List<Integer> ascDocs = extractDocIds(ascOutput);
      assertFalse(ascDocs.isEmpty());
      assertTrue(isAscending(ascDocs));

      String descOutput = CommandTestSupport.runCommand(ctx, "search", "-q", "*", "-n", "5", "-s", "doc:desc");
      List<Integer> descDocs = extractDocIds(descOutput);
      assertFalse(descDocs.isEmpty());
      assertTrue(isDescending(descDocs));
    } finally {
      CommandTestSupport.shutdown(ctx);
    }
  }

  private static List<Integer> extractDocIds(String output) {
    List<Integer> docIds = new ArrayList<>();
    Matcher matcher = DOC_PATTERN.matcher(output);
    while (matcher.find()) {
      docIds.add(Integer.parseInt(matcher.group(1)));
    }
    return docIds;
  }

  private static boolean isAscending(List<Integer> values) {
    for (int i = 1; i < values.size(); i++) {
      if (values.get(i) < values.get(i - 1)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isDescending(List<Integer> values) {
    for (int i = 1; i < values.size(); i++) {
      if (values.get(i) > values.get(i - 1)) {
        return false;
      }
    }
    return true;
  }
}
