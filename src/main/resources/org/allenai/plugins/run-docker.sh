#!/bin/bash

# Script to start a java process.
#
# This uses three environment variables to control how it operates:
# JAVA_MAIN (required) - The main class to execute.
# JVM_ARGS - Arguments to pass to `java`, before the class name. If unset, defaults to
#            "-Xms512m -Xmx512m".
# CONFIG_ENV - Controls which Typesafe config is used in the application. The file
#              conf/${CONFIG_ENV}.conf will be used if it exists; else, conf/application.conf will
#              be used if it exists.
#
# All arguments will be passed on to the underlying JVM application.
#
# In addition to behavior described above, this will:
# - set the system property logback.configurationFile to conf/logback.xml, if that file exists
# - set the system property application.cacheKey to the contents of conf/cacheKey.sha1, if that
#   file exists

# Require JAVA_MAIN.
if [ -z "$JAVA_MAIN" ]; then
  echo "You must have the JAVA_MAIN environment variable set."
  exit 1
fi

JAVA_CMD=(java)

# Set default memory settings JVM_ARGS if undefined.
if [ -z "$JVM_ARGS" ]; then
  JVM_ARGS="-Xms512m -Xmx512m"
fi

JAVA_CMD+=($JVM_ARGS)

# Change to the root of the install.
SCRIPT_DIR=`dirname $0`
cd "$SCRIPT_DIR/.."

# Configure JVM logging.
if [ -e conf/logback.xml ]; then
  JAVA_CMD+=("-Dlogback.configurationFile=conf/logback.xml")
fi
# Use a per-env config, if it exists.
if [ -e conf/$CONFIG_ENV.conf ]; then
  JAVA_CMD+=("-Dconfig.file=conf/$CONFIG_ENV.conf")
elif [ -e conf/application.conf ]; then
  # Use the default application.conf, if it exists.
  JAVA_CMD+=("-Dconfig.file=conf/application.conf")
fi

# Add a cache key define, if it exists.
if [ -e conf/cacheKey.sha1 ]; then
  CACHE_KEY_CONTENTS=$(<conf/cacheKey.sha1)
  JAVA_CMD+=("-Dapplication.cacheKey=$CACHE_KEY_CONTENTS")
fi

# Generate the classpath.
CLASSPATH=`find lib -name '*.jar' | tr "\\n" :`
JAVA_CMD+=(-classpath "$CLASSPATH")

# Add the main class as the last argument.
JAVA_CMD+=("$JAVA_MAIN")

echo Running "${JAVA_CMD[@]} $@" ...
exec ${JAVA_CMD[@]} $@
