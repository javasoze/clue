#!/usr/bin/env bash

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

lib=$bin/../build/libs

HEAP_OPTS="-Xmx1g -Xms1g -XX:NewSize=256m"
#JAVA_DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,address=1044,server=y,suspend=y"

MAIN_CLASS="io.dashbase.clue.ClueApplication"

DIR_PROVIDER=""
ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dir-provider)
      if [[ -z "$2" ]]; then
        echo "missing value for --dir-provider" >&2
        exit 1
      fi
      DIR_PROVIDER="$2"
      shift 2
      ;;
    --dir-provider=*)
      DIR_PROVIDER="${1#*=}"
      shift
      ;;
    --)
      shift
      ARGS+=("$@")
      break
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done

EXTRA_JAVA_OPTS=""
if [[ -n "$DIR_PROVIDER" ]]; then
  EXTRA_JAVA_OPTS="-Dclue.dir.provider=$DIR_PROVIDER"
fi

(cd $bin/..; java $JAVA_OPTS $JAVA_DEBUG $HEAP_OPTS $EXTRA_JAVA_OPTS -classpath "$lib/*" $MAIN_CLASS "${ARGS[@]}")
