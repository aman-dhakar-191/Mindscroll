# Mo's animation system

How Mo moves, and how to check it on a device.

## How it fits together

- `MoMotion.pose(...)` turns a clock and a few start times into one `MoPose`. The face (brows,
  eyes, mouth) and the body (squash, stretch, hop, lean) are both drawn from that single pose,
  so they cannot drift apart.
- `MotionProfile` (CALM / BALANCED / PLAYFUL) holds the timing and physics of a style. The
  Settings choice maps to it through `Settings.MotionStyle.profile`.
- `MoPainter` draws a pose. `MoView` (overlay windows) and `MoFace` (Compose home screen) play it.
- A reaction is a crouch, a fast pop, then a damped spring that overshoots and settles. The
  spring is a closed-form function, so there is no per-frame integration and no state.

| | CALM | BALANCED | PLAYFUL |
|---|---|---|---|
| Crouch before the pop | 80 ms | 150 ms | 260 ms |
| Pop rise | 320 ms | 200 ms | 170 ms |
| Settle | 850 ms, no overshoot | 1000 ms, one rebound | 1500 ms, several rebounds |
| Reaction size | 0.8x | 1.1x | 1.45x |
| Gesture speed (1x = 680 ms peek) | 0.55x | 0.65x | 0.75x |
| Idle sway | off in the overlay; small, ~15 fps on the home screen | on, ~20 fps | on, ~25 fps |

## Events and what Mo does

| Event | Body | Face |
|---|---|---|
| Reel counted | crouch, stretch up, squash on the rebound, settle | raised brows, wide eyes, "o" mouth |
| Fast scroll (3+ reels within 1.5 s) | same, as joy | `^ ^` eyes, open grin |
| App entry | leans out | raised brows, bigger smile |
| App switch | turns toward a sideways glance | one brow higher, eyes dart and return |
| Back after a break | wakes with a stretch and yawn, or hops joyfully if awake | joy |
| App exit | eases into a lean and turns away | eyes close in a smile |
| Three quick taps | squash, then a giggle | joy |
| Nothing happening | a small random behaviour every few seconds | varies |

An event that arrives mid-animation continues from the current pose: a reaction restarts from
where Mo is (and skips the crouch), and gestures blend out of the pose they interrupted.

## Local intelligence: temperament and idle behaviour

Plain rules and statistics, no model, nothing leaves the phone.

- **Fatigue changes the body, not just the face** (`MoTemperament`). A worn-out Mo (Dizzy, Sleepy)
  reacts smaller and slower than a rested one.
- **Habituation.** The first reel after a minute without one is a full startle. Each reel in a
  steady run startles a bit less, settling after about 9 reels at under half the size, and a
  little brisker. A minute without a reel and the surprise comes back.
- **Idle director** (`MoIdle`). Every 6-24 s (by mood and style) Mo does one small thing: look
  around, a curious peek, a stretch and yawn, or a little wiggle. Picked at random, weighted by mood
  (Sleepy yawns, Curious looks around) and the time of day (more yawns late at night), and not the
  same thing twice in a row. Never over an event animation, and only while the idle sway is on, so
  Calm and a switched-off screen stay still. It runs on a timer, not per frame.
- Both write to the detection log (`mo: idle → …`, and `run`, `novelty`, `energy`, `size`, `tempo`
  on each reel line), so what Mo did and why can be read back.

## Habits Mo notices (`MoScrollWatch`)

Detected from reel timestamps and app sessions; no polling, it sleeps until a pattern could next
become due. Each fires once per occasion.

| Habit | Detected | Mo does |
|---|---|---|
| Scrolling too fast | 5+ reels within 10 s (not again for 30 s) | panic: fast spiral eyes, sweat drops, speed lines |
| Lingering on one reel | 45 s since the last counted reel, still in the app | the side-eye (unamused half-lids, pupils slid sideways, a flat mouth) and a slow "...", and a line |
| Staring at one reel | 2 min since the last counted reel | mesmerised: slow spiral eyes, and a line |
| A long stretch | every 20 min in one session | stretch and yawn while a tuft of grass grows at his feet ("touch grass"), and a line saying how long |
| Straight back | back in the same app within 45 s of leaving | double take, then a "?!" pops up, and a line |
| Three quick taps | taps within 650 ms of each other | crying with laughter: the laughing face with tears streaming |

### Meme props

