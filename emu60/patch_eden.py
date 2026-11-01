#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Pad6 Emu60: scoped rebranding of an actual Eden ARM64 emulator.
No keys, firmware, games or copyrighted game assets are supplied.
"""
import pathlib
import sys

def checked(s, old, new, label):
    if s.count(old) != 1:
        raise RuntimeError(f"{label}: expected exactly one upstream anchor; found {s.count(old)}")
    return s.replace(old, new, 1)

def main(root):
    gradle = root/"src/android/app/build.gradle.kts"
    conf = root/"src/common/settings.h"
    b, c = gradle.read_text(), conf.read_text()
    b = checked(b, 'applicationId = "dev.eden.eden_emulator"', 'applicationId = "com.pad6.emu60"', "main package")
    b = checked(b, 'applicationId = "dev.legacy.eden_emulator"', 'applicationId = "com.pad6.emu60.legacy"', "legacy package")
    mainline = '''create("mainline") {
            dimension = "version"
            isDefault = true
            minSdk = 33

            manifestPlaceholders += mapOf("appNameBase" to "Eden")
            resValue("string", "app_name_suffixed", "Eden")'''
    legacy = '''create("legacy") {
            dimension = "version"
            minSdk = 29
            manifestPlaceholders += mapOf("appNameBase" to "Eden Legacy")
            resValue("string", "app_name_suffixed", "Eden Legacy")'''
    b = checked(b, mainline, mainline.replace('"Eden"', '"Pad6 Emu60"'), "main label")
    b = checked(b, legacy, legacy.replace('"Eden Legacy"', '"Pad6 Emu60 Legacy"'), "legacy label")
    c = checked(c,
      '    SwitchableSetting<ResolutionSetup> resolution_setup{linkage, ResolutionSetup::Res1X,\n',
      '''#if defined(__ANDROID__)
    SwitchableSetting<ResolutionSetup> resolution_setup{linkage, ResolutionSetup::Res3_4X,
#else
    SwitchableSetting<ResolutionSetup> resolution_setup{linkage, ResolutionSetup::Res1X,
#endif
''', 'Android 0.75x')
    # All anchors verified before writing either source.
    gradle.write_text(b)
    conf.write_text(c)
    print("Pad6 Emu60 Android source patched: ARM64, real Eden core, 0.75x default.")
    print("No promise of 60 FPS in every game.")
if __name__ == "__main__":
    main(pathlib.Path(sys.argv[1]).resolve())
