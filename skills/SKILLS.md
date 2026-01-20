# Clue JShell Skills

JShell scripts for analyzing Lucene indexes using pure Lucene API.

## Quick Start

```bash
# Use the launcher script (recommended)
./skills/clue-jshell.sh              # interactive mode
./skills/clue-jshell.sh cars         # with index path preset

# Then load skills
jshell> help()                        # show available skills
jshell> /open skills/terms-agg.jsh    # load a skill
```

## Manual Setup

```bash
# Start jshell with Lucene on classpath
jshell --class-path "build/libs/*"

# For percentile skills, include t-digest
jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"

# Set index path
jshell> var indexPath = "/path/to/index"

# Load a skill
jshell> /open skills/<skill-name>.jsh
```

## Configuration Files

| File | Purpose |
|------|---------|
| `clue-jshell.sh` | Launcher script with correct classpath |
| `startup.jsh` | Auto-loaded imports and help() function |
| `skills-config.json` | Index aliases and default settings |
| `codec-config.json` | Custom codec JAR mappings (auto-generated) |

## Available Skills

### field-info-load.jsh
Loads field info from all segments into a cache file for querying.

```java
var indexPath = "myidx"
/open skills/field-info-load.jsh
// Output: field-info-cache.json
```

### field-info-query.jsh
Query functions for cached field info. Run after `field-info-load.jsh`.

```java
/open skills/field-info-query.jsh

summary()                  // index stats by docval type, index options
listFields()               // list all field names
listFields("user.*")       // list fields matching regex pattern
listFields(".*", 50)       // list with custom limit
fieldInfo("fieldName")     // full details for a specific field
```

### top-terms.jsh
Output top N terms for a field ordered by docFreq descending.

```java
var indexPath = "myidx"
/open skills/top-terms.jsh

topTerms("fieldName", 10)  // top 10 terms by docFreq
closeIndex()               // close when done
```

Returns error if field is not indexed (no term dictionary).

### doc-values.jsh
Output all field values for a given docid (stored fields + docvalues).

```java
var indexPath = "myidx"
/open skills/doc-values.jsh

docValues(0)               // show all values for doc 0
docValues(100)             // show all values for doc 100
closeDocIndex()            // close when done
```

- Skips fields with no values for the document
- For binary data: attempts UTF-8 decode, falls back to base64
- Handles all docvalue types: NUMERIC, BINARY, SORTED, SORTED_SET, SORTED_NUMERIC

### codec-info.jsh
Extract codec name and class name from all segments in a Lucene index.

```java
var indexPath = "myidx"
/open skills/codec-info.jsh

codecInfo()                // show codec info for all segments
closeCodecIndex()          // close when done
```

**Output:**
```
Reading index: myidx
Segments: 3

Segment: _0
  Codec name:  Lucene912
  Codec class: org.apache.lucene.codecs.lucene912.Lucene912Codec

Summary:
  Total segments: 3
  Unique codecs: 1
  - Lucene912 (org.apache.lucene.codecs.lucene912.Lucene912Codec): 3 segment(s)
```

Useful for:
- Identifying which Lucene codec version was used to write the index
- Detecting mixed-codec indexes (different segments with different codecs)
- Debugging codec compatibility issues

### index-analysis.jsh
Unified interface for comprehensive index file analysis. Generates metadata with exact byte offsets.

```java
var indexPath = "myidx"
/open skills/index-analysis.jsh

analyzeIndex()           // Print index summary, files, and fields
generateMeta()           // Generate index-meta.json with file offsets
fieldFiles("color")      // Show which files contain a field
closeAnalysis()
```

**Output: index-meta.json**
```json
{
  "indexPath": "myidx",
  "codec": "Lucene101",
  "segments": [{
    "name": "_0",
    "compoundEntries": [
      {"file": ".tim", "offset": 6416, "length": 10230},
      {"file": ".dvd", "offset": 1163056, "length": 5172821}
    ]
  }],
  "fields": [{"name": "color", "indexed": false, "docValuesType": "SORTED"}]
}
```

