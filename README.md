CLue - Command Line tool for Apache Lucene
==========================================

### Build:

mvn package

### Run:

Interactive Mode:

    ./bin/clue.sh my-idx

Non-interactive Mode:

    ./bin/clue.sh my-idx command args

Command list:

    ./bin/clue.sh my-idx help

### Examples:

1. Getting all the terms in the field 'color':

    **./bin/clue.sh /tmp/my-idx terms color**

2. Getting all the terms in the field 'color' starting with the term staring with 'r':

    **./bin/clue.sh /tmp/my-idx terms color:r**

    **./bin/clue.sh /tmp/my-idx terms color | grep r**

3. Do a search:

    **./bin/clue.sh /tmp/my-idx search myquery**

4. Get the index info:

    **./bin/clue.sh /tmp/my-idx info**

5. Iterate a posting for the term color:red

    **./bin/clue.sh /tmp/my-idx postings color:red**

6. List docvalues for a column-stride-field:

    **./bin/clue.sh /tmp/my-idx docval price**
