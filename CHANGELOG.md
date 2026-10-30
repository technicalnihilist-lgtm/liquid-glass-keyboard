# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] — 2026-10-30

The glass is now yours to tune, following the third design pass.

### Added
- Glass controls on the setup screen: transparency (0–45%), frosting (0–50 px), colour boost (100–300%), floating colours on/off.
- Keyboard controls: key height (38–70 px) and space between keys (2–14 px) as sliders, replacing the three presets; glow-on-press switch.
- Appearance: Auto / Light / Dark segmented control, bounce (0–100%) and a reset-to-defaults button.
- Pressed keys now sink under the finger — the key treatment scales down by up to 14%, tied to the bounce setting.
- Frosting drives the real window blur radius on Android 12+, so 0 px means no blur at all.

### Changed
- Preferences are now numeric sliders stored as ints; the old three-step key size preference is migrated by default value.
- The orbs switch replaces the older motion switch.

## [2.0.1] — 2026-10-28

Open-source housekeeping. No behaviour changes.

### Added
- MIT licence header in every source file.
- GitHub Actions workflow that builds the APK on every push and fails if the result requests
  the `INTERNET` permission.
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, this changelog, issue and pull request
  templates.
- `design/` holding the original HTML mock-up the keyboard's look came from, for provenance.
- `fastlane/metadata/android/` so the app can be listed on F-Droid without further work.
- `SHA256SUMS`, and a "verifying a download" section in the README.

### Changed
- `versionCode` 3, `versionName` 2.0.1, so the published binary matches the published sources.

## [2.0.0] — 2026-10-27

### Added
- Layered key material: blurred specular rim, inner rim fading to the base, three-stop body,
  caustic pool along the bottom of each key, upper-left light source, and paired contact shadows.
- Real backdrop blur on Android 12+ (`FLAG_BLUR_BEHIND`, 26 dp) with a painted fallback.
- Drifting colour orbs, with a settings toggle.
- Static glass baked into a bitmap once, so more layers cost less per frame.

### Changed
- Thinner, brighter plate; more transparent key bodies so the backdrop bleeds through.

## [1.0.0] — 2026-10-27

### Added
- First release: native `InputMethodService` keyboard, no WebView.
- QWERTY with shift, double-tap caps lock and sentence auto-capitalisation.
- Numbers, symbols and emoji pages; long-press accents on 16 letters.
- Repeating backspace that clears selections and steps over surrogate pairs.
- Adaptive return key (`send`, `search`, `go`, `next`, `done`, `prev`).
- Dark glass, light glass or system theme; three key heights.
- Key-click sound and haptic tap, both switchable.