### compound-files.jsh
Parse compound files (.cfe/.cfs) to extract embedded file offsets.

```java
var indexPath = "myidx"
/open skills/compound-files.jsh

listSegments()              // List all segments
compoundEntries("_0")       // Show files embedded in _0.cfs
compoundEntriesJson("_0")   // Same as JSON
closeCompoundIndex()
```

**Output:**
```
Compound file: _0.cfs (6335893 bytes)
File                                         Offset          Length
----------------------------------------------------------------------
.fdx                                             48             114
_Lucene101_0.tim                               6416           10230
_Lucene90_0.dvd                             1163056         5172821
```

### term-offsets.jsh
Extract term and posting byte offsets using reflection on internal Lucene state.

```java
var indexPath = "myidx"
/open skills/term-offsets.jsh

termOffset("color_indexed", "red")       // Show term location info
termOffsetJson("color_indexed", "red")   // Same as JSON
postingOffset("color_indexed", "red")    // Show posting file info
closeTermIndex()
```

**Output:**
```json
{
  "term": "color_indexed:red",
  "docFreq": 2160,
  "locations": {
    "term": {"file": ".tim", "offset": 1053, "length": 85},
    "postings": {"file": ".doc", "offset": 34636}
  }
}
```

### docvalue-offsets.jsh
Extract docvalue file offsets for fields with SORTED, NUMERIC, or other docvalue types.

```java
var indexPath = "myidx"
/open skills/docvalue-offsets.jsh

listDocValueFields()              // List fields with docvalues
docValueOffset("color")           // Show docvalue location info
docValueOffsetJson("color")       // Same as JSON
closeDocValueIndex()
```

**Output:**
```
Field: color
DocValuesType: SORTED

Ordinals entry:
  valuesOffset: 144929
  valuesLength: 7500

Terms dictionary entry:
  termsDataOffset: 152429
  termsDataLength: 46
```

### io-metadata.jsh
Generate comprehensive index metadata with exact byte offsets. This reads the index ONCE and produces a JSON file that can be used for I/O planning without touching the index again.

```java
var indexPath = "myidx"
/open skills/io-metadata.jsh

generateIOMetadata()    // Generates io-metadata.json
```

**Output: io-metadata.json**
```json
{
  "indexPath": "myidx",
  "generatedAt": "2025-01-20T21:01:54.603150Z",
  "totalDocs": 15000,
  "segments": [{
    "name": "_0",
    "docCount": 15000
  }],
  "compoundFile": {
    "_0.cfs": {
      ".tim": {"offset": 6416, "length": 10230},
      ".dvd": {"offset": 1163056, "length": 5172821}
    }
  },
  "indexedFields": {
    "color_indexed": {
      "termCount": 8,
      "terms": {
        "red": {"docFreq": 2160, "docStartFP": 34636, "length": 1996},
        "blue": {"docFreq": 1104, "docStartFP": 36632, "length": 1020}
      }
    }
  },
  "docValueFields": {
    "price": {
      "type": "NUMERIC",
      "dataOffset": 1163113,
      "dataLength": 15000
    },
    "color": {
      "type": "SORTED",
      "ordinalsOffset": 1307985,
      "ordinalsLength": 7500,
      "termsOffset": 1315485,
      "termsLength": 46
    }
  },
  "storedFields": {
    "chunks": [
      {"firstDoc": 0, "offset": 161678, "length": 12785},
      {"firstDoc": 1024, "offset": 174463, "length": 12800}
    ]
  }
}
```

### io-planner-v2.jsh
Analyze I/O requirements using pre-generated metadata. This NEVER reads from the index - all data comes from `io-metadata.json`.

