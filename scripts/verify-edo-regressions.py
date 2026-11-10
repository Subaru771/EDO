#!/usr/bin/env python3
"""Prevent accidentally reintroducing old storage paths or dropping Codex fixes."""
from pathlib import Path

def load(filename):
    return Path(filename).read_text(encoding="utf-8")

android = load("app/src/main/java/com/dropwave/tv/AndroidFiles.java")
paths = load("app/src/main/java/com/dropwave/tv/EdoDownloadPaths.java")
build = load("app/build.gradle")
sync = load("desktop/src/com/dropwave/desktop/SyncClient.java")
sync_test = load("desktop/src/com/dropwave/desktop/SyncTest.java")
dav = load("app/src/main/java/com/dropwave/tv/WebDav.java")
dav_test = load("desktop/src/com/dropwave/desktop/SecureDavTest.java")
tv = load("app/src/main/java/com/dropwave/tv/MainActivity.java")
desktop = load("desktop/src/com/dropwave/desktop/DesktopMain.java")

assert 'return downloadsDirectory + "/Edo/";' in paths, "New downloads must target Edo"
assert 'return downloadsDirectory + "/DropWave/";' in paths, "Legacy reads must remain possible"
assert 'EdoDownloadPaths.root(Environment.DIRECTORY_DOWNLOADS)' in android
assert 'EdoDownloadPaths.legacyRoot(Environment.DIRECTORY_DOWNLOADS)' in android
assert android.count('EdoDownloadPaths.destination(Environment.DIRECTORY_DOWNLOADS,folder)') == 3, "All upload/move paths must use Edo"
assert android.count('verifyEdoPath(uri,intended)') == 3, "Every save/move must verify actual location"
assert 'resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)' in android
assert 'public int migrateLegacyDownloads()' in android, "Keep legacy migration"
assert 'applicationId \'com.dropwave.tv\'' in build, "Keep upgrade-compatible Android application ID"
assert 'getSharedPreferences("dropwave",0)' in android, "Keep original preferences"
assert '.limit(2000)' not in sync and 'Files.walk(root,12)' in sync
assert 'i<2001' in sync_test and '2001' in sync_test
assert 'repo.moveTo(entry.id,parent(dest),leaf(dest))' in dav
assert 'Old-name collision remains unchanged' in dav_test, "Keep DAV collision regression"
assert 'actionPair(' in tv and 'liteMode' in tv, "Preserve compact native TV UI"
assert 'class DesktopMain' in desktop, "Preserve existing desktop implementation"
print("PASS: Edo storage, Codex fixes, and compact TV/desktop source invariants")
