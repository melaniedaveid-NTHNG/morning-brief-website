# Agent OS — Phone (3)

Agent OS is the entry point to a voice-first OS for the Nothing Phone (3). It runs as the home
screen. An agent lives in the middle of the display, and you mostly get around the phone by
talking to it.

```
nothing-os/
├── app/          Android app (Kotlin, Jetpack Compose), minSdk 33, target 35
└── prototype/    Browser prototype of the same agent, gestures and buttons
```

## Interaction model

| Input | Action |
| --- | --- |
| **Double tap** the display | Wake the agent and start listening |
| **Single tap** | Show info (time, date, battery, next alarm). Tap again, or wait 10 s, to close it |
| Tap while listening | Stop listening and answer now |
| **Swipe left** | Apps (the panel off the right edge) |
| **Swipe right** | Settings and permissions (off the left edge) |
| **Swipe up** | History of recent questions and answers (below). Tap one to ask it again |
| **Volume up**, tap | Show or hide info. Holding it still changes volume |
| **Volume down**, tap | Dismiss: stop listening or speaking and go back to idle. Holding it still changes volume |
| **Essential Key** | Tap wakes the agent. Hold to talk, release to send *(keycode not confirmed yet, see below)* |
| **Glyph Button** (back) | Select the *Agent* Glyph Toy. Long-press it to wake the agent |
| Power | System only (apps can't intercept it) |

All button bindings are in one table, `ButtonMap` in `app/.../input/Buttons.kt`.

Panels follow your finger and snap open past about a fifth of the screen. Swipe back, or press
Back or Home, to return to the agent.

The agent has five states, and each one has its own motion. The on-screen agent and the
Glyph Matrix both show it:

- **idle**: slow breathing and drift
- **info**: shrinks and moves to the top
- **listening**: cells swell in a wave driven by your voice level; a halo grows on the Matrix
- **thinking**: a ripple moves out from the centre
- **speaking**: a rhythmic pulse

The Glyph Matrix also shows the agent's output. Each reply appears there: it's held if it fits
in 25 columns, otherwise it scrolls past once. While the info view is open, the Matrix shows
the clock. Turn this off in Settings with "replies on matrix".

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

`agent/AgentModel.kt` grows the agent from a seed. It's a mirrored grid of cells with
links, rings and ball-and-stick satellites, plus a few dim discs and a bright pill. It has no
thin lines, only blobs. `ui/AgentOrb.kt` draws it in three layers. The merged, organic outlines come from a
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

1. The CI build downloads the Glyph Matrix SDK `.aar` from
   [GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
   Nothing's licence doesn't allow redistributing it, so it isn't committed. For a local build,
   copy it into `app/libs/`.
2. While Agent OS is in the foreground, it shows the agent and its replies on the Matrix.
3. In Settings → glyph toys, add **Agent**. Press the Glyph Button to select it, and long-press
   it to wake the agent. A selected Glyph Toy takes priority over the app on the Matrix.

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

- **Builds:** GitHub Actions builds the APK on every push that touches `nothing-os/`, and
  publishes it to the `agent-os-latest` release. A committed debug key keeps the signature
  stable, so each new APK installs over the last one.
- **Essential Key:** the keycode it sends to apps isn't documented, and the system may keep the
  key for Essential Space. `KEYCODE_ASSIST` and `KEYCODE_VOICE_ASSIST` are mapped as guesses.
  Any unmapped key shows `key <code> · unmapped` at the top of the screen. Press the Essential
  Key once and add the code it shows to `ButtonMap`.
- **Glyph Toy events:** the `GlyphToy` constants are read from the SDK, with documented
  defaults as a fallback.
- Android may block activity launches from the background, so a long-press on the Glyph Button
  might not always bring the screen up while the phone is locked.