**Two-phase workflow:**
1. Generate metadata once: `/open skills/io-metadata.jsh && generateIOMetadata()`
2. Plan I/O using metadata: `/open skills/io-planner-v2.jsh`

```java
/open skills/io-planner-v2.jsh

loadIOMetadata("io-metadata.json")    // Load pre-generated metadata

// List available fields and terms
listFields()                          // Show all indexed and docvalue fields
listTerms("color_indexed")            // Show terms for a field

// Plan operations (no index I/O!)
planSum("price")                      // SUM aggregation
planAvg("price")                      // AVG aggregation
planTermQuery("color_indexed", "red") // Term query
planRangeQuery("price", 7000, 12000)  // Numeric range query
planTermsAgg("color")                 // Terms aggregation
planGetDoc(500)                       // Stored fields retrieval
```

**Example Output:**
```
================================================================================
I/O PLAN: TermQuery(color_indexed:red)
================================================================================

#    File         Component                    Offset       Length  Purpose
--------------------------------------------------------------------------------
1    _0.cfs       .tip                            760          192  Term index
2    _0.cfs       .tim (block)                  7,469           85  Term dictionary block
3    _0.cfs       .doc (postings)             895,428        1,996  Posting list (2160 docs)
--------------------------------------------------------------------------------
TOTAL: 2,273 bytes (3 blocks)

JSON:
[
  {"file": "_0.cfs", "component": ".tip", "offset": 760, "length": 192},
  {"file": "_0.cfs", "component": ".tim (block)", "offset": 7469, "length": 85},
  {"file": "_0.cfs", "component": ".doc (postings)", "offset": 895428, "length": 1996}
]
docFreq: 2160
```

**Supported Operations:**

| Operation | I/O Pattern | Files Read |
|-----------|-------------|------------|
| `planSum/planAvg` | DocValues scan | .dvm + .dvd |
| `planTermQuery` | Inverted index | .tip + .tim + .doc |
| `planRangeQuery` | DocValues scan | .dvm + .dvd |
| `planTermsAgg` | DocValues scan | .dvm + .dvd (ordinals + terms) |
| `planGetDoc` | Stored fields | .fdx + .fdm + .fdt (chunk) |

**Key Features:**
- **No index I/O during planning**: All data from pre-generated JSON
- **Exact offsets**: No estimates, all values are precise byte positions
- **Warnings for missing data**: If metadata is incomplete, logs what's missing

### io-planner.jsh (Legacy)
Original I/O planner that reads from the index during planning. **Superseded by io-planner-v2.jsh** which uses pre-generated metadata.

```java
var indexPath = "myidx"
/open skills/io-planner.jsh

// Aggregations - shows I/O plan then executes
planSum("price")                        // SUM aggregation
planAvg("price")                        // AVG aggregation
planTermsAgg("color")                   // Terms aggregation (bucket)

// Queries - shows I/O plan then executes
planTermQuery("color_indexed", "red")   // Term query
planRangeQuery("price", 7000, 12000)    // Numeric range query

// Document retrieval
planGetDoc(5000)                        // Stored fields for doc 5000

closeIOPlanner()
```

**Use Cases:**
- Understanding Lucene I/O patterns for different query types
- Capacity planning and I/O optimization
- Debugging slow queries by examining data access patterns
- Educational: see exactly how Lucene resolves queries to file blocks

### sum-agg.jsh
OpenSearch-style sum aggregation for numeric fields.
See: https://docs.opensearch.org/latest/aggregations/metric/sum/

```java
var indexPath = "myidx"
/open skills/sum-agg.jsh

// All documents
sum("mileage")                                  // long field (default)
sum("price", "double")                          // double field (uses longBitsToDouble)
sum("price", "double", 0.0)                     // with missing value
sumWithScript("price", "_value * 100", "double")

// Explicit docid list filter
sumDocs("mileage", new int[]{0, 1, 5, 10, 100})
sumDocs("price", "double", new int[]{0, 1, 5})
sumDocsWithScript("price", "_value * 100", "double", new int[]{0, 1})

// Query filter (uses Lucene QueryParser syntax)
sumQuery("mileage", "color_indexed:red")
sumQuery("price", "double", "year:[2000 TO *]")
sumQueryWithScript("price", "_value * 100", "double", "color_indexed:red AND year:>2000")

closeSumIndex()                                 // close when done
```

