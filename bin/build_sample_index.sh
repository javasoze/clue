#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../build/libs

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
JAVA_OPTS=""

MAIN_CLASS="io.dashbase.clue.test.BuildSampleIndex"

java $JAVA_OPTS $JMX_OPTS $HEAP_OPTS -cp $lib/* $MAIN_CLASS $bin/../src/main/resources/cars.json $@
