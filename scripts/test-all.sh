#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
javac --release 17 -encoding UTF-8 -d "$dir" \
  app/src/main/java/com/dropwave/tv/{LibraryIndex,LanServer,ResumeStore,TransferJournal,TrustRegistry,WebDav,UdpDiscovery,PeerClient}.java \
  desktop/src/com/dropwave/desktop/{DiskRepository,SyncClient,TwoWaySync,TlsDesktop,SyncTest,TwoWaySyncTest,SecureDavTest,ConnectionSecurityTest}.java \
  tests/{SmokeTest,AdvancedTest,PeerClientTest,RemoteControlTest,UdpDiscoveryTest,LibraryIndexTest,LegacyPeerClientTest}.java
java -cp "$dir" SmokeTest
java -cp "$dir" AdvancedTest
java -cp "$dir" com.dropwave.desktop.SyncTest
java -cp "$dir" com.dropwave.desktop.TwoWaySyncTest
java -cp "$dir" PeerClientTest
java -cp "$dir" RemoteControlTest
java -cp "$dir" UdpDiscoveryTest
java -cp "$dir" LibraryIndexTest
java -cp "$dir" LegacyPeerClientTest
java -cp "$dir" com.dropwave.desktop.SecureDavTest
java -cp "$dir" com.dropwave.desktop.ConnectionSecurityTest
if command -v node >/dev/null; then
  python3 - "$dir" <<'PY'
from pathlib import Path
import sys
s=Path('app/src/main/assets/index.html').read_text()
Path(sys.argv[1]+'/browser.js').write_text(s.split('<script>',1)[1].rsplit('</script>',1)[0])
PY
  node --check "$dir/browser.js"
  echo 'PASS: Web UI JavaScript syntax'
fi
if command -v swiftc >/dev/null; then
  swiftc -frontend -parse ios/Edo/*.swift ios/EdoShare/*.swift
  echo 'PASS: iOS Swift parser (NOT iOS compilation)'
fi
python3 - <<'PY'
import xml.etree.ElementTree as ET
import plistlib
ET.parse('app/src/main/AndroidManifest.xml')
ET.parse('app/src/main/res/values/strings.xml')
for plist in ('ios/Edo/Info.plist','ios/EdoShare/Info.plist','ios/Edo/Edo.entitlements','ios/EdoShare/EdoShare.entitlements'):
    with open(plist,'rb') as f:plistlib.load(f)
print('PASS: Android XML and iOS plist syntax')
PY

python3 scripts/verify-brand-assets.py