**Field types:**
| Type | Handling |
|------|----------|
| `"long"` / `"l"` | Raw long value (default) |
| `"int"` / `"i"` | Cast to double |
| `"double"` / `"d"` | `Double.longBitsToDouble()` + Kahan summation |
| `"float"` / `"f"` | Same as double |

**Supported scripts:**
- Arithmetic: `_value * N`, `_value + N`, `_value / N`, `_value - N`
- Math functions: `Math.sqrt(_value)`, `Math.abs(_value)`, `Math.pow(_value, N)`

**Parameters (matching OpenSearch API):**
| Parameter | Description |
|-----------|-------------|
| `field` | The numeric docvalues field to sum |
| `type` | Field type: `"long"` (default), `"double"`, `"float"`, `"int"` |
| `missing` | Default value for documents missing the field |
| `script` | Expression to transform values (uses `_value` variable) |

**Document filtering:**
| Method | Description |
|--------|-------------|
| `sum()` / `sumWithScript()` | Aggregate over all documents |
| `sumDocs()` / `sumDocsWithScript()` | Aggregate over explicit docid list |
| `sumQuery()` / `sumQueryWithScript()` | Aggregate over documents matching a query |

**Query syntax:** Uses Lucene QueryParser with StandardAnalyzer. Examples:
- `"field:value"` - term query
- `"field:[min TO max]"` - range query
- `"field:>100"` - range query (greater than)
- `"field1:a AND field2:b"` - boolean query
- `"*"` - match all documents

**Note:** Double/float types use Kahan summation algorithm to maintain precision when summing many floating-point values.

### stats-agg.jsh
OpenSearch-style stats aggregations: min, max, valueCount, stats, extendedStats.
See: https://docs.opensearch.org/latest/aggregations/metric/stats/

```java
var indexPath = "myidx"
/open skills/stats-agg.jsh

// Single value aggregations
min("price", "double")
max("price", "double")
valueCount("price")

// Combined stats (count, min, max, avg, sum)
stats("price", "double")

// Extended stats (+ variance, stdDev, sumOfSquares)
extendedStats("price", "double")

// With query filter
statsQuery("price", "double", "color_indexed:red")
extendedStatsQuery("price", "double", "year:[2010 TO *]")

// With docid list
statsDocs("price", "double", new int[]{0, 1, 2, 3, 4})

closeStatsIndex()
```

### weighted-avg-agg.jsh
OpenSearch-style weighted average aggregation.
See: https://docs.opensearch.org/latest/aggregations/metric/weighted-avg/

```java
var indexPath = "myidx"
/open skills/weighted-avg-agg.jsh

// Weighted average: sum(value * weight) / sum(weight)
weightedAvg("price", "quantity", "double")
weightedAvg("price", "quantity", "double", "long")  // different types

// With query filter
weightedAvgQuery("price", "quantity", "double", "color_indexed:red")

// With docid list
weightedAvgDocs("price", "quantity", "double", new int[]{0, 1, 2})

closeWavgIndex()
```

### cardinality-agg.jsh
OpenSearch-style cardinality aggregation using HyperLogLog algorithm.
See: https://docs.opensearch.org/latest/aggregations/metric/cardinality/

```java
var indexPath = "myidx"
/open skills/cardinality-agg.jsh

// Approximate distinct count
cardinality("color_indexed")          // default precision (14)
cardinality("color_indexed", 10)      // custom precision (4-18)

// With query filter
cardinalityQuery("color_indexed", "year:[2010 TO *]")

closeCardIndex()
```

