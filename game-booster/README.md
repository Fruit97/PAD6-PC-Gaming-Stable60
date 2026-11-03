# Pad 6 Game Booster v1.0.1
Thai-language launcher and device-status helper for Xiaomi Pad 6 (Android 14). No root, no external APIs, no ads. Can browse and launch installed games, display RAM and battery health, display thermal status, open screen/battery/Xiaomi Security settings, and optionally switch on Do Not Disturb for a play session if the user grants the policy access. Returning to the launcher normally restores DND (subject to app process survival).

**Limitations:** Does not unlock 90/120 FPS, alter GPU frequencies, measure in-game FPS, kill other apps or override thermal limits. A cooler device, supported display mode and appropriate in-game graphics settings may reduce stutter, but improvements are not guaranteed.

Android debug APK is signed by the GitHub runner's debug key and can be sideloaded. To build: Actions -> Build Pad6 Game Booster APK -> latest successful run -> Artifacts -> Pad6-Game-Booster-APK. The ZIP artifact contains app-debug.apk. Requires installing unknown apps permission for the file manager. First launch: select an installed game and press start.

Source is separate from the Switch/PC emulator modules within the same GitHub repository.
