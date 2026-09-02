# Build and release guide

## Toolchain

The project intentionally avoids Gradle. The build requires:

- a POSIX-compatible shell;
- JDK tools: `javac`, `jar`, `keytool`;
- Android tools: `aapt2`, `d8`, `apksigner`;
- Android API 35 `android.jar`.

On Termux, place `android.jar` at the default path documented in the README or
set `ANDROID_JAR` explicitly.

## Local build

```sh
./build.sh
./verify.sh
sha256sum output/CloudCLI-Shell.apk
```

The build performs resource compilation, Java compilation, DEX generation, APK
linking and APK signing. `verify.sh` then checks the signature, application ID,
version, permission count, network policy and WebView restrictions.

## Signing

By default the signing files live under `$XDG_DATA_HOME/cloudcli-shell/`, or
`$HOME/.local/share/cloudcli-shell/` when `XDG_DATA_HOME` is unset. Override the
directory with `CLOUDCLI_SHELL_SIGNING_DIR`.

The keystore and password file are intentionally ignored by Git and must never
be attached to a release. Losing the keystore prevents future builds from
upgrading installations signed by that key.

## Release checklist

1. Update versionCode/versionName together in `build.sh` and `verify.sh`.
2. Run `./build.sh` and `./verify.sh`.
3. Install with upgrade semantics and verify package metadata.
4. Compare the built APK hash with the installed base APK where supported.
5. Launch from Pixel Launcher and verify the real adaptive-icon crop.
6. Confirm `http://127.0.0.1:3001/health` and the main CloudCLI page.
7. Scan tracked files for secrets before pushing.
8. Attach only the verified APK to the matching GitHub release.

