#!/bin/bash
# This script is used to start the Spring Boot application located in the /api directory.
# It sets the JVM argument -Xss2m for the running application.
#
# This is only necessary on older machines, I think.  
#
# Instructions for Use:
# 1. Ensure this script is executable:
#    chmod +x start-api-server.sh
#
# 2. To run this script, navigate to the backend/scripts directory and execute:
#    ./start-api-server.sh
#
#    Alternatively, you can add an alias to your shell profile to run this script from anywhere
#

# Navigate to the script's directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Navigate to the api directory from the script's directory
cd "$DIR"/../../api

# Start the Spring Boot application with the specified JVM arguments.
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xss2m"
