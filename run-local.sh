#!/usr/bin/env sh
set -eu

mkdir -p build/classes build/test-classes
javac -d build/classes $(find src/main/java -name '*.java')
javac -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
java -cp build/classes:build/test-classes dev.infrai.edtech.SnapshotPolicyTest

if [ "${1:-}" = "snapshot" ]; then
  java -cp build/classes dev.infrai.edtech.NightlySnapshotRunner sample/deliveries.csv 2026-08-17
fi
