# Security policy

## Supported version

Security fixes are applied to the latest source revision and GitHub release.

## Threat model

CloudCLI Android Shell is designed for a single user running CloudCLI on the same
Android device. It is not a remote administration gateway and must not be treated
as protection for a CloudCLI server exposed to an untrusted network.

The shell enforces these boundaries:

- the only permitted web origin is `http://127.0.0.1:3001`;
- cleartext traffic is denied for every other host;
- cross-origin navigation, subresources, redirects and downloads are blocked;
- external links are not handed to another browser or application;
- WebView debugging, file access, mixed content, third-party cookies and
  JavaScript bridges are disabled;
- microphone access is optional and is granted only to the fixed local origin;
- Android backup is disabled.

JavaScript remains enabled because the CloudCLI frontend requires it. The local
CloudCLI service is responsible for login security, session storage, provider
credentials and Agent authorization.

## Secrets and release artifacts

Do not commit any of the following:

- signing keystores or keystore passwords;
- API keys, access tokens, cookies or CloudCLI account databases;
- `.env` files, device screenshots, logs or conversation exports;
- locally built APKs when they have not passed `./verify.sh`.

`build.sh` stores its generated signing identity outside the repository. Keep a
secure backup if future APKs must upgrade an existing installation.

## Reporting a vulnerability

Use GitHub's private security advisory feature for this repository. Do not put
credentials, private conversation data or a working exploit against a public
device in a normal issue.

