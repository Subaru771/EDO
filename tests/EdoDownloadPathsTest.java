package com.dropwave.tv;

/** Plain-Java check, run on every pull request without an Android emulator. */
public final class EdoDownloadPathsTest {
    private static void require(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        String downloads = "Download";
        require(EdoDownloadPaths.root(downloads).equals("Download/Edo/"), "New download root must be Edo");
        require(EdoDownloadPaths.destination(downloads, "").equals("Download/Edo/"), "Root upload");
        require(EdoDownloadPaths.destination(downloads, "Photos/2026").equals("Download/Edo/Photos/2026/"), "Nested upload");
        require(EdoDownloadPaths.legacyRoot(downloads).equals("Download/DropWave/"), "Legacy read path only");
        require(!EdoDownloadPaths.root(downloads).equals(EdoDownloadPaths.legacyRoot(downloads)), "Do not write to legacy root");
        require(EdoDownloadPaths.matches("Download/Edo/Photos/", EdoDownloadPaths.destination(downloads, "Photos")), "Expected actual path");
        require(!EdoDownloadPaths.matches("Download/DropWave/Photos/", EdoDownloadPaths.destination(downloads, "Photos")), "Reject silently storing in old folder");
        require(!EdoDownloadPaths.matches(null, EdoDownloadPaths.root(downloads)), "Reject an unverified destination");
        System.out.println("PASS: All new downloads target Edo, legacy path is distinct, unexpected storage path rejected");
    }
}
