# Mindscroll

A personal digital wellbeing tracker for Android. Version 1 counts the reels and shorts you
watch in **Instagram**, **YouTube Shorts** and **Facebook**, and keeps today's number in front
of you, with **Mo** keeping you company:

- **Status bar / notification:** the count itself is the status bar icon. The notification
  shows Mo, what Mo thinks of today so far, and a per-app breakdown.
- **Camera patch:** a black island around the front camera, with Mo on one side and the count on
  the other. Tap it to open a panel with Mo's take, per-app counts and the last 7 days. It's
  drawn by the accessibility service, which can draw above the status bar. A notification can't
  do that.
- **Floating bubble:** Mo with a count badge, which you can drag anywhere. Tap it for the same
  panel. Off by default.
- **Home screen widget** with Mo, the count and Mo's line.

## Meet Mo

Mo is a small blob who counts with you: cheeky, on your side, never preachy. Mo's mood (and
color) follows today's count: **chill** (0–15), **curious** (16–40), **wobbly** (41–80),
**dizzy** (81–150) and **running on fumes** (151+). Mo's lines change every few reels, with
special ones at milestones and late at night. Moods and lines live in
[`Mo.kt`](app/src/main/java/io/github/amandhakar/mindscroll/mascot/Mo.kt), and the drawing is in
[`MoPainter.kt`](app/src/main/java/io/github/amandhakar/mindscroll/mascot/MoPainter.kt).

Everything stays on the phone. The app has no internet permission.

## Install

Install it from the [private app store](https://github.com/aman-dhakar-191/private-app-store),
or download the APK from the [latest release](https://github.com/aman-dhakar-191/Mindscroll/releases/latest).
On first launch, onboarding walks through:

1. **Accessibility** (required). This is how reels are counted. If Android says *Restricted
   setting*, open App info, tap ⋮, choose *Allow restricted settings*, and try again.
2. **Notifications**, for the status bar count.
3. **Battery optimization**, so the phone doesn't kill the counter. Phones with their own
   "auto-start" or "sleeping apps" lists need Mindscroll kept off those as well.

The overlays don't need the *display over other apps* permission, because the accessibility
service draws them.

## How reels are counted

The accessibility service only listens to Instagram, YouTube and Facebook. Within those, it
looks for a *reel feed*: either a known feed view id, or a list that fills most of the screen
while one item fills the list. That rule filters out normal feeds, and sideways pagers like
stories are skipped.

- **Opening a feed counts its first reel.** This covers the Reels tab, tapping a reel in the
  feed, and coming back to the app. Window changes trigger a check of the screen, at most
  every 0.8 s, because video apps change their content constantly.
- **Swiping counts each new reel** once the list settles on an item further down than any
  already counted in this visit, so swiping back and forth doesn't count again. A refresh
  back to the top counts its new first reel.
- **A sheet or dialog over a reel** (comments, share) doesn't count again when it closes. Nor
  does coming back to the exact reel you left.
- **A visit ends** when you leave the feed, or after 45 s with no activity at all from the app.

If an app doesn't report item positions, each separate swipe gesture counts once instead. These
rules live in [`ReelDetector`](app/src/main/java/io/github/amandhakar/mindscroll/tracker/reels/ReelDetector.kt).
They're unit tested, and the per-app view ids are in
[`ReelApps`](app/src/main/java/io/github/amandhakar/mindscroll/tracker/reels/ReelApps.kt).
Apps rename their internal views now and then, so if counts look off after an app update,
start there.

## Architecture

```
tracker/   Tracker interface, TrackerRegistry, reels/ (the V1 tracker)
data/      Room: one metric_event table, MetricsRepository, Days, Settings
service/   TrackerAccessibilityService (routes events to trackers), BootReceiver
display/   CounterNotification, CounterOverlay, CounterWidget
ui/        Compose: onboarding, home
```

**Trackers** implement [`Tracker`](app/src/main/java/io/github/amandhakar/mindscroll/tracker/Tracker.kt)
and are listed in `TrackerRegistry`. The accessibility service subscribes to the union of the
packages and event types the trackers need, and hands each event to the trackers that want it.
A tracker records measurements through a `Recorder`. It never touches the database itself.

**Data:** every tracker writes rows into the same `metric_event` table:
`(tracker_id, item_key, value, day, timestamp)`. For reels, that's `("reels", <package>, 1, …)`.
Daily totals, per-app breakdowns and multi-day series are all `SUM` queries in
`MetricsRepository`, so a new tracker needs no schema change.

### Adding the planned features

| Feature | Where it plugs in |
| --- | --- |
| Screen time per app | A `ScreenTimeTracker` (`id = "screen_time"`, `item_key` = package, `value` = ms) that reads `UsageStatsManager` from a periodic worker. It doesn't use accessibility events, so its `watchedPackages` stays empty. Needs the usage-access permission in onboarding. |
| Daily limits + gentle nudges | A `limit` table (`tracker_id`, `item_key`, `daily_limit`), added with a Room migration, plus a nudge component that collects `observeTodayTotal` / `observeTodayByItem` and posts a notification or briefly changes the camera patch color when a limit is crossed. The service already collects these flows for the displays. |
| Weekly stats | `MetricsRepository.observeDailyTotals(trackerId, days)` already returns zero-filled day series. The home screen's 7-day chart uses it. `timestamp` is stored for hour-of-day breakdowns later. |

Room exports schemas to `app/schemas/` on build. Commit that folder after the first local build,
so future migrations can be checked against it.

## Releasing

Releases work like the store's own: GitHub Releases is the backend, and the store picks up
new versions from here.

### One-time setup

Create the signing key with
[android-keystore-generator](https://github.com/aman-dhakar-191/android-keystore-generator)
(private). Go to *Actions → Generate signing key → Run workflow* and set `target_repo` to
`aman-dhakar-191/Mindscroll`. This writes the four secrets the Release workflow reads straight
into this repo:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the keystore, base64 |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the key alias |
| `KEY_PASSWORD` | the key password |

`target_repo` needs that repo's `TARGET_REPO_TOKEN` to have *Secrets: read and write* on
Mindscroll. Without it, add the values from `secrets.txt` here by hand. Download the encrypted
archive from the run within a day and back it up. Mindscroll gets its own key, separate from the
store's: every update must be signed with the same key, so losing it means everyone has to
uninstall and reinstall.

### Each release

**Actions → Release → Run workflow** on `main`, then pick **patch**, **minor** or **major**.
The workflow finds the latest `vX.Y.Z` tag, builds a signed APK, and publishes the release.
The first release starts from `0.0.0`, so pick **minor** to get `0.1.0`. `versionCode` is
`major × 1000000 + minor × 1000 + patch`.

The [Android workflow](.github/workflows/android.yml) runs the unit tests and an unsigned test
build on every pull request and on `main`.

## Building locally

Needs JDK 17 and the Android SDK.

```sh
./gradlew testDebugUnitTest assembleDebug
```
