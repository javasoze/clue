// Clue JShell Skills - Startup Configuration
// This file is loaded automatically by clue-jshell.sh

// Common imports for all skills
import java.nio.file.Path;
import java.util.*;
import java.util.stream.*;

// Display available skills
void help() {
    System.out.println();
    System.out.println("Clue JShell Skills");
    System.out.println("==================");
    System.out.println();
    System.out.println("Setup:");
    System.out.println("  var indexPath = \"path/to/index\"    // set index path first");
    System.out.println();
    System.out.println("Field Analysis:");
    System.out.println("  /open skills/field-info-load.jsh   // load field info to cache");
    System.out.println("  /open skills/field-info-query.jsh  // query cached field info");
    System.out.println("  /open skills/top-terms.jsh         // top terms by docFreq");
    System.out.println("  /open skills/doc-values.jsh        // show all values for a docid");
    System.out.println();
    System.out.println("Metric Aggregations:");
    System.out.println("  /open skills/sum-agg.jsh           // sum aggregation");
    System.out.println("  /open skills/stats-agg.jsh         // min, max, avg, stats");
    System.out.println("  /open skills/weighted-avg-agg.jsh  // weighted average");
    System.out.println("  /open skills/cardinality-agg.jsh   // approximate distinct count");
    System.out.println("  /open skills/percentile-agg.jsh    // percentiles, MAD (requires t-digest)");
    System.out.println();
    System.out.println("Bucket Aggregations:");
    System.out.println("  /open skills/terms-agg.jsh         // terms bucket aggregation");
    System.out.println("  /open skills/histogram-agg.jsh     // numeric histogram buckets");
    System.out.println("  /open skills/range-agg.jsh         // custom numeric ranges");
    System.out.println("  /open skills/missing-agg.jsh       // docs missing field value");
    System.out.println("  /open skills/filter-agg.jsh        // filter buckets");
    System.out.println();
    System.out.println("Sub-Aggregations:");
    System.out.println("  /open skills/subagg.jsh            // bucket + metric (requires t-digest)");
    System.out.println("  /open skills/nested-agg.jsh        // bucket + bucket + metric (requires t-digest)");
    System.out.println();
    System.out.println("Utilities:");
    System.out.println("  /open skills/codec-support.jsh     // custom codec support");
    System.out.println();
    System.out.println("Type help() to see this message again");
    System.out.println();
}

System.out.println();
System.out.println("╔══════════════════════════════════════════════════════════════╗");
System.out.println("║              Clue JShell Skills Environment                  ║");
System.out.println("╠══════════════════════════════════════════════════════════════╣");
System.out.println("║  Type help() to see available skills                         ║");
System.out.println("║  Set index: var indexPath = \"path/to/index\"                  ║");
System.out.println("╚══════════════════════════════════════════════════════════════╝");
System.out.println();
