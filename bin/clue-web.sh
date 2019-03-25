#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../target/lib
dist=$bin/../target

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
#JAVA_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,address=1044,server=y,suspend=y"

(cd $bin/..; java $JAVA_OPTS $JAVA_DEBUG $HEAP_OPTS -cp $dist/clue-*.jar io.dashbase.clue.server.ClueWebApplication server config/clue-web.yml)