**Precision settings:**
| Precision | Memory | Error Rate |
|-----------|--------|------------|
| 10 | ~1KB | ~1.04% |
| 14 | ~16KB | ~0.81% (default) |
| 18 | ~256KB | ~0.41% |

### percentile-agg.jsh
OpenSearch-style percentile aggregations using T-Digest algorithm.
See: https://docs.opensearch.org/latest/aggregations/metric/percentile/

**Requires t-digest library:**
```bash
# Download t-digest JAR
wget -O plugins/t-digest-3.3.jar https://repo1.maven.org/maven2/com/tdunning/t-digest/3.3/t-digest-3.3.jar

# Start jshell with t-digest on classpath
jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"
```

```java
var indexPath = "myidx"
/open skills/percentile-agg.jsh

// Percentiles - values at given percentile ranks
percentiles("price", "double", new double[]{25, 50, 75, 95, 99})
percentilesQuery("price", "double", new double[]{50, 99}, "color_indexed:red")

// Percentile Ranks - what percentile is a given value at
percentileRanks("price", "double", new double[]{5000, 10000, 15000})

// Median Absolute Deviation
medianAbsoluteDeviation("price", "double")
madQuery("price", "double", "color_indexed:red")

closePctIndex()
```

### terms-agg.jsh
OpenSearch-style terms bucket aggregation for SORTED/SORTED_SET doc values.
See: https://docs.opensearch.org/latest/aggregations/bucket/terms/

```java
var indexPath = "myidx"
/open skills/terms-agg.jsh

// Basic terms aggregation (top 10 by count)
terms("color")
terms("color", 20)                    // top 20 terms
terms("color", 20, "term")            // alphabetically sorted
terms("color", 20, "count", 5)        // minDocCount=5

// With indexed field query filter
termsQuery("color", "color_indexed:red")
termsQuery("color", "contents:toyota", 20, "count")

// With numeric doc value range filter
termsRange("color", "price", "double", 10000, Double.MAX_VALUE)  // price > 10000
termsRange("color", "year", "long", 2010, 2020)                  // year 2010-2020

// With docid list
termsDocs("color", new int[]{0, 1, 2, 3, 4, 5})
termsDocs("color", new int[]{0, 1, 2}, 10, "term")

closeTermsIndex()
```

**Order options:**
| Order | Description |
|-------|-------------|
| `"count"` | By doc count descending (default) |
| `"count_asc"` | By doc count ascending |
| `"term"` | Alphabetically ascending |
| `"term_desc"` | Alphabetically descending |

**Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `field` | Field with SORTED or SORTED_SET doc values | required |
| `size` | Max buckets to return | 10 |
| `order` | Bucket ordering | `"count"` |
| `minDocCount` | Min docs per bucket | 1 |

**Filtering methods:**
| Method | Use Case |
|--------|----------|
| `termsQuery()` | Filter by indexed text fields (uses QueryParser) |
| `termsRange()` | Filter by numeric doc value range (for non-indexed numeric fields) |
| `termsDocs()` | Filter by explicit docId list |

**Note:** Uses ordinal-based counting within segments for efficiency with large document counts (100M+). String lookups only occur for unique terms, not per-document.

### histogram-agg.jsh
OpenSearch-style histogram bucket aggregation for numeric fields.
See: https://docs.opensearch.org/latest/aggregations/bucket/histogram/

```java
var indexPath = "myidx"
/open skills/histogram-agg.jsh

// Basic histogram (buckets by fixed interval)
histogram("price", 5000, "double")              // buckets: 0-5000, 5000-10000, ...
histogram("mileage", 10000)                     // long field
histogram("price", 5000, "double", 10)          // minDocCount=10

// With filters
histogramQuery("price", 5000, "double", "color_indexed:red")
histogramRange("price", 5000, "double", "mileage", "long", 0, 50000)
histogramDocs("price", 5000, "double", new int[]{0, 1, 2, 3})

closeHistIndex()
```

