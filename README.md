# Liquid Glass Keyboard

A real Android keyboard — a native `InputMethodService`, not a web page in a shell — with a
glassy, colour-shifting keypad. Fully offline: the APK requests no internet permission at all.

<p align="center">
  <img src="screenshots/01-keyboard.png" width="240" alt="Typing with the dark glass keyboard">
  <img src="screenshots/02-shift.png" width="240" alt="Shift held, keys showing capitals">
  <img src="screenshots/04-accent.png" width="240" alt="Long-press accent picker on the n key">
</p>

## Download

**[LiquidGlassKeyboard.apk](LiquidGlassKeyboard.apk)** — 178 KB, signed, `minSdk 21` (Android 5.0+),
`targetSdk 34`.

Install it, open the app, then:

1. Tap **Enable keyboard** → turn on *Liquid Glass Keyboard* in the system list.
2. Tap **Choose keyboard** → pick *Liquid Glass Keyboard*.

The app shows your live status, so you can tell whether it is enabled and whether it is the
keyboard currently in use. There is a text box on the same screen to try it out immediately.

## What it does

- Full QWERTY with shift, double-tap caps lock, and automatic capitals at the start of a sentence.
- Three symbol pages (letters, numbers, symbols) plus an emoji page of 48 characters.
- Long-press a key for accented variants — `e` gives è é ê ë ē ė ę, and 16 letters carry alternates.
  Slide to the character you want and let go; the base character is swapped for it.
- Backspace deletes one character, repeats while held, removes whole selections, and steps over
  emoji rather than splitting them in half.
- The return key adapts to the field: it reads *send*, *search*, *go*, *next*, *done* or *prev* and
  triggers the right editor action, falling back to a newline in multi-line fields.
- Every press rotates the accent hue and throws a glow and a colour flare, exactly as the original
  HTML mock-up did — but drawn on a `Canvas` at 60 fps rather than in a browser.
- Popup preview above the key you are pressing, key-click sound and haptic tap (both switchable).
- Dark glass, light glass, or follow the system theme. Three key heights.

## What it deliberately does not do

No predictive text, no autocorrect, no swipe typing, no clipboard history, no cloud anything.
It types what you press and nothing else. Nothing leaves the device, and there is no network code
in the APK to leave with.

## Screenshots

| | |
|---|---|
| <img src="screenshots/01-keyboard.png" width="220"> | <img src="screenshots/03-numbers.png" width="220"> |
| Dark glass, key press flare | Numbers and symbols page |
| <img src="screenshots/05-light.png" width="220"> | <img src="screenshots/06-setup.png" width="220"> |
| Light glass | Setup, live status and preferences |

The screenshots are rendered from the same geometry, colours and gradients the keyboard draws with
(`tools/render_shots.py` mirrors `KeyboardView.onDraw`), so they match what the APK puts on screen.

## How it is built

No Gradle. The APK is assembled directly with the Android build tools, which keeps the build
readable end to end:

```
app/
  AndroidManifest.xml          IME service + setup activity
  java/com/liquidglass/keyboard/
    LiquidImeService.java      InputMethodService: commits text, handles enter/backspace
    KeyboardView.java          canvas drawing, touch handling, glow and flare animations
    KeyboardLayout.java        every page's rows, weights and long-press alternates
    GlassPalette.java          colour tokens, HSL helper, preferences
    GlassBackdrop.java         gradient sky + drifting colour orbs
    SetupActivity.java         enable/choose flow, live status, preferences
  res/                         styles, glass drawables, layout, launcher icons, IME metadata
build.sh                       aapt2 → javac → d8 → zipalign → apksigner
tools/                         icon generator and screenshot renderer
```

To build it yourself:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk     # needs build-tools 34.0.0 and platforms/android-34
./build.sh
```

The signing key is created on first run at `~/.liquidglass/keyboard.jks` (override with `KS` and
`KSPASS`). Replace it with your own key if you plan to publish — builds signed with a different key
cannot update over this one.

## Permissions

`VIBRATE` only. There is no `INTERNET` permission, so the app cannot phone home even in principle.

## Licence

MIT — see [LICENSE](LICENSE).
