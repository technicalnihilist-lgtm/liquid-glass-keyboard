# Security Policy

## The short version

This keyboard has no `INTERNET` permission. It cannot send anything anywhere. There is no
analytics, no telemetry, no crash reporting, no update check, no ads, and no third-party SDK.

## Supported versions

| Version | Supported |
|---|---|
| 2.0.x | yes |
| 1.0.x | no — please update |

## What counts as a vulnerability

- Any way to make the keyboard send data off the device.
- Any way to make it read text it should not: content from another field, another app, or the
  clipboard.
- Text being committed to the wrong field, or surviving after the field loses focus.
- A crash reachable from normal typing, including via crafted clipboard or text content.
- Anything that lets another app drive the keyboard's `InputMethodService` without the
  `BIND_INPUT_METHOD` permission.

Typing your password into a password field and the keyboard remembering it would be a serious
bug; the keyboard holds no history, so it should be impossible — tell us if you find otherwise.

## Reporting

Please do not open a public issue for a security problem. Use GitHub's private reporting:

**https://github.com/technicalnihilist-lgtm/liquid-glass-keyboard/security/advisories/new**

Include the version (Settings → Apps → Liquid Glass Keyboard), your Android version and device,
and the shortest steps that reproduce it. You will get a reply as soon as the maintainer sees it;
this is a spare-time project, so please allow a few days.

## Verifying a download

Every release is signed, and each build's SHA-256 is published in `SHA256SUMS`. To check a
download:

```bash
sha256sum LiquidGlassKeyboard.apk
```

Compare against `SHA256SUMS` in the repository, and against the digest shown on the release page.
If they differ, do not install it.

You can also verify the signing certificate:

```bash
apksigner verify --print-certs LiquidGlassKeyboard.apk
```

Builds made by CI are signed with a throwaway key and are for testing only — they will not
install over a released build.
