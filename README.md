CLue - Command Line tool for Apache Lucene
==========================================

### Overview:

When working with Lucene, it is often necessary to inspect an index for debugging, analysis, or optimization purposes.

[Luke](https://github.com/apache/lucene/tree/main/lucene/luke) provides a powerful graphical interface for examining Lucene indexes. However, in many scenarios, particularly when working on a remote machine, using a GUI-based tool may not be practical. This is where Clue proves to be a valuable alternative.

Clue allows users to inspect Lucene indexes directly from the command line, making it ideal for remote environments. By connecting via SSH to a production server, users can analyze their indexes without the need for a graphical interface.

Additionally, Clue is designed to seamlessly integrate with Unix command-line utilities through piping. This enables users to leverage tools such as grep, more, and other shell commands to filter and process index data efficiently.


#### License:

Clue is under the [Apache Public License v2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

#### Bugs:

Please file bugs and feature requests [here](https://github.com/javasoze/clue/issues).

### Downloads:

latest version: 1.0.0-10.2.2

#### What's new in this release?

See [Release Note](https://github.com/javasoze/clue/releases/tag/release-1.0.0-10.2.2)

### Build:

gradle build

This will create the following artifact in the build/libs directory:

```clue-${VERSION}.jar```

### Run:

Start Clue (CLI):

Interactive mode:

    ./bin/clue.sh my-idx

Non-interactive mode:

    ./bin/clue.sh my-idx command args

Start Clue Web:

1) Update `clue.web.dir` in `config/clue-web.yml` to point at your index.
2) Run the web app:

    ./bin/clue-web.sh

The service starts on port 8080 by default and exposes endpoints under `/clue`.

Command list:
```bash
    ./bin/clue.sh my-idx help

    using configuration file found at: /Users/johnwang/github/clue/config/clue.conf
	Analyzer: 		class org.apache.lucene.analysis.standard.StandardAnalyzer
	Query Builder: 		class io.dashbase.clue.api.DefaultQueryBuilder
	Directory Builder: 	class io.dashbase.clue.api.DefaultDirectoryBuilder
	IndexReader Factory: 	class io.dashbase.clue.api.DefaultIndexReaderFactory
	count - shows how many documents in index match the given query
	delete - deletes a list of documents from searching via a query, input: query
	directory - prints directory information
	docsetinfo - doc id set info and stats
	docval - gets doc value for a given doc, <field> <docid>, if <docid> not specified, all docs are shown
	exit - exits program
	explain - shows score explanation of a doc
	export - export index to readable text files
	help - displays help
	info - displays information about the index, <segment number> to get information on the segment
	merge - force merges segments into given N segments, input: number of max segments
	norm - displays norm values for a field for a list of documents
	points - gets points values from the index, e.g. <field:value>
	postings - iterating postings given a term, e.g. <fieldname:fieldvalue>
	readonly - puts clue in readonly mode
	reconstruct - reconstructs an indexed field for a document
	search - executes a query against the index, input: <query string>
	stored - displays stored data for a given field
	terms - gets terms from the index, <field:term>, term can be a prefix
	trim - trims the index, <TRIM PERCENTAGE> <OPTIONS>, options are: head, tail, random
	tv - shows term vector of a field for a doc
```

### Command Reference

All commands support `-h`/`--help` for detailed usage.

```
count
  -q, --query <query...>          Optional. Defaults to "*" (match all).

delete
  -q, --query <query>             Required.

directory
  (no options)

docsetinfo
  -f, --field <field:term>        Required.
  -s, --size <bucketSize>         Optional. Default: 1000.

docval
  -f, --field <field>             Required.
  -d, --docs <docIds...>          Optional. If omitted, all docs are shown.
  -n, --num <numPerPage>          Optional. Default: 20.

dumpdoc
  -d, --doc <docId>               Required.

explain
  -q, --query <query...>          Required.
  -d, --docs <docIds...>          Required.

export
  -o, --output <dir>              Required.
  -t, --text [true|false]         Optional. Default: true.

help
  (no options)

info
  -s, --seg <segmentId>           Optional. Default: -1 (summary view).

merge
  -n, --num <segments>            Optional. Default: 1.

norm
  -f, --field <field>             Required.
  -d, --docs <docIds...>          Optional. If omitted, all docs are shown.
  -n, --num <numPerPage>          Optional. Default: 20.

points
  -f, --field <field:value>       Required.

postings
  -f, --field <field:term>        Required.
  -n, --num <numPerPage>          Optional. Default: 20.

readonly
  readonly <true|false>           Required positional argument.

reconstruct
  -f, --field <field>             Required.
  -d, --doc <docId>               Required.

search
  -q, --query <query...>          Optional. Defaults to "*" (match all).
  -n, --num <numHits>             Optional. Default: 10.
  -s, --sort <sort...>            Optional. field[:type[:asc|desc]] or score/doc; type: string|int|long|float|double.

showcommitdata
  (no options)

savecommitdata
  -k, --key <key>                 Required.
  -v, --value <value>             Required.

deletecommitdata
  -k, --key <key>                 Required.

stored
  -f, --field <field>             Required.
  -d, --doc <docId>               Required.

terms
  -f, --field <field[:term|prefix*]> Required.

trim
  -p, --percent <0-100>           Required.

tv
  -f, --field <field>             Required.
  -d, --doc <docId>               Required.

exit (interactive mode only)
  (no options)
```

### Directory Provider Plugins (ServiceLoader)

Clue loads directory providers via the Java ServiceLoader framework. This lets you add custom Lucene Directory implementations without changing Clue itself.

Usage:

    ./bin/clue.sh --dir-provider fs my-idx info

If not set, the provider defaults to `fs`.

Configure in `config/clue.yml`:

```yaml
dirBuilder:
  type: default
  provider: fs
  options:
    key: value
```

Implement a provider:

```java
public class MyDirectoryProvider implements DirectoryProvider {
  @Override
  public String getName() {
    return "my-provider";
  }

  @Override
  public Directory build(String location, ParsedOptions options) throws IOException {
    // Create and return your Directory implementation here.
  }
}
```

Register it in your plugin jar:

```
META-INF/services/io.dashbase.clue.api.DirectoryProvider
```

with contents:

```
com.example.MyDirectoryProvider
```

Place your plugin jar on the classpath (for example, copy it into `build/libs`).

### Command Plugins (ServiceLoader)

Command plugins are discovered via ServiceLoader and are registered at startup. Any command you return is available via `help` and can be invoked like built-in commands.

Implement a plugin:

```java
public class MyCommandPlugin implements CommandPlugin {
  @Override
  public String getName() {
    return "my-plugin";
  }

  @Override
  public Collection<ClueCommand> createCommands(ClueContext ctx) {
    return Collections.singletonList(new HelloCommand(ctx));
  }
}
```

Implement a command:

```java
@Readonly
@Command(name = "hello", mixinStandardHelpOptions = true)
public class HelloCommand extends ClueCommand {
  public HelloCommand(ClueContext ctx) {
    super(ctx);
  }

  @Override
  public String getName() {
    return "hello";
  }

  @Override
  public String help() {
    return "prints a greeting";
  }

  @Override
  protected void run(PrintStream out) {
    out.println("hello from plugin");
  }
}
```

Register it in your plugin jar:

```
META-INF/services/io.dashbase.clue.commands.CommandPlugin
```

with contents:

```
com.example.MyCommandPlugin
```

Place your plugin jar on the classpath (for example, copy it into `build/libs`). Use `@Readonly` if you want the command to appear while in read-only mode.
	
### Build a sample index to play with:

Clue bundles with some test data (15000 car data) for you to build a sample index to play with, do:

    ./bin/build_sample_index.sh my-idx
	

### Examples:

1. Getting all the terms in the field 'color_indexed':

    **./bin/clue.sh my-idx terms color_indexed**

2. Getting all the terms in the field 'color_indexed' starting with the term staring with 'r':

    **./bin/clue.sh my-idx terms color_indexed:r**

    **./bin/clue.sh my-idx terms color_indexed | grep r**

3. Do a search:

    **./bin/clue.sh my-idx search myquery**

4. Get the index info:

    **./bin/clue.sh my-idx info**

5. Iterate a posting for the term color_indexed:red

    **./bin/clue.sh my-idx postings color_indexed:red**

6. List docvalues for the column-stride-field color:

    **./bin/clue.sh my-idx docval color**

7. Get docvalue for the column-stride-field *category* for document 4:

    **./bin/clue.sh my-idx docval *category* 5**

8. Get docvalue for the column-stride-field *year* of type numeric for document 3:

	**./bin/clue.sh my-idx docval year 3**
	
9. Get docvalue for the column-stride-field *json* of type binary for document 3:

	**./bin/clue.sh my-idx docval json 3**
	
9. Get docvalue for the column-stride-field *tags* of type sorted-set for document 3:

	**./bin/clue.sh my-idx docval tags 3**
