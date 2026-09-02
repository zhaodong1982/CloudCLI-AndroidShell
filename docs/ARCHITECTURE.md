# Architecture

## Runtime flow

```text
Android launcher
    -> MainActivity
        -> hardened Android WebView
            -> http://127.0.0.1:3001
                -> CloudCLI service in Termux
                    -> locally configured Agent providers
```

The APK contains no CloudCLI server and no provider credentials. It is a focused
presentation and device-integration layer for an already running local service.

## Main components

- `MainActivity` creates the native header, configures WebView, handles recovery,
  file selection, camera capture, microphone permission and local downloads.
- `DeviceProfileStore` retains only the fixed loopback profile and rejects
  alternative origins.
- `GestureSafeWebView` prevents horizontal browser-history gestures from
  interfering with the CloudCLI interface.
- `LocalFileProvider` exposes only temporary camera captures through a private,
  non-exported content provider.
- `network_security_config.xml` permits cleartext HTTP only for `127.0.0.1`.

## Data ownership

The shell stores only its WebView state and fixed local profile metadata. CloudCLI
projects, chats, credentials and Agent state remain owned by the Termux backend.
Uninstalling the shell does not remove backend data.

