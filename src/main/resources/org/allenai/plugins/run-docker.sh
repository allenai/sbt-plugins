#!/bin/bash

# Script to start a java process.
#
# This uses three environment variables to control how it operates:
# JAVA_MAIN (required) - The main class to execute.
# JVM_ARGS - Arguments to pass to `java`, before the class name. If unset, defaults to
#            "-Xms512m -Xmx512m".
# CONFIG_ENV - Controls which Typesafe config is used in the application. The file
#              conf/${CONFIG_ENV}.conf will be used if it exists; else, conf/application.conf will
#              be used.
#
# All arguments will be passed on to the underlying JVM application.
#
# In addition to behavior described above, this will:
# - set the system property logback.appname to "ai2service"
# - set the system property logback.configurationFile to conf/logback.xml, if that file exists
# - set the system property application.cacheKey to the contents of conf/cacheKey.sha1, if that
#   exists

# Require JAVA_MAIN.
if [ -z "$JAVA_MAIN" ]; then
  echo "You must have the JAVA_MAIN environment variable set."
  exit 1
fi

# Set default memory settings JVM_ARGS if undefined.
if [ -z "$JVM_ARGS" ]; then
  JVM_ARGS="-Xms512m -Xmx512m"
fi

# Change to the root of the install.
SCRIPT_DIR=`dirname $0`
cd "$SCRIPT_DIR/.."

# Configure JVM logging.
LOGBACK_CONF=("-Dlogback.appname=ai2service")
if [ -e conf/logback.xml ]; then
  LOGBACK_CONF+=("-Dlogback.configurationFile=conf/logback.xml")
fi
CONF_FILE="-Dconfig.file=conf/application.conf"
# Use a per-env config, if it exists.
if [ -e conf/$CONFIG_ENV.conf ]; then
  CONF_FILE="-Dconfig.file=conf/$CONFIG_ENV.conf"
fi

# Add a cache key define, if it exists.
CACHE_KEY=""
if [ -e conf/cacheKey.sha1 ]; then
  CACHE_KEY_CONTENTS=$(<conf/cacheKey.sha1)
  CACHE_KEY="-Dapplication.cacheKey=$CACHE_KEY_CONTENTS"
fi

# Generate the classpath & full command.
CLASSPATH=`find lib -name '*.jar' | tr "\\n" :`
JAVA_CMD=(
  java $JVM_ARGS -classpath "$CLASSPATH" "$CONF_FILE" "$CACHE_KEY" ${LOGBACK_CONF[@]} "$JAVA_MAIN"
)

echo Running "${JAVA_CMD[@]} $@" . . .
${JAVA_CMD[@]} "$@"
