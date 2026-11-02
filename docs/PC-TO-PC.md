# Transfer files from one PC to another with Edo

Run **Edo Desktop v1.0** on one Windows, macOS or Linux computer with **Java 21+**, and connect from a browser on the second computer.

## Fastest method: only one PC installs Edo

### On Computer A (receiving / sharing PC)
1. Both PCs should be on the same trusted LAN (Wi-Fi or Ethernet). Disable guest Wi-Fi isolation if it blocks device-to-device connections.
2. Download and extract [Edo Desktop v1.0](https://github.com/Subaru771/EDO/releases). With Java 21+ installed, on Windows open `Start-Edo-Windows.vbs` (or `Start-Edo-Windows.cmd` for diagnostics). On macOS/Linux run `java -jar Edo-Desktop.jar`.
3. Choose a folder you want to share. **Only share files you're comfortable letting paired devices access.**
4. Click **Start sharing**. Copy the complete local address printed in Edo (for example, `http://192.168.1.20:8765`). The address varies per network.
5. Find the six-digit pairing PIN in Edo Desktop. If device approval is enabled, keep this window available.

### On Computer B (using a browser)
1. In Chrome, Brave, Edge, Firefox or another compatible modern browser, visit the address shown on Computer A. You do not need to install Edo here.
2. Enter Computer A's PIN. Approve the request on Computer A if prompted.
3. **B → A:** Open **Transfer**, select or drag files. They are saved to Computer A's shared folder.
4. **A → B:** Open **Files** and download files from Computer A's shared folder to Computer B.
5. For moving an entire folder, use **Choose a folder** in Transfer when supported by your browser, or select files and use ZIP export for a grouped download.

**Important distinction:** Computer A runs the transfer *server* and manages the shared folder. Computer B uses its *browser* to upload to A or download from A. Transfer data moves directly over the LAN, not through GitHub or a public Edo website.

## If you want the same experience on both PCs
Install Edo Desktop on **both** computers. Each can host its own shared folder, and each can browse the other computer's address. Edo's desktop **Back up now**, **Receive from TV** (legacy label: also works with PC hosts), and **Safe two-way merge** tools are more advanced options. The merge keeps conflicting versions rather than overwriting or deleting files automatically.

## Common problems

- **Page does not open:** Confirm Computer A shows **ONLINE**, use its exact address (including `http://` or `https://` and port), check both devices are on the same LAN, allow Edo/Java through the OS firewall on **private/trusted** networks, and check Wi-Fi guest isolation.
- **Pairing fails:** Use the PIN from Computer A, approve the request there if required, and try a fresh browser tab.
- **HTTPS certificate warning:** Edo currently uses a locally generated certificate that some browsers reject. Verify its fingerprint rather than blindly dismissing security warnings. See [SECURITY.md](../SECURITY.md).
- **Wrong download direction:** Upload from the browser PC to the server PC; download in the browser to copy from server PC to browser PC.
- **Browser upload was interrupted:** Reselect the original local files and resume where supported.

### Example

`PC A (Edo Desktop / server) ← Wi-Fi/Ethernet → PC B (browser)`

Files stay on the local network unless you deliberately share or back them up elsewhere.

## Manage your files on the host PC

Edo Desktop **v1.0** includes a **Your files** sidebar tab. Use it to browse and search the shared folder, import files, create folders, rename, copy/move and delete items. These are local file-management actions on the host PC. Deleted items may not be recoverable.
