#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
javac -d "$dir" app/src/main/java/com/dropwave/tv/{LibraryIndex,LanServer,ResumeStore,TransferJournal,TrustRegistry,WebDav,UdpDiscovery}.java desktop/src/com/dropwave/desktop/{DiskRepository,SyncClient,SyncTest}.java tests/{SmokeTest,AdvancedTest}.java
java -cp "$dir" SmokeTest
java -cp "$dir" AdvancedTest
java -cp "$dir" com.dropwave.desktop.SyncTest
