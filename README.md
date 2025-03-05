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

latest version: 1.0.0-10.1.0

#### What's new in this release?

See [Release Note](https://github.com/javasoze/clue/releases/tag/release-1.0.0-10.1.0)

### Build:

mvn package

This will create the following artifact in the target directory:

```clue-${VERSION}.jar```

### Run:

Interactive Mode:

    ./bin/clue.sh my-idx

Non-interactive Mode:

    ./bin/clue.sh my-idx command args

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
