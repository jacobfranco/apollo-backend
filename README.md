# Apollo Project

## Overview
This project contains various scripts to help with development tasks such as generating Thrift files and starting the Spring Boot application.

---

## Generate Thrift Files

If any Thrift objects are modified, you need to regenerate the Thrift files. To do this, run the following command from the root directory:

```bash
cd backend
./scripts/genthrift.sh
```

---

## Start the Spring Boot Application

This script is used to start the Spring Boot application located in the `/api` directory with optimized JVM arguments. It also performs some cleanup tasks before starting the application.

### Instructions for Use

1. Ensure the script is executable:
    ```bash
    chmod +x start-api-server.sh
    ```

2. To run this script, navigate to the `backend/scripts` directory and execute:
    ```bash
    ./start-api-server.sh
    ```

    Alternatively, you can add an alias to your shell profile to run this script from anywhere. For example:
    ```bash
    alias startapi='sh /home/jacob/Apollo/apollo-backend/backend/scripts/start-api-server.sh'
    ```

    > **Note:** Running `mvn spring-boot:run` from the `/api` directory might work out of the box. Check to see if this script is necessary for you first.

### JVM Arguments

The script sets the following JVM arguments to optimize performance and prevent issues like StackOverflowException:
```bash
-Xss2m
-Xmx2048m
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+ParallelRefProcEnabled
-XX:InitiatingHeapOccupancyPercent=45
-XX:G1ReservePercent=15
-XX:ReservedCodeCacheSize=256m
-XX:ConcGCThreads=4
-XX:ParallelGCThreads=4
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp
-Djava.security.egd=file:/dev/./urandom
```

### Cleanup

Before starting the application, the script performs the following cleanup tasks:
- Removes any directories starting with `ipc` in the `/tmp` directory to free up space.
- Runs `mvn clean install` to ensure dependencies are up-to-date (this step may be removed in the future).

---

## Additional Notes

- Running this application locally might consume a lot of CPU. Your results may vary.
- If there is a better way to solve the problems addressed by this script, contributions are welcome!
