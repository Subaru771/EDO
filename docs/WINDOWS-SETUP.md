# Edo Desktop for Windows

## Portable Windows Edo.exe (preview)

Open the [Windows build workflow](https://github.com/Subaru771/EDO/actions/workflows/desktop-windows.yml), select a successful build and download the `Edo-Desktop-v1.0-Portable` artifact.

Extract `Edo-Desktop-v1.0-Windows-Portable.zip` completely. Launch **Edo/Edo.exe**. Keep the whole Edo folder with its bundled Java runtime, rather than moving only the executable.

This build is an **unsigned test artifact** until it has been verified on a Windows PC and intentionally published in a new Release.

## Existing public Java ZIP

[Download Edo Desktop v1.0](https://github.com/Subaru771/EDO/releases/download/v5.4.2/Edo-Desktop-v1.0-Windows.zip). Extract it, install Java 21+, then open `Start-Edo-Windows.vbs` or use the `.cmd` launcher to see errors.

## PC-to-PC transfers

On PC A, run Edo, choose a folder and start sharing. On PC B, open the displayed URL in a browser, enter the PIN and approve on PC A if prompted. Use Transfer to upload and Files to download. Share only on trusted local networks; HTTP is not encrypted.