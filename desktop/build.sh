#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p desktop/build/classes desktop/dist
javac -encoding UTF-8 -d desktop/build/classes app/src/main/java/com/dropwave/tv/{LibraryIndex,LanServer,ResumeStore,TransferJournal,TrustRegistry,WebDav,UdpDiscovery}.java desktop/src/com/dropwave/desktop/{DiskRepository,DesktopMain,SyncClient,TwoWaySync,TlsDesktop}.java
cp app/src/main/assets/index.html desktop/build/classes/index.html
cp desktop/resources/edo-icon.png desktop/build/classes/edo-icon.png
printf 'Main-Class: com.dropwave.desktop.DesktopMain\n' > desktop/build/MANIFEST.MF
jar cfm desktop/dist/Edo-Desktop.jar desktop/build/MANIFEST.MF -C desktop/build/classes .
cp desktop/Edo-Windows.vbs desktop/dist/Edo-Windows.vbs
echo "Created desktop/dist/Edo-Desktop.jar"
