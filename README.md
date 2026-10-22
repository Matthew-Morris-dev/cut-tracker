# Cut Tracker

A personal Android app that makes three daily things one tap each:

- log calories and protein,
- keep a "no alcohol / no sugar" streak on a calendar,
- see weight and body-fat trends (Renpho scale via Health Connect, coming in milestone 3).

Local-first: no account and no backend. Data lives in a Room database on the phone and is
covered by Android Auto Backup.

## Status

| Milestone | Scope | State |
| --- | --- | --- |
| 1. Log calories | Room schema, Today screen, quick-add kcal/protein, daily total vs target | Done |
| 2. Goals and calendar | Tags, treat/alcohol presets, auto-miss rule, month calendar, streaks | Done |
| 3. Renpho via Health Connect | Permissions, import, background sync, 7-day average chart | Planned |
| 4. Faster food entry | Open Food Facts search, barcode scanning | Planned |
| 5. Smart targets | Adaptive calorie target, goal-date projection, evening reminder | Planned |
| 6. Polish | CSV export, home-screen widget, release build | Planned |

Manual weigh-ins, a 7-day average and the weekly change are already on the Progress tab, so
weight can be tracked by hand until Health Connect lands.

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

Needs JDK 17+ and the Android SDK (platform 36).

```sh
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest       # domain unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI builds a debug APK on every push to `main` and attaches it to the workflow run. Each CI run
signs with a fresh debug key, so uninstall before installing a CI build over a local one.

## Layout

```
app/src/main/java/dev/matthewmorris/cuttracker/
  data/db/     Room entities, DAOs, database and first-run seed (presets, goals, targets)
  data/        Repositories and GoalStatusResolver (persists the day-status rule)
  domain/      Pure Kotlin: day-status rule, calendar colours, streaks, trends
  ui/          Compose screens: today, calendar, progress, settings
```

Stack: Kotlin, Jetpack Compose, Material 3, Room, MVVM (screens → ViewModels → repositories),
min SDK 28.