### range-agg.jsh
OpenSearch-style range bucket aggregation for numeric fields.
See: https://docs.opensearch.org/latest/aggregations/bucket/range/

```java
var indexPath = "myidx"
/open skills/range-agg.jsh

// Basic range (custom boundaries)
range("price", "double", new double[]{0, 5000, 10000, 20000})

// With named buckets
rangeNamed("price", "double",
    new String[]{"cheap", "mid", "expensive"},
    new double[]{0, 5000, 15000})

// With filters
rangeQuery("price", "double", new double[]{0, 10000, 20000}, "color_indexed:red")
rangeFilter("price", "double", new double[]{0, 10000}, "mileage", "long", 0, 50000)
rangeDocs("price", "double", new double[]{0, 10000}, new int[]{0, 1, 2})

closeRangeIndex()
```

### missing-agg.jsh
OpenSearch-style missing bucket aggregation.
See: https://docs.opensearch.org/latest/aggregations/bucket/missing/

```java
var indexPath = "myidx"
/open skills/missing-agg.jsh

// Count docs missing a field value
missing("color")
missingQuery("color", "year:[2010 TO *]")
missingDocs("color", new int[]{0, 1, 2, 3, 4})

closeMissIndex()
```

### filter-agg.jsh
OpenSearch-style filter bucket aggregations (single and multiple).
See: https://docs.opensearch.org/latest/aggregations/bucket/filter/

```java
var indexPath = "myidx"
/open skills/filter-agg.jsh

// Single filter - count matching docs
filter("color_indexed:red")
filter("price:[10000 TO *] AND color_indexed:black")

// Multiple filters - named buckets
filters(new String[]{"red", "blue", "other"},
        new String[]{"color_indexed:red", "color_indexed:blue", "*"})

// Multiple filters - auto-named
filters(new String[]{"color_indexed:red", "color_indexed:blue"})

closeFilterIndex()
```

### subagg.jsh
OpenSearch-style bucket + metric sub-aggregations.
Combines bucket aggregations (terms, histogram, range) with metric aggregations.

**Requires t-digest library:**
```bash
jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"
```

```java
var indexPath = "myidx"
/open skills/subagg.jsh

// Terms + Metric (group by color, compute metric on price)
termsBy("color", "price", "double", "p95", 10)      // top 10 by 95th percentile
termsBy("color", "price", "double", "avg", 5)       // top 5 by average
termsBy("color", "mileage", "long", "max", 3)       // top 3 by max mileage
termsBy("color", "price", "double", "p95", 10, "key")  // sort by color name

// Histogram + Metric (bucket by price interval, compute metric on mileage)
histogramBy("price", 5000, "double", "mileage", "long", "avg")
histogramBy("year", 1, "long", "price", "double", "p50")

// Range + Metric (custom price ranges, compute metric on mileage)
rangeBy("price", "double", new double[]{0, 10000, 20000}, "mileage", "long", "avg")
rangeBy("price", "double", new double[]{0, 10000, 20000},
        new String[]{"budget", "mid", "luxury"}, "mileage", "long", "p50")

closeSubIndex()
```

**Supported metrics:**
| Metric | Description |
|--------|-------------|
| `p50`, `median` | 50th percentile |
| `p75`, `p90`, `p95`, `p99` | Other percentiles |
| `min`, `max` | Minimum/maximum value |
| `avg` | Average value |
| `sum` | Sum of values |
| `count` | Document count |

**Sort options:** `"metric"` (default, descending) or `"key"` (bucket key)

### nested-agg.jsh
OpenSearch-style nested bucket aggregations (two levels of buckets + metric).
Combines histogram, terms, or range bucket aggregations with inner terms or histogram buckets.

