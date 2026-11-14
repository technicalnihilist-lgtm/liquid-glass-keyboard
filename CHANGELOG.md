# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.1] — 2026-11-14

Layout maths: the key grid is computed once and reused, and every place it can go
wrong now has a bound. No visible change — the key rects are bit-for-bit identical
to 3.0.0 at every density, gap and key height.

### Changed
- The key grid is cached and rebuilt only when the width, the page, the key height or
  the gap changes, instead of on every frame and on every touch. Saves one allocation
  of the whole board's rects per draw and per `ACTION_DOWN`.
- The row under a touch is found arithmetically from the uniform row pitch rather than
  by scanning every row. Hit areas are unchanged, including the half-gap slop between
  rows and the exact points where two bands meet.
- Each row's last key absorbs the sub-pixel drift of the weight sum, so a row ends
  exactly on the right margin instead of a hairline short of it.
- The clip path used by `onDraw` is cached per size rather than rebuilt every frame.

### Fixed
- Changing key height or key gap while the keyboard was open left the board at its old
  measured height, clipping the bottom rows; the view now re-measures.
- The keyboard never claims more height than the IME window offers, so a short window
  no longer reports a height it cannot fill.
- An accent strip wider than the screen (seven alternates on a narrow display) was
  positioned at a negative x and ran off the left edge; the bubbles now shrink to fit
  and the strip is clamped to both margins.
- Key-rect lookups are bounds-checked through one accessor, so a stale row/column index
  from a pending long-press or repeat can no longer throw `IndexOutOfBoundsException`.
- Pending long-press and key-repeat callbacks are cancelled when the page changes or
  the board resets, so a held backspace cannot keep deleting into a new input field.
- Label autoshrink is floored at 1 px, so an extreme gap/key-height combination can no
  longer compute a negative text size.

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
