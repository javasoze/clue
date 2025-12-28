#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../build/libs
plugins_dir=$bin/../plugins

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
JAVA_OPTS=""

MAIN_CLASS="io.dashbase.clue.test.BuildSampleIndex"

CLASSPATH="$lib/*"
if [[ -d "$plugins_dir" ]]; then
  CLASSPATH="$CLASSPATH:$plugins_dir/*"
fi

java $JAVA_OPTS $JMX_OPTS $HEAP_OPTS -cp "$CLASSPATH" $MAIN_CLASS $bin/../src/main/resources/cars.json $@
