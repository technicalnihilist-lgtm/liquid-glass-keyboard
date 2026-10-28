# Contributing

Thanks for wanting to help. This is a small, deliberately boring project: one input method,
no dependencies, no Gradle, no network code. Please keep it that way.

## Building

You need a JDK 17 and an Android SDK with `platforms;android-34` and `build-tools;34.0.0`.

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash build.sh
```

That produces `LiquidGlassKeyboard.apk` in the project root. The first run creates a signing key
at `~/.liquidglass/keyboard.jks` — override with `KS` and `KSPASS`. A build signed with your own
key will install fine, but it cannot update over the released build (Android requires a matching
signature), so uninstall first if you are testing on top of a release.

CI (`.github/workflows/build.yml`) runs the same script on every push and fails the build if the
resulting APK requests `INTERNET`. Keep that invariant.

## Layout

| Path | What lives there |
|---|---|
| `app/java/com/liquidglass/keyboard/LiquidImeService.java` | the `InputMethodService`: commits text, handles enter/backspace, asks for window blur |
| `app/java/com/liquidglass/keyboard/KeyboardView.java` | all drawing and touch handling |
| `app/java/com/liquidglass/keyboard/KeyboardLayout.java` | pages, key weights, long-press alternates |
| `app/java/com/liquidglass/keyboard/GlassPalette.java` | colour tokens and preferences |
| `app/res/` | styles, drawables, the setup screen layout, launcher icons, IME metadata |
| `tools/render_shots.py` | renders the screenshots from the same geometry the view uses |
| `tools/make_icons.py` | generates the launcher icons |

If you change how a key is drawn, change `tools/render_shots.py` to match and re-run it — the
screenshots in the README are meant to be an honest preview, not a separate mock-up.

## Scope

In scope: better typing behaviour, layouts for more languages, accessibility, performance,
visual refinements that keep the glass look.

Out of scope, by design: predictive text, autocorrect, swipe typing, clipboard history, cloud
sync, analytics, ads. Anything that needs the `INTERNET` permission will not be merged.

## Pull requests

- One focused change per pull request.
- Say what you tested and on which Android version and device.
- Run `bash build.sh` before pushing; CI will run it too.
- Match the existing style: four-space indent, no external libraries, no build system.
- New files get the MIT header that the existing sources carry.

## Commit messages

Short imperative subject line, then a body explaining *why* if the reason is not obvious.
`Fix backspace splitting emoji` is good; `fix stuff` is not.
