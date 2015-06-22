#!/bin/bash
set -x
if [[ $# -lt 2 ]]; then
    echo "Usage: $0 <filename for recording> <path to pom of project to run missinglink on>"
    exit 1
fi

RECORDING_FILENAME=$1
POM_PATH=$2

if [[ ! -e $POM_PATH ]]; then
    echo "Error: $POM_PATH does not exist!"
    exit 2
fi

PLUGIN=com.spotify:missinglink-maven-plugin:0.1.1-SNAPSHOT
# use a modified "profile" template for flight recorder, the default won't have some stuff enabled
DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
JFR_SETTINGS=$DIR/profile.jfc

# launch maven
JFR_OPTS="-XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=$RECORDING_FILENAME,settings=$JFR_SETTINGS"
MAVEN_OPTS=$JFR_OPTS mvn $PLUGIN:check -f "$POM_PATH"
