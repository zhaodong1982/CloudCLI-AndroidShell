# Local Android shell acceptance

## Prerequisites

- CloudCLI runit service is healthy at `http://127.0.0.1:3001/`.
- Android System WebView is installed and enabled.
- `output/CloudCLI-Shell.apk` has passed `./verify.sh`.

## Installation and identity

1. Install the APK with package manager upgrade semantics.
2. Read package metadata and installed APK path.
3. Hash the built and installed APK artifacts.

Expected: package `local.cloudcli.shell`, version `1.0.1`/code `2`, matching
signer, and a launchable `MainActivity`. Package-manager rewriting may change the
container APK hash, so signer and package metadata remain authoritative.

## Local-only navigation

1. Launch CloudCLI from its icon.
2. Confirm the registration or login page renders.
3. Tap the native refresh button.
4. Tap an external HTTPS link if one is available.

Expected: CloudCLI stays inside the Shell, refresh does not expose browser
history, external links show `本机模式已阻止外部链接` without launching Chrome or
another app, and no editable server address is shown.

## Offline recovery

1. Stop only the `cloudcli` runit service.
2. Return to the Shell or tap refresh.
3. Start the service and tap `重新连接`.

Expected: a native offline card names `sv up cloudcli`; the app reconnects
without clearing cookies, account state, or drafts.

Cleanup: leave the runit service running. Do not clear app data unless explicitly
testing a clean local login.

## Theme and layout

1. Exercise the registration/login page and main view in portrait and landscape.
2. Verify system bars, keyboard resize, and the native 40dp header.
3. If the CloudCLI account exposes theme controls, check both light and dark UI.

Expected: no light native surfaces on the midnight header/background, no clipped
form controls, and no content hidden behind the keyboard or system bars.

## Launcher icon

1. Inspect the icon on Pixel Launcher beside common first-party app icons.
2. Verify the normal circular mask and the themed monochrome variant.
3. Confirm the symbol remains legible at the normal launcher grid size.

Expected: a flat blue circle with a centered white cloud and crisp blue `>_`;
no neon halo, glass texture, inner tile, clipped cloud, or fine detail.

## Agent acceptance

1. Complete CloudCLI local account setup.
2. Start one Claude turn and one Codex turn from the Shell.
3. Reload the app and confirm both completed results remain visible.

Expected: each provider returns a real completed response and persistence survives
process recreation. This step remains pending until the owner completes local
account registration.