Reaction faces borrow the same trick as reaction memes: exaggerate the face and add a prop. Props
(`MoProp`) pop in with a little overshoot, hold, then shrink away, and several can show at once.
They are drawn as plain shapes (the "?" and "!" are paths, not font glyphs), attached to Mo so they
wobble and hop with him. Sweat and tears are a deeper blue so they show on every mood colour. While
tears are showing the laughing face stays too, otherwise they would read as sadness.

Lingering and staring only count after a reel was counted in the session, so reading comments or
messages in the app is not "stuck on a reel". All lines go through the same policy as the rest of
Mo's speech (quiet hours, snooze, rate limits). Each habit also writes `pattern: …` and the reaction
to the detection log.

## Battery

- Event animations draw at display rate only while they run.
- Idle sway redraws at the profile's idle rate, only while the screen is on.
- The Compose `MoFace` sleeps between frames when idle, stops while the app is paused, and
  has no second always-running animation.
- With the system animation scale at 0, Mo uses the CALM profile and no idle motion.

## On-device checks

Run each style (Settings, Animation style) through each scenario.

1. **Reel counter.** Scroll one reel in a tracked app. Mo should crouch, pop, and settle with a
   rebound whose size and bounciness match the style. CALM never bounces; PLAYFUL rebounds more
   than once.
2. **Interruption.** Scroll three reels quickly. Mo should keep moving smoothly, with no snap
   back to rest between reels and no second crouch, then turn excited on the third.
3. **Entry / switch / return / exit.** Open a tracked app (lean out), switch to another (sideways
   glance), leave for 5+ minutes and return (wake or joyful hop), then close it (turns away
   smiling, then the patch goes). Each should look different from the others.
4. **Sleep.** Let Mo fall asleep, then tap Mo or return to the app: stretch, yawn, eyes open.
5. **Edges.** Check nothing is clipped at the top or sides at the largest mascot size, on a
   device with a camera cut-out and one without.
6. **Reduced motion.** Turn off animations in Developer options. Mo stays still when idle and
   only reacts softly.
7. **Battery.** Leave the overlay on with the screen on for 10 minutes: `adb shell dumpsys
   gfxinfo io.github.amandhakar.mindscroll` should show a low frame count while nothing is
   happening, and none with the screen off.

## Characters and colours (`MoLook`, `MoBodies`)

Mo can look like one of six characters (Mo, Kit, Bun, Nimbus, Sprout, Boo) in one of five colour
sets (Classic, Peach, Berry, Ocean, Mono). The user picks both in Settings, or in setup.

- Every character wears Mo's face, so all moods, blinks, yawns and props work on all of them.
  Only the silhouette differs: `MoBodies` draws what is behind the body (ears, leaves), the body,
  and what is on it (whiskers, a nose), on the same 100 × 100 grid as the face. Bun's face sits a
  little lower (`MoCharacter.faceDrop`); `MoCharacter.top` is each one's highest point at rest,
  which the startle hop uses to stay inside the view.
- A palette gives every mood its own body colour (`MoPalette.color`). Classic is `Mood.color`.
- `MoLook` holds the current choice as Compose state. `Settings` writes it when the choice
  changes (and on start), and everything that draws Mo reads it, so there is nothing to pass around.
- The launcher icon follows the character: one `activity-alias` per character in the manifest,
  and `LauncherIcon` enables the chosen one. The icon drawables (`ic_launcher_fg_*`) show the
  Chill mood in Classic colours; a launcher icon can't change colour.
- The camera island hugs the camera hole with a thin rim and never starts closer than 3dp to the
  top of the screen (`CounterOverlay.patchHeight`, `patchTop`).

## What the camera island shows (`IslandInfo`)

Each tracked app chooses what the island's information slot shows (Apps to track → "Island shows"):
Timer, Counter, Both or Nothing. The rules are in `IslandInfo` and are unit tested:

- An app that doesn't count reels never shows the counter: Counter and Both fall back to Timer.
- **Both** keeps the counter up and brings the session timer in its place for 5 seconds every 5 or
  10 minutes (Settings → Camera island). **Timer** is always on, or comes and goes on the same
  rhythm. Changes fade; nothing scrolls.
- Apps with no choice of their own get the default for their kind (reel apps: Both; others: Timer),
  seeded from the old single setting so nobody's island changes on update.
- Both sides of the camera get the same width, so the hole stays in the middle of the island.
- After leaving an app the island hides after 3 or 5 seconds (`Settings.LeaveDelay`); when the
  phone can't say what is in front at all it waits twice as long (it used to wait 30 s).
- The detection log says what the island shows and why (`overlay shows ...`) and where the camera
  and island are (`overlay: camera=...`).
