# Agent OS — Phone (3)

Agent OS is the entry point to a voice-first OS for the Nothing Phone (3). It runs as the home
screen. An agent lives in the middle of the display, and you mostly get around the phone by
talking to it.

```
nothing-os/
├── app/          Android app (Kotlin, Jetpack Compose), minSdk 31, target 35
└── prototype/    Browser prototype of the same agent, gestures and buttons
```

## Interaction model

| Input | Action |
| --- | --- |
| **Double tap** the display | Wake the agent and start listening |
| **Single tap** | Show info (time, date, battery, next alarm). Tap again, or wait 10 s, to close it |
| Tap while listening | Stop listening and answer now |
| **Swipe up** | App list (the fallback way to open apps) |
| **Volume up**, tap | Show or hide info. Holding it still changes volume |
| **Volume down**, tap | Dismiss: stop listening or speaking and go back to idle. Holding it still changes volume |
| **Essential Key** | Tap wakes the agent. Hold to talk, release to send *(keycode not confirmed yet, see below)* |
| **Glyph Button** (back) | Select the *Agent* Glyph Toy. Long-press it to wake the agent |
| Power | System only (apps can't intercept it) |

All button bindings are in one table, `ButtonMap` in `app/.../input/Buttons.kt`.

The agent has five states, and each one has its own motion. The on-screen agent and the
Glyph Matrix both show it:

- **idle**: slow breathing and drift
- **info**: shrinks and moves to the top
- **listening**: cells swell in a wave driven by your voice level; a halo grows on the Matrix
- **thinking**: a ripple moves out from the centre
- **speaking**: a rhythmic pulse

## What the agent can do (v0)

`voice/CommandRouter.kt` uses simple pattern matching, on purpose. An LLM goes in at this point
later.

- "what time is it", "what's the date", "battery"
- "set a timer for 5 minutes", "wake me up at 7:30"
- "torch on" / "torch off", "open camera"
- "open Spotify" (matches any installed app by its label)
- "Wi-Fi settings", "Bluetooth", "display", "sound", "settings"
- "call 0123 456789" (opens the dialer)
- "status" / "info" (opens the info view)
- anything else becomes a web search

Speech uses Android's `SpeechRecognizer` and replies use `TextToSpeech`.

## The agent's visual

`agent/AgentModel.kt` grows the agent from a seed. The core is a mirrored grid of cells with
links, rings and ball-and-stick satellites. Around it sits a shell of outlined bubbles and
pills. `ui/AgentOrb.kt` draws it in five layers. The merged, organic outlines come from a
**blur plus a steep alpha threshold** (`RenderEffect`), which melts nearby shapes into one
blob. `glyph/MatrixRenderer.kt` samples the same model onto the 25×25 Matrix, using a
smooth-min distance field that gives the same merged look at low resolution.

`prototype/agent.js` is a line-by-line port of the model and uses the same PRNG (mulberry32).
The prototype therefore shows **the same agent as the phone**. This was checked: both produce
identical geometry and samples.

To change the agent, edit the model in both files, or change the `seed`.

## Running it

### On the phone

1. Open `nothing-os/` in Android Studio and run the `app` configuration on the Phone (3).
2. To use it as the OS entry point, press Home and choose **Agent** as the default home app.
3. Allow microphone access on the first double tap.

### Glyph Matrix

1. Download the Glyph Matrix SDK `.aar` from
   [GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
   and put it in `app/libs/`.
2. Turn on Glyph debugging, which allows the `test` API key in the manifest:
   `adb shell settings put global nt_glyph_interface_debug_enable 1`
3. While Agent OS is in the foreground, it mirrors the agent onto the Matrix.
4. In Glyph Toys settings, add **Agent**. Press the Glyph Button to select it, and long-press it
   to wake the agent.

The SDK is reached through reflection, so the app also builds and runs without the AAR. In
that case the Matrix stays dark.

### Prototype (any browser)

```bash
cd nothing-os/prototype && python3 -m http.server 8977
```

Open <http://localhost:8977>. Tap and double tap the phone. Use `E`, `↑`, `↓` and `G` for the
buttons (hold `E` to push-to-talk). The panel on the right shows a live preview of the
Glyph Matrix. Voice uses the Web Speech API where it's available, and otherwise plays a
simulated utterance.

## Status and open points

- **No Gradle build has been run yet.** The environment this was written in can't reach
  Google's Maven repository. All files that don't use AndroidX (model, Glyph, buttons, voice,
  device) were compiled with Kotlin 2.1 against the Android 15 framework jar. The Compose and
  AndroidX files (`ui/`, `MainActivity`, `AgentViewModel`) have only been reviewed by hand.
  Android Studio may offer to bump the AGP and Kotlin versions.
- **Essential Key:** the keycode it sends to apps isn't documented, and the system may keep the
  key for Essential Space. `KEYCODE_ASSIST` and `KEYCODE_VOICE_ASSIST` are mapped as guesses.
  Any unmapped key shows `key <code> · unmapped` at the top of the screen. Press the Essential
  Key once and add the code it shows to `ButtonMap`.
- **Matrix brightness range:** `GlyphMatrix.MAX_BRIGHTNESS` is set to 4095. Check this against
  the kit docs on the device. If the range is 0–255, the Matrix will look binary until the
  value is changed.
- **Glyph Toy events:** the `GlyphToy` constants are read from the SDK, with documented
  defaults as a fallback.
- Android may block activity launches from the background, so a long-press on the Glyph Button
  might not always bring the screen up while the phone is locked.
