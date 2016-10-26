#!/bin/bash

# Script to start a single server with the given main class.
# This assumes that we're working out of a universal install (lib/ directory
# present, and this script is in bin/).
#
# This takes at least three arguments:
# 1 - fully-qualified classname to run
# 2 - short name used to name output and PID files
# 3 - command, one of start|stop|restart
#
# If arguments should be passed on to the main class at startup, they can be given as arguments to
# this script, separated from the three args above by "--".
#
# You can specify custom JVM arguments by setting the environment variable JVM_ARGS.

# Set default JVM_ARGS if undefined
if [ -z "${JVM_ARGS}" ]; then
  JVM_ARGS="-Xms512m -Xmx512m"
fi

SCRIPT_NAME=`basename $0`
# Use a compact usage if we're being invoked as another script.
if [[ $SCRIPT_NAME == run-class.sh ]]; then
  USAGE="usage: $SCRIPT_NAME mainClass shortName [start|stop|restart] [-- <args>]"
else
  USAGE="usage: $SCRIPT_NAME [start|stop|restart] [-- <args>]"
fi

if [[ $# < 3 ]]; then
  echo "$USAGE"
  exit 1
fi
MAIN_CLASS=$1
shift
SHORT_NAME=$1
shift
COMMAND=$1
shift

case $COMMAND in
start)
  START=1
  ;;
restart)
  STOP=1
  START=1
  ;;
stop)
  STOP=1
  ;;
*)
  echo "$USAGE"
  exit 1
esac

# Check if any additional args were passed to this script.
# If there are additional args, the first should be the separator "--". This separator will be
# stripped and all remaining args will be passed on to the main java class.
# If additional args were passed but not properly separated, print usage and exit with an error.
if [[ ! -z "$1" ]]; then
  if [[ "$1" != "--" ]]; then
    echo "$USAGE"
    exit 1
  fi
  shift
fi

# Change to the root of the install.
SCRIPT_DIR=`dirname $0`
cd "$SCRIPT_DIR/.."

PID_FILE="$SHORT_NAME.pid"

# Returns true if the service is running.
process_running () {
  [ -e "$PID_FILE" ] && ps -p `cat "$PID_FILE"` > /dev/null 2>&1
}

# Kill the service if we've been asked to and it's running.
if process_running; then
  # If it's running but we weren't asked to kill it, print an error and exit.
  if [[ ! $STOP ]]; then
    echo "service $SHORT_NAME still running, use 'stop' or 'restart'"
    exit 1
  fi
  echo "killing $SHORT_NAME, PID `cat "$PID_FILE"` . . ."
  kill `cat "$PID_FILE"`
  sleep 1
  while process_running; do
    echo "still waiting for $SHORT_NAME to die . . ."
    sleep 1
  done
  rm "$PID_FILE"
else
  if [[ $STOP ]]; then
    echo "No process to stop."
  fi
fi

# Exit early if we weren't asked to start.
if [[ ! $START ]]; then
  exit
fi

LOGBACK_CONF=("-Dlogback.appname=$SHORT_NAME")
if [ -e conf/logback.xml ]; then
  LOGBACK_CONF+=("-Dlogback.configurationFile=conf/logback.xml")
fi
CONF_FILE="-Dconfig.file=conf/application.conf"
# Use a per-env config, if it exists.
if [ -e conf/env.conf ]; then
  CONF_FILE="-Dconfig.file=conf/env.conf"
fi

#Add a cache-key config, user tests for existence
ADD_CACHE_KEY=""
if [ -e conf/cacheKey.Sha1 ]; then
  CACHEKEY=$(<conf/cacheKey.Sha1)
  ADD_CACHE_KEY="-Dapplication.cacheKey=$CACHEKEY"
fi

CLASSPATH=`find lib -name '*.jar' | tr "\\n" :`
JAVA_CMD=(java $JVM_ARGS -classpath $CLASSPATH $CONF_FILE
  ${LOGBACK_CONF[@]} $ADD_CACHE_KEY)

# Run java.
echo "running in `pwd` ..."
echo "${JAVA_CMD[@]} ${MAIN_CLASS} $@"
nohup ${JAVA_CMD[@]} "$MAIN_CLASS" "$@" > "${SHORT_NAME}.out" 2> "${SHORT_NAME}.err" &
EXIT_CODE=$?

sleep 2

ps -p $! > /dev/null
if [ $? -eq 0 ]; then
  echo $! > "$PID_FILE"
  echo "Process forked, pid: $!"
else
  echo "Error: ${SHORT_NAME} failed to start! See `pwd`/${SHORT_NAME}.{err,out} for info."
  exit 1
fi

exit $EXIT_CODE
