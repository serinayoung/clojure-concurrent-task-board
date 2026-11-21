#!/usr/bin/env bash
# Runs the Concurrent Task Board on Linux.
# Needs: JDK 11+ and Leiningen (check with: java -version, lein version)
# Usage:  ./run.sh

set -e                       # stop if any command fails
cd "$(dirname "$0")"         # run from the project folder

echo "=== Concurrent Task Board ==="
echo "Java : $(java -version 2>&1 | head -n 1)"
echo "Lein : $(lein version 2>/dev/null)"
echo

lein run
