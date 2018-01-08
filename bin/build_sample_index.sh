#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../target/lib
dist=$bin/../target

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
JAVA_OPTS="-server -d64"

MAIN_CLASS="com.senseidb.clue.test.BuildSampleIndex"

java $JAVA_OPTS $JMX_OPTS $HEAP_OPTS -cp $dist/clue-6.2.0-1.0.0.jar $MAIN_CLASS $bin/../src/main/resources/cars.json $@