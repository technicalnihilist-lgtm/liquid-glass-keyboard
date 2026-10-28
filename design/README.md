# Design

`Liquid_Glass_Keyboard.html` is the original single-file mock-up this keyboard grew out of: a
browser demo of a glass keypad with hue-rotating press glows and drifting colour orbs. It is kept
here for provenance — it is where the palette, the flare behaviour and the 41-degree hue rotation
per press came from.

Open it in any browser to see the original. It is not used at runtime, is not shipped in the APK,
and is not the app: the keyboard itself is a native `InputMethodService` that draws every key on a
`Canvas`. Where the two differ, the native version is the truth:

- The mock-up used CSS `backdrop-filter`; the app draws layered gradients, rims and shadows, and
  asks Android for real window blur on 12+ instead.
- The mock-up's keyboard had no emoji page, no accent pickers, no repeating backspace and no
  adaptive return key.
- The mock-up's key radius and metrics were tuned for a 560 px page; the app's are in dp and
  scale with the device.

The screenshots in the repository root are rendered by `tools/render_shots.py`, which mirrors the
native drawing maths rather than this mock-up.
