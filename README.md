# Game Thai Translator — dedicated APK build branch

This branch contains a self-contained version of the Android source as
three Base64-encoded chunks of a Brotli-compressed tar archive. The
GitHub Actions workflow reconstructs the files, validates the SHA-256
checksum, compiles the Android app, and publishes the debug APK artifact.

The repository default branch and the original application are not changed.
