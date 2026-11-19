#!/bin/bash
# Compiles and runs Ventria, the ECG anomaly detection web app.
#   ./run.sh              -> web app on http://localhost:8080
#   ./run.sh 9090         -> web app on another port
#   ./run.sh mock [dir]   -> (re)generate the mock ECG CSV files (default ../Ventria_Mock_ECG_Data)
cd "$(dirname "$0")"

# Find a JDK (uses JAVA_HOME, then macOS java_home, then the installed AdoptOpenJDK 16)
if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
fi
if [ -z "$JAVA_HOME" ] && [ -d /Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk ]; then
  JAVA_HOME=/Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk/Contents/Home
fi
JAVA_BIN=${JAVA_HOME:+$JAVA_HOME/bin/}

rm -rf out
"${JAVA_BIN}javac" -d out $(find src -name "*.java") || exit 1

if [ "$1" == "mock" ]; then
  "${JAVA_BIN}java" -cp out ecg.tools.MockDataGenerator ${2:+"$2"}
else
  "${JAVA_BIN}java" -cp out ecg.web.WebServer "${1:-8080}"
fi
