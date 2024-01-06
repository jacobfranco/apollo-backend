#!/usr/bin/env bash
set -eo pipefail

hash thrift 2> /dev/null ||
    {
        printf 'thrift not found.  Install with `brew install thrift`\n';
        exit 1
    }

# Remove existing generated files
rm -rf src/main/java/com/apollo/backend/data

# Generate new Thrift files
thrift --out src/main/java --gen java:generated_annotations=suppress src/apollo.thrift

# Post-process generated files to modify @SuppressWarnings, workaround hopefully doesn't lead to issues later
find src/main/java/com/apollo/backend/data -type f -name '*.java' | while read file; do
    sed -i '' -e 's/@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})/@SuppressWarnings({"rawtypes", "unchecked", "unused"})/g' "$file"
done
