#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../target/lib
dist=$bin/../target

#HADOOP_CONF_DIR=

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
JAVA_OPTS="-server -d64 -Dhadoop.conf.dir=$HADOOP_CONF_DIR"
#JAVA_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,address=1044,server=y,suspend=y"

(cd $bin/..; java $JAVA_OPTS $JAVA_DEBUG $HEAP_OPTS -jar $dist/clue-6.6.2-1.0.0.jar $@)

