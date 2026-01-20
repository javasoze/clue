#!/bin/bash
# Launch jshell with Lucene + t-digest on classpath for Clue skills
#
# Usage:
#   ./skills/clue-jshell.sh [index_path]
#
# Examples:
#   ./skills/clue-jshell.sh                    # interactive, set indexPath manually
#   ./skills/clue-jshell.sh cars               # set indexPath="cars" on startup
#   ./skills/clue-jshell.sh /path/to/myindex   # absolute path

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Build classpath
CLASSPATH="$PROJECT_DIR/build/libs/*"

# Add t-digest if available
if [ -f "$PROJECT_DIR/plugins/t-digest-3.3.jar" ]; then
    CLASSPATH="$CLASSPATH:$PROJECT_DIR/plugins/t-digest-3.3.jar"
fi

# Add any custom codec JARs from plugins/
for jar in "$PROJECT_DIR/plugins/"*.jar; do
    if [ -f "$jar" ] && [[ "$jar" != *"t-digest"* ]]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

# Build startup commands
STARTUP=""
if [ -n "$1" ]; then
    STARTUP="var indexPath = \"$1\""
fi

# Launch jshell
if [ -n "$STARTUP" ]; then
    echo "$STARTUP" | jshell --class-path "$CLASSPATH" --startup "$SCRIPT_DIR/startup.jsh" -
else
    jshell --class-path "$CLASSPATH" --startup "$SCRIPT_DIR/startup.jsh"
fi
