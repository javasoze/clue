package io.dashbase.clue.commands;

import io.dashbase.clue.LuceneContext;
import io.dashbase.clue.client.CmdlineHelper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Readonly
@Command(name = "search", mixinStandardHelpOptions = true)
public class SearchCommand extends ClueCommand {

  private final LuceneContext ctx;

  public SearchCommand(LuceneContext ctx) {
    super(ctx);
    this.ctx = ctx;
  }

  @Option(names = {"-q", "--query"}, arity = "0..*", description = "query")
  private String[] query;

  @Option(names = {"-n", "--num"}, defaultValue = "10")
  private int num;

  @Option(names = {"-s", "--sort"}, paramLabel = "sort",
      description = "sort by field[:type[:asc|desc]] or score/doc; repeat for multi-sort")
  private List<String> sort;

  @Override
  public String getName() {
    return "search";
  }

  @Override
  public String help() {
    return "executes a query against the index";
  }

  @Override
  protected void run(PrintStream out) throws Exception {
    IndexSearcher searcher = ctx.getIndexSearcher();
    List<String> qlist;
    if (query == null || query.length == 0) {
      qlist = Collections.singletonList("*");
    } else {
      qlist = Arrays.asList(query);
    }

    Query q;

    try{
      q = CmdlineHelper.toQuery(qlist, ctx.getQueryBuilder());
    }
    catch(Exception e){
      out.println("cannot parse query: " + e.getMessage());
      return;
    }
    
    out.println("parsed query: " + q);

    int count = num;
    Sort sortSpec = buildSort(sort, out);
    if (sortSpec == null && sort != null && !sort.isEmpty()) {
      return;
    }

    long start = System.currentTimeMillis();
    TopDocs td = sortSpec == null ? searcher.search(q, count) : searcher.search(q, count, sortSpec);
    long end = System.currentTimeMillis();
    
    out.println("numhits: " + td.totalHits);
    out.println("concurrent search: " + ctx.isCurrentSearch());
    out.println("time: " + (end-start) + "ms");
    ScoreDoc[] docs = td.scoreDocs;
    for (ScoreDoc doc : docs){
      out.println("doc: " + doc.doc + ", score: " + doc.score);
    }
  }

  private Sort buildSort(List<String> specs, PrintStream out) {
    if (specs == null || specs.isEmpty()) {
      return null;
    }
    List<SortField> fields = new ArrayList<>();
    for (String spec : specs) {
      SortField field = parseSortField(spec, out);
      if (field == null) {
        return null;
      }
      fields.add(field);
    }
    return new Sort(fields.toArray(new SortField[0]));
  }

  SortField parseSortField(String spec, PrintStream out) {
    if (spec == null || spec.trim().isEmpty()) {
      out.println("sort spec is empty");
      return null;
    }
    String trimmed = spec.trim();
    Boolean orderDesc = null;
    if (trimmed.startsWith("-")) {
      orderDesc = true;
      trimmed = trimmed.substring(1);
    }
    String[] parts = trimmed.split(":", -1);
    if (parts.length > 3) {
      out.println("invalid sort spec: " + spec);
      return null;
    }
    String field = parts[0];
    if (field.isEmpty()) {
      out.println("invalid sort spec: " + spec);
      return null;
    }
    String typePart = null;
    String orderPart = null;
    if (parts.length == 2) {
      if (isOrder(parts[1])) {
        orderPart = parts[1];
      } else {
        typePart = parts[1];
      }
    } else if (parts.length == 3) {
      typePart = parts[1];
      orderPart = parts[2];
    }
    if (orderPart != null) {
      if ("desc".equalsIgnoreCase(orderPart)) {
        orderDesc = true;
      } else if ("asc".equalsIgnoreCase(orderPart)) {
        orderDesc = false;
      } else {
        out.println("invalid sort order: " + orderPart);
        return null;
      }
    }
    if ("score".equalsIgnoreCase(field)) {
      if (typePart != null) {
        out.println("score sort does not take a type: " + spec);
        return null;
      }
      boolean reverse = orderDesc == null ? false : !orderDesc;
      return new SortField(null, SortField.Type.SCORE, reverse);
    }
    if ("doc".equalsIgnoreCase(field)) {
      if (typePart != null) {
        out.println("doc sort does not take a type: " + spec);
        return null;
      }
      boolean reverse = orderDesc != null && orderDesc;
      return new SortField(null, SortField.Type.DOC, reverse);
    }
    SortField.Type type = SortField.Type.STRING;
    if (typePart != null) {
      type = parseSortType(typePart);
      if (type == null) {
        out.println("invalid sort type: " + typePart);
        return null;
      }
    }
    boolean reverse = orderDesc != null && orderDesc;
    return new SortField(field, type, reverse);
  }

  private static boolean isOrder(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.toLowerCase(Locale.ROOT);
    return "asc".equals(normalized) || "desc".equals(normalized);
  }

  private static SortField.Type parseSortType(String type) {
    String normalized = type.toLowerCase(Locale.ROOT);
    switch (normalized) {
      case "string":
        return SortField.Type.STRING;
      case "int":
        return SortField.Type.INT;
      case "long":
        return SortField.Type.LONG;
      case "float":
        return SortField.Type.FLOAT;
      case "double":
        return SortField.Type.DOUBLE;
      default:
        return null;
    }
  }
}
