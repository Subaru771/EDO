# Edo sharing guide — choose your devices

| You have | Run Edo on | Connect from | First action |
|---|---|---|---|
| Two PCs | One PC, as a desktop server | The second PC's browser | Start sharing on PC A; visit its URL on PC B |
| PC + Google TV | TV app | PC browser | Open Edo on TV; scan QR or enter the TV's address |
| iPhone + Google TV | TV app | Safari | Scan QR or enter TV address, then pair |
| Android phone + PC | Desktop app on PC | Android browser | Visit PC's Edo URL and pair |
| Mac/Linux + Windows | Edo Desktop on the host running Java 21+ | Browser | Visit the host's LAN address and pair |

Both devices normally must be reachable on the same trusted LAN. The computer or TV running Edo must remain awake and online.

## Every transfer has two roles

- **Server/receiver:** The device running the Edo app (TV APK or Java Desktop). It shows the address and six-digit PIN and holds the shared folder.
- **Client:** Another device using its browser, with no installation. Clients can **upload to the server** and **download from the server**.

For the exact two-PC process, see [PC-TO-PC.md](PC-TO-PC.md). For encryption, see [SECURE-CONNECTION-GUIDE.md](../SECURE-CONNECTION-GUIDE.md).
