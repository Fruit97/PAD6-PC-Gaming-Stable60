# HOK Skin Finder 1.5
Offline Android file inventory scanner. Select already accessible/copied HOK OBB/data files or folders. The app searches names, file headers, and raw binary text for skin/hero/model/texture markers, including inside regular ZIP-based OBB/APK packages. It saves JSON findings. It never edits or decrypts game data or installs/modifies skins.
Build in GitHub Actions: workflow "Build HOK Skin Finder APK", artifact "HOK-Skin-Finder-v1.3-APK". Input file scanning is not proof that a candidate actually contains a skin. Unknown/proprietary game archives are reported as opaque. Android 14 protected folders must be copied out or made accessible through the system file picker first.

Use the Copy brief report button to paste scan findings directly into ChatGPT if uploading JSON fails. The smaller .txt export can also be attached from Downloads.

V1.5 filters out log and Pandora program false positives, reports Unity assetbundles, texture signatures, opaque containers and the largest unrecognized files for further analysis. No proof of HOK skin mapping is implied.
