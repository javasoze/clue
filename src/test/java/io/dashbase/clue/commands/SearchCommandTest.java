package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import org.apache.lucene.search.SortField;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCommandTest {

  @Test
  void parseSortFieldParsesTypedFieldAndOrder() {
    SearchCommand command = new SearchCommand((LuceneContext) null);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
      SortField field = command.parseSortField("price:double:desc", out);
      assertNotNull(field);
      assertEquals("price", field.getField());
      assertEquals(SortField.Type.DOUBLE, field.getType());
      assertTrue(field.getReverse());
      assertEquals("", buffer.toString(StandardCharsets.UTF_8));
    }
  }

  @Test
  void parseSortFieldRejectsUnknownType() {
    SearchCommand command = new SearchCommand((LuceneContext) null);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
      SortField field = command.parseSortField("price:bogus", out);
      assertNull(field);
      assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("invalid sort type: bogus"));
    }
  }
}
