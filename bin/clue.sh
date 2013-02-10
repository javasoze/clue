#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../target/lib
dist=$bin/../target

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
JAVA_OPTS="-server -d64"

MAIN_CLASS="com.senseidb.clue.ClueApplication"
CLASSPATH=$resources/:$lib/*:$dist/*:$1/ext/*

java $JAVA_OPTS $JMX_OPTS $HEAP_OPTS -classpath $CLASSPATH $MAIN_CLASS $@