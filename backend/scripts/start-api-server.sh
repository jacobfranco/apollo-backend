#!/bin/bash
# This script is used to start the Spring Boot application located in the /api directory.
# It sets the JVM argument -Xss2m for the running application.
#
# This will prevent from triggering a mysterious StackOverflow Exception on startup.  
# This originally happened on my really old machine, but it was still happening on 
# a newer machine as well.  Your results may vary.   
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

# Run mvn clean install, can probably be removed later but its useful for now
mvn clean install

# Navigate to the script's directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Navigate to the api directory from the script's directory
cd "$DIR"/../../api

# Start the Spring Boot application with the specified JVM arguments.
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xss2m"
