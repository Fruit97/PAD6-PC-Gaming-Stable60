#!/usr/bin/env python3
"""Apply the non-destructive Craft Anything feature overlay to the v0.2 source ZIP."""
from pathlib import Path

root = Path(__file__).resolve().parent
app = root.parent / "build" / "app"
target = app / "src" / "main" / "java" / "com" / "pcappstudio" / "launcher"
target.mkdir(parents=True, exist_ok=True)
for java in ("CraftZipPaths.java", "CraftAnythingActivity.java"):
    (target / java).write_bytes((root / java).read_bytes())

activity = target / "MainActivity.java"
s = activity.read_text(encoding="utf-8")
s = s.replace("        b.setTextAllCaps(false);\n", "")
anchor = 'root.addView(text("เตรียมไฟล์ Windows บน Android แล้วเปิดผ่าน Winlator", 14, MUTED, false), lp(5));'
if anchor not in s:
    raise SystemExit("Cannot locate main activity layout anchor")
s = s.replace(anchor, anchor + '\n        root.addView(button("โหมด Craft Anything • Windows Portable ZIP", true, v -> startActivity(new Intent(this, CraftAnythingActivity.class))), lp(15));')
activity.write_text(s, encoding="utf-8")

manifest = app / "src" / "main" / "AndroidManifest.xml"
s = manifest.read_text(encoding="utf-8")
needle = '        <activity\n            android:name=".MainActivity"'
if needle not in s:
    raise SystemExit("Cannot locate main manifest activity")
s = s.replace(needle, '        <activity android:name=".CraftAnythingActivity" android:exported="false" />\n' + needle, 1)
manifest.write_text(s, encoding="utf-8")

gradle = app / "build.gradle"
s = gradle.read_text(encoding="utf-8")
s = s.replace("versionCode 2", "versionCode 3").replace("versionName '0.2.0'", "versionName '0.3.0'")
gradle.write_text(s, encoding="utf-8")
print("Craft Anything v0.3 overlay applied.")
