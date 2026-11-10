package com.dropwave.tv;

/**
 * The one source of truth for EDO's public download locations.
 * The old DropWave directory is read only for migration and legacy access.
 * Package IDs and preference names must stay unchanged for upgrade compatibility.
 */
public final class EdoDownloadPaths {
    private EdoDownloadPaths() {}

    public static String root(String downloadsDirectory) {
        return downloadsDirectory + "/Edo/";
    }

    public static String legacyRoot(String downloadsDirectory) {
        return downloadsDirectory + "/DropWave/";
    }

    public static String destination(String downloadsDirectory, String subfolder) {
        return root(downloadsDirectory) + (subfolder == null || subfolder.isEmpty() ? "" : subfolder + "/");
    }

    /** MediaStore's returned RELATIVE_PATH must match the intended storage folder. */
    public static boolean matches(String actual, String intended) {
        return actual != null && actual.replace('\\', '/').equals(intended);
    }
}
