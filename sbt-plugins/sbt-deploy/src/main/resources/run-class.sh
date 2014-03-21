#!/bin/bash

# Script to start a single server with the given main class.
# This assumes that we're working out of a universal install (lib/ directory
# present, and this script is in bin/).
#
# This takes three arguments:
# 1 - fully-qualified classname to run
# 2 - short name used to name output and PID files
# 3 - command, one of start|stop|restart

SCRIPT_NAME=`basename $0`
# Use a compact usage if we're being invoked as another script.
if [[ $SCRIPT_NAME == run-class.sh ]]; then
  USAGE="usage: $SCRIPT_NAME mainClass shortName [start|stop|restart]"
else
  USAGE="usage: $SCRIPT_NAME [start|stop|restart]"
fi

if [[ $# != 3 ]]; then
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

CLASSPATH=`find lib -name '*.jar' | tr "\\n" :`
# TODO(jkinkead): Don't always run with the same heap.
JAVA_CMD=(java -Xms256m -Xmx512m -classpath $CLASSPATH $CONF_FILE
  ${LOGBACK_CONF[@]})

# Run java.
echo "running in `pwd` ..."
echo "${JAVA_CMD[@]} ${MAIN_CLASS}"
nohup ${JAVA_CMD[@]} "$MAIN_CLASS" > "${SHORT_NAME}.out" 2> "${SHORT_NAME}.err" &
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
