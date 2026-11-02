# Edo Desktop v1.0 — Windows

## Java desktop ZIP

Download the **Edo Desktop v1.0** ZIP from [GitHub Releases](https://github.com/Subaru771/EDO/releases). Extract the entire archive and install [Java 21+](https://adoptium.net/temurin/releases/?version=21) if necessary.

Open `Start-Edo-Windows.vbs` (no console), or `Start-Edo-Windows.cmd` (shows startup diagnostics).

## Portable Edo.exe

The [Windows build workflow](https://github.com/Subaru771/EDO/actions/workflows/desktop-windows.yml) builds `Edo-Desktop-v1.0-Windows-Portable.zip` using `jpackage` with Java included. Extract the **whole** ZIP and run `Edo/Edo.exe`. The portable build is unsigned and should be tested before public distribution; don't disable antivirus to launch it.

## Share between PCs

On the host PC, choose the shared folder and start sharing. On another computer, open the displayed address in a browser and pair. Upload using Transfer; download through Files. Use trusted LANs: HTTP is not encrypted.

See [PC-to-PC instructions](PC-TO-PC.md).
