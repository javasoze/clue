CLue - Command Line tool for Apache Lucene
==========================================

### Overview:

When working with Lucene, it is often useful to inspect an index.

[Luke](http://www.getopt.org/luke/) is awesome, but often times it is not feasible to inspect an index on a remote machine using a GUI. That's where Clue comes in.
You can ssh into your production box and inspect your index using your favorite shell.

Another important feature for Clue is the ability to interact with other Unix commands via piping, e.g. grep, more etc.

#### License:

Clue is under the [Apache Public License v2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

#### Bugs:

Please file bugs and feature requests [here](https://github.com/javasoze/clue/issues).

### Downloads:

latest version: 0.0.6

#### What's new in this release?

* Add support for autocompletion and history
* Lucene 5.0 upgrade
* Support/fix interacting with indexes on hdfs
* Add support for sortednumericdv docvalue type
* Adding cmd to read and save commit data

source: [release-0.0.6.zip](https://github.com/javasoze/clue/archive/release-0.0.6.zip)

clue-all executable jar with all dependencies:
     [clue-all-0.0.6.jar](https://github.com/javasoze/clue/releases/download/release-0.0.6/clue-all-0.0.6.jar)

clue jar with only clue class files, used as a library:
     [clue-0.0.6.jar](https://github.com/javasoze/clue/releases/download/release-0.0.6/clue-0.0.6.jar)

distribution
     [clue-0.0.6.zip](https://github.com/javasoze/clue/releases/download/release-0.0.6/clue-0.0.6.zip)

### Build:

mvn package

This will create 2 artifacts in the target directory:

1. clue-xxx.jar

   jar file containing all clue classes.

2. clue-all-xxx.jar

   executable jar file containing all clue classes as well as all runtime dependencies, e.g. java -jar clue-all-xxx.jar works

### Run:

Interactive Mode:

    ./bin/clue.sh my-idx

Non-interactive Mode:

    ./bin/clue.sh my-idx command args

Command list:

    ./bin/clue.sh my-idx help

    using configuration file found at: /Users/johnwang/github/clue/config/clue.conf
	Analyzer: 		class org.apache.lucene.analysis.standard.StandardAnalyzer
	Query Builder: 		class com.senseidb.clue.api.DefaultQueryBuilder
	Directory Builder: 	class com.senseidb.clue.api.DefaultDirectoryBuilder
	IndexReader Factory: 	class com.senseidb.clue.api.DefaultIndexReaderFactory
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
	postings - iterating postings given a term, e.g. <fieldname:fieldvalue>
	readonly - puts clue in readonly mode
	reconstruct - reconstructs an indexed field for a document
	search - executes a query against the index, input: <query string>
	stored - displays stored data for a given field
	terms - gets terms from the index, <field:term>, term can be a prefix
	trim - trims the index, <TRIM PERCENTAGE> <OPTIONS>, options are: head, tail, random
	tv - shows term vector of a field for a doc
	
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
