#!/bin/sh
if [ $# -lt 1 ]; then
    echo "Usage: execute-maven-plugin.sh <path to project to test>"
    exit 1
fi
PROJECT=$1
shift
mvn -e -Dverbose com.spotify:missinglink-maven-plugin:0.1-SNAPSHOT:check-conflicts -f "$PROJECT/pom.xml" "$@"

