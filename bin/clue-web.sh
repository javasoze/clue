#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../build/libs
config_file=$bin/../config/clue-web.yml
config_dir=$bin/../config

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
#JAVA_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,address=1044,server=y,suspend=y"

MAIN_CLASS="io.dashbase.clue.server.ClueWebApplication"

(cd $bin/..; java $JAVA_OPTS $JAVA_DEBUG $HEAP_OPTS -Dconfig.dir="$config_dir" -Dmicronaut.config.files="$config_file" -classpath "$lib/*" $MAIN_CLASS)
