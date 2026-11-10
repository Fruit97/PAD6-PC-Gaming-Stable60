package com.pcappstudio.launcher;

public final class CraftZipPathsTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check("craft-anything/craft-anything.exe".equals(CraftZipPaths.safeRelative("craft-anything/craft-anything.exe")), "basic archive");
        check(CraftZipPaths.safeRelative("../secret.exe") == null, "parent traversal");
        check(CraftZipPaths.safeRelative("/root/evil.exe") == null, "absolute path");
        check(CraftZipPaths.safeRelative("C:\\Windows\\evil.exe") == null, "drive");
        check(CraftZipPaths.safeRelative("folder/../evil.exe") == null, "nested traversal");
        check(CraftZipPaths.safeRelative("folder/CON.txt") == null, "Windows reserved");
        check(CraftZipPaths.safeRelative("folder/a?.exe") == null, "Windows wildcard");
        check(CraftZipPaths.safeRelative("folder\\tool.exe").equals("folder/tool.exe"), "Windows separators");
        check(CraftZipPaths.windowsFilePath("batch", "dir/craft-anything.exe").equals("D:\\PCAppStudio\\CraftAnything\\batch\\dir\\craft-anything.exe"), "Winlator path");
        check(CraftZipPaths.candidateScore("bin/craft-anything.exe") > CraftZipPaths.candidateScore("bin/uninstall.exe"), "main app first");
        System.out.println("CraftZipPaths tests passed (10 checks)");
    }
}