**Requires t-digest library:**
```bash
jshell --class-path "build/libs/*:plugins/t-digest-3.3.jar"
```

```java
var indexPath = "myidx"
/open skills/nested-agg.jsh

// Histogram -> Terms -> Metric (by year, top colors by p90 price)
histogramTermsBy("year", 1, "long", "color", "price", "double", "p90", 5)
histogramTermsBy("year", 1, "long", "color", "price", "double", "p90", 5, "key")  // sort inner by term

// Terms -> Terms -> Metric (by make, top colors by avg price)
termsTermsBy("make", "color", "price", "double", "avg", 5, 3)
termsTermsBy("make", "color", "price", "double", "avg", 5, 3, "count", "metric")

// Range -> Terms -> Metric (price ranges, top colors by avg mileage)
rangeTermsBy("price", "double", new double[]{0, 10000, 20000}, "color", "mileage", "long", "avg", 5)
rangeTermsBy("price", "double", new double[]{0, 10000, 20000},
             new String[]{"budget", "mid", "luxury"}, "color", "mileage", "long", "p50", 5)

// Terms -> Histogram -> Metric (by color, yearly avg price)
termsHistogramBy("color", "year", 1, "long", "price", "double", "avg", 5)

closeNestedIndex()
```

**Supported patterns:**
| Pattern | Description |
|---------|-------------|
| `histogramTermsBy()` | Numeric intervals → categorical terms → metric |
| `termsTermsBy()` | Categorical → categorical → metric |
| `rangeTermsBy()` | Custom ranges → categorical terms → metric |
| `termsHistogramBy()` | Categorical → numeric intervals → metric |

**Supported metrics:** `p50`, `p75`, `p90`, `p95`, `p99`, `min`, `max`, `avg`, `sum`, `count`

**Sort options:**
- Outer buckets: `"count"` (default) or `"key"`/`"term"`
- Inner buckets: `"metric"` (default) or `"key"`/`"term"`

### codec-support.jsh
Utilities for handling indexes built with custom codecs.

```java
/open skills/codec-support.jsh

registerCodec("MyCodec", "/path/to/codec.jar")  // register a codec JAR
```

## Custom Codec Support

If your index was built with a custom codec not included in the standard Lucene JARs, the skills will detect this and provide instructions.

### Workflow

1. **Error Detection**: When a skill tries to open an index with a missing codec, you'll see:
   ```
   !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   ERROR: Codec 'MyCustomCodec' not found!
   !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   ```

2. **Register the Codec**: Load codec-support and register your codec JAR:
   ```java
   /open skills/codec-support.jsh
   registerCodec("MyCustomCodec", "/path/to/my-codec.jar")
   ```

3. **Update Classpath**: Run the `/env` command shown:
   ```java
   /env -class-path build/libs/*:/path/to/my-codec.jar
   ```

4. **Reload the Skill**: Re-run `/open skills/<skill>.jsh`

### Config Persistence

Registered codecs are saved to `codec-config.json` in the working directory:
```json
{
  "codecs": {
    "MyCustomCodec": "/path/to/my-codec.jar"
  }
}
```

On subsequent sessions, `codec-support.jsh` will load this config and suggest the appropriate classpath.

### Example: Using a Custom Codec

```bash
# Start jshell
jshell --class-path "build/libs/*"

# Try to open an index with custom codec - will fail
jshell> var indexPath = "/path/to/custom-index"
jshell> /open skills/field-info-load.jsh
# ERROR: Codec 'MyCodec' not found!

# Register the codec
jshell> /open skills/codec-support.jsh
jshell> registerCodec("MyCodec", "plugins/my-codec.jar")
# Run: /env -class-path build/libs/*:plugins/my-codec.jar

# Update classpath
jshell> /env -class-path build/libs/*:plugins/my-codec.jar

# Now reload the skill - it will work
jshell> /open skills/field-info-load.jsh
# Success!
```
