#!/usr/bin/env bash

###############################################
# To generate, run this from the root directory
#
# cd backend
# ./scripts/genthrift.sh
###############################################

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

