#!/bin/bash

# HINT: Make an alias for running this
# alias api='sh /path/to/Apollo/apollo-backend/backend/scripts/start-api.sh dev'

# Create Rama temp directory in /tmp
RAMA_TEMP="/tmp/rama_temp"
if [ ! -d "$RAMA_TEMP" ]; then
    echo "Creating Rama temp directory at $RAMA_TEMP"
    mkdir -p "$RAMA_TEMP"
fi

# Clean up old Rama temp files
echo "Cleaning up old Rama temp files"
find "$RAMA_TEMP" -type f -mtime +1 -delete

# Remove any directories starting with ipc in the Rama temp directory
echo "Removing ipc* directories from Rama temp"
rm -rf "$RAMA_TEMP"/ipc*

# Default to dev if no environment specified
ENVIRONMENT=${1:-dev}

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
API_DIR="$SCRIPT_DIR/../../api"
cd "$API_DIR" || { echo "Failed to change to API directory"; exit 1; }

mvn clean install
mvn spring-boot:run -Dspring.profiles.active=$ENVIRONMENT -Dspring-boot.run.jvmArguments="\
-Xss4m \
-Xmx12g \
-XX:+UseG1GC \
-XX:ReservedCodeCacheSize=256m \
-XX:HeapDumpPath=$RAMA_TEMP \
-Djava.io.tmpdir=$RAMA_TEMP \
-XX:+HeapDumpOnOutOfMemoryError"