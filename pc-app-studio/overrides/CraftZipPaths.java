package com.pcappstudio.launcher;

import java.util.Locale;

/** Rejects archive traversal and names that cannot be used in Windows file paths. */
public final class CraftZipPaths {
    private CraftZipPaths() {}

    public static String safeRelative(String original) {
        if (original == null || original.isEmpty() || original.length() > 512) return null;
        if (original.startsWith("/") || original.startsWith("\\") || original.indexOf('\0') != -1) return null;
        String replaced = original.replace('\\', '/');
        if (replaced.matches("(?i)^[a-z]:.*") || replaced.startsWith("//")) return null;
        if (replaced.endsWith("/")) replaced = replaced.substring(0, replaced.length() - 1);
        if (replaced.isEmpty()) return null;
        String[] parts = replaced.split("/", -1);
        if (parts.length > 20) return null;
        for (String part : parts) {
            if (part.length() == 0 || part.length() > 128 || part.equals(".") || part.equals("..")) return null;
            if (part.matches(".*[<>:\"|?*\\p{Cntrl}].*") || part.endsWith(" ") || part.endsWith(".")) return null;
            String base = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return null;
        }
        return String.join("/", parts);
    }

    public static String parentPath(String safe) {
        int idx = safe.lastIndexOf('/');
        return idx < 0 ? "" : safe.substring(0, idx + 1);
    }

    public static String baseName(String safe) {
        int idx = safe.lastIndexOf('/');
        return idx < 0 ? safe : safe.substring(idx + 1);
    }

    public static String windowsFilePath(String batch, String safe) {
        return "D:\\PCAppStudio\\CraftAnything\\" + batch + "\\" + safe.replace('/', '\\');
    }

    public static boolean isExecutable(String safe) {
        return safe.toLowerCase(Locale.ROOT).endsWith(".exe");
    }

    public static int candidateScore(String safe) {
        if (!isExecutable(safe)) return -100;
        String name = baseName(safe).toLowerCase(Locale.ROOT);
        if (name.contains("uninstall") || name.contains("update") || name.contains("crash")) return -10;
        if (name.equals("craft-anything.exe")) return 100;
        if (name.startsWith("craft-anything")) return 80;
        if (name.contains("craft")) return 30;
        return 0;
    }
}
