#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../target/libs
dist=$bin/../target
plugins_dir=$bin/../plugins

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
#JAVA_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,address=1044,server=y,suspend=y"

CLASSPATH="$dist/*:$lib/*"
if [[ -d "$plugins_dir" ]]; then
  CLASSPATH="$CLASSPATH:$plugins_dir/*"
fi

(cd $bin/..; java $JAVA_OPTS $JAVA_DEBUG $HEAP_OPTS -cp "$CLASSPATH" io.dashbase.clue.client.ClueCommandClient $@)
