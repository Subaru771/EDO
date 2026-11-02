# Edo Desktop v1.0 — Windows

This version includes the redesigned Edo Desktop dashboard for PC-to-PC and phone-to-PC local file sharing.

## Start
1. Download `Edo-Desktop-v1.0-Windows.zip` from the [Edo Releases page](https://github.com/Subaru771/EDO/releases/tag/v5.4.2).
2. Extract the complete ZIP.
3. Install [Java 21 or newer](https://adoptium.net/temurin/releases/?version=21) if needed.
4. Double-click `Start-Edo-Windows.vbs` for silent launch, or use `Start-Edo-Windows.cmd` to diagnose launch errors.
5. Choose the folder to share and click **Start sharing**.
6. On your other PC or phone, visit the displayed Edo LAN address and pair.

The Windows ZIP is a Java desktop application. The separate portable `Edo.exe` preview bundles Java, if a successful build artifact is available under GitHub Actions. It is not part of the public ZIP unless explicitly included.

## Security
Use on trusted private LANs. HTTP is unencrypted. Don't forward Edo's server port to the internet.

## Versions
- Edo Desktop: **v1.0**.
- Android TV app remains **v5.4.2**.

[PC-to-PC guide](../docs/PC-TO-PC.md)
