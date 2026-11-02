# Capture genuine Google TV screenshots

Google Play TV screenshot(s) must show the actual current Android TV app interface. Do not use the mock browser preview pictures as TV screenshots.

After installing Edo on a TV and enabling Android Debug Bridge, connect using the TV's IP address:

```
adb connect <TV_IP>:5555
adb shell screencap -p /sdcard/Download/edo-tv-screen.png
adb pull /sdcard/Download/edo-tv-screen.png
```

Use the original 16:9 PNG with the actual app content. Do not render fake UI or add decorative overlays. Ensure the TV screen does not expose a real PIN that remains usable after capture, or revoke the pairing code before sharing where feasible.

Supply the real screenshot under Google Play Console → Store presence → Main store listing → Android TV screenshots.
