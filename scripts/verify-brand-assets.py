#!/usr/bin/env python3
"""Check that Edo's logo is wired to each native and browser entry point."""
from pathlib import Path
import json, re, struct
root=Path(__file__).resolve().parents[1]
def check_png(path,size=None):
    b=(root/path).read_bytes()
    assert b[:8]==b'\x89PNG\r\n\x1a\n',path
    dimensions=struct.unpack('>II',b[16:24]); assert size is None or dimensions==size,(path,dimensions)
    return dimensions
check_png('branding/edo-original-logo.png',(1254,1254))
check_png('app/src/main/res/drawable/banner.png',(640,360))
check_png('app/src/main/res/drawable-nodpi/edo_app_icon.png',(1024,1024))
check_png('app/src/main/res/drawable-nodpi/edo_brandmark.png',(144,144))
check_png('desktop/resources/edo-icon.png',(256,256))
assert (root/'desktop/resources/edo-icon.ico').read_bytes()[:4]==b'\0\0\1\0'
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'android:icon="@drawable/edo_app_icon"' in manifest
assert 'android:banner="@drawable/banner"' in manifest
main=(root/'app/src/main/java/com/dropwave/tv/MainActivity.java').read_text()
assert 'R.drawable.edo_brandmark' in main
html=(root/'app/src/main/assets/index.html').read_text()
assert 'class="brandmark"' in html and '<link rel="icon" type="image/png" href="data:image/png;base64,' in html
assert '<script>' in html and len(html.encode()) < 500_000
assert 'cp desktop/resources/edo-icon.png desktop/build/classes/edo-icon.png' in (root/'desktop/build.sh').read_text()
contents=json.loads((root/'ios/Edo/Assets.xcassets/AppIcon.appiconset/Contents.json').read_text())
assert len(contents['images']) >= 18
for image in contents['images']:
    check_png('ios/Edo/Assets.xcassets/AppIcon.appiconset/'+image['filename'])
assert 'ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon' in (root/'ios/project.yml').read_text()
print('PASS: Official Edo branding wired to Android TV, browser, desktop and iOS source')
