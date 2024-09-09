#!/bin/bash

# This script is used to start the Spring Boot application located in the /api directory.
#
# It sets the JVM arguments so that it can run more efficiently 
#
# This will prevent from triggering a mysterious StackOverflow Exception on startup.  
# This originally happened on my really old machine, but it was still happening on 
# a newer machine as well.  Your results may vary.   
#
# Also running this locally takes up a lot of CPU, your results may also vary.  
#
# It also gets rid of directories starting with ipc in the /tmp directory (again, your results may vary)
# This is so that the device doesn't run out of space.  There might be a be a better way to do this, but I'm 
# novice Linux user and felt like this was the easiest way.  
#
# Instructions for Use:
# 1. Ensure this script is executable:
#    chmod +x start-api-server.sh
#
# 2. To run this script, navigate to the backend/scripts directory and execute:
#    ./start-api-server.sh
#
#    Alternatively, you can add an alias to your shell profile to run this script from anywhere
#    For example, mine looks like this:
#
#    alias startapi='sh /home/jacob/Apollo/apollo-backend/backend/scripts/start-api-server.sh'
#
#    But running mvn spring-boot:run from the /api directory might just work out of the box
#    Check to see if this script is necessary for you first
#    Alternatively, if there is a better way to solve this problem, be my guest.  
#

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

# Run mvn clean install
echo "Running mvn clean install"
mvn clean install

# Navigate to the script's directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Navigate to the api directory from the script's directory
cd "$DIR"/../../api

# Start the Spring Boot application with optimized JVM arguments
echo "Starting Spring Boot application"
mvn spring-boot:run -Dspring-boot.run.jvmArguments="\
-Xss2m \
-Xmx2048m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-XX:+ParallelRefProcEnabled \
-XX:InitiatingHeapOccupancyPercent=45 \
-XX:G1ReservePercent=15 \
-XX:ReservedCodeCacheSize=256m \
-XX:ConcGCThreads=4 \
-XX:ParallelGCThreads=4 \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=$RAMA_TEMP \
-Djava.io.tmpdir=$RAMA_TEMP \
-Djava.security.egd=file:/dev/./urandom"