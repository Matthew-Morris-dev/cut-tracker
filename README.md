# Cut Tracker

A personal Android app that makes three daily things one tap each:

- log calories and protein,
- keep a "no alcohol / no sugar" streak on a calendar,
- see weight and body-fat trends, imported from the Renpho scale through Health Connect.

Local-first: no account and no backend. Data lives in a Room database on the phone and is
covered by Android Auto Backup.

## Status

| Milestone | Scope | State |
| --- | --- | --- |
| 1. Log calories | Room schema, Today screen, quick-add kcal/protein, daily total vs target | Done |
| 2. Goals and calendar | Tags, treat/alcohol presets, auto-miss rule, month calendar, streaks | Done |
| 3. Renpho via Health Connect | Import, background sync, 7-day average chart, calorie write-back | Built, on-phone test pending |
| 4. Faster food entry | Open Food Facts search, barcode scanning | Planned |
| 5. Smart targets | Adaptive calorie target, goal-date projection, evening reminder | Planned |
| 6. Polish | CSV export, home-screen widget, release build | Planned |

## Health Connect

Renpho has no public API, but Renpho Health writes weigh-ins to Android's Health Connect, and the app
reads them from there. In Renpho Health, turn on its Health Connect sync; in Cut Tracker, tap
Connect on the Progress tab.

- **Reads** weight, body fat and lean body mass from every app, back to 30 days before the first
  sync. Each Health Connect record is one `BodyReading` row keyed by its record id, so re-reading is
  harmless; the Progress list groups readings taken within five minutes into one weigh-in.
- **Syncs** on every app open, from Sync now, and every 6 hours in the background when background
  read is granted. After the first import it uses one change token per metric, so only new and
  deleted records come through. A token unused for 30 days expires; the app then re-reads the last
  30 days and drops readings that are gone.
- **Writes** each logged food as a `NutritionRecord` (name, kcal, protein) with client id
  `foodlog-<id>`, and deletes it when the log is deleted. If Health Connect can't be reached at that
  moment, the next sync catches up.
- Manual weigh-ins still work, and are the only ones that can be deleted in the app.

Debug builds running on the emulator get an "Add test weigh-ins" button in Settings, so sync can be
tried without a scale. It never appears on a real phone.

## How goals work

Every food can carry a tag (`alcohol`, `treat`, or your own). A goal avoids one tag, so
"No alcohol" is the goal with avoid tag `alcohol`. For each goal on each day:

1. Any log that day carries the goal's tag → **missed** (straight away, no extra tap).
2. Otherwise, once the day is over and something was logged → **met**.
3. Otherwise → **unmarked** (grey on the calendar).
4. A manual status (tap a goal on Today, or a day on the Calendar) always wins.

Statuses are stored in `DayGoalStatus` and re-resolved whenever a log changes and each time the
app comes to the foreground, which is what turns yesterday green.

Streaks count consecutive days where every goal was met. A past unmarked day breaks a streak;
today being unmarked does not. With a weekly allowance (e.g. alcohol on one day a week), misses
within the allowance (weeks start Monday) keep the streak alive without adding to it.

## Building

Needs JDK 17 to 21 (Gradle 8.14 does not run on Java 25) and the Android SDK (platform 36).
With mise, `mise.toml` pins Temurin 21 for this repo; run `mise install` once.

```sh
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest       # unit tests, including Robolectric sync and migration tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI builds a debug APK on every push to `main` and attaches it to the workflow run. Each CI run
signs with a fresh debug key, so uninstall before installing a CI build over a local one.

## Layout

```
app/src/main/java/dev/matthewmorris/cuttracker/
  data/db/     Room entities, DAOs, database and first-run seed (presets, goals, targets)
  data/        Repositories and GoalStatusResolver (persists the day-status rule)
  data/health/ Health Connect: client wrapper, two-way sync, background worker
  domain/      Pure Kotlin: day-status rule, calendar colours, streaks, trends
  ui/          Compose screens: today, calendar, progress, settings
```

Stack: Kotlin, Jetpack Compose, Material 3, Room, Health Connect, WorkManager, Vico charts, MVVM
(screens → ViewModels → repositories), min SDK 28.
