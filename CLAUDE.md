# CLAUDE.md

Guidance for working in this repository.

## What this is

Cubench (formerly "Nano Timer") is an Android speedcubing timer app. It generates
scrambles for many puzzle types, records solve times, and shows statistics,
averages, graphs and sessions. Published on Google Play as `com.cube.nanotimer`
(GPL v3) — the display name is "Cubench" but the package/module name stays
`nanotimer` (permanent Play Store identity).

- Language: **Java** (source/target Java 17). No Kotlin.
- Build: **Gradle** (Android Gradle Plugin 8.13), multi-module.
- `minSdk 21`, `compile/targetSdk 36`. AndroidX (`useAndroidX=true`, Jetifier on).
- Current version: see `nanotimer/build.gradle` (`versionCode` / `versionName`).

Related project: **3StyleTrainer** (sibling Flutter app, same owner) lives at
`C:\Projects\3StyleTrainer\three_style_trainer`. Its `packages/smartcube/lib/src/` Dart
package is the reference implementation the `smartcube` module was ported from, and its
`docs/smart-cube-integration-plan.md` holds the smart-cube protocol research of record.

## Modules

The build is split into one app module and several libraries (see
`settings.gradle`). Dependency direction flows downward; `datamodel` is the leaf.

- **`nanotimer`** — the application (`applicationId com.cube.nanotimer`). Contains
  all GUI (`gui/`), scramble generation (`scrambler/`), and utilities (`util/`).
  Namespace `com.cube.nanotimer`.
- **`services`** — persistence layer. SQLite access, the `Service` facade, and DB
  schema/migrations. Namespace `com.cube.nanotimer.services`.
- **`datamodel`** — value objects (`vo/`), enums (`CubeType`, `ScrambleType`,
  `SolveType`…), session/stats logic (`session/`). Pure-ish model, the leaf module.
  Namespace `com.cube.nanotimer.vo`.
- **`guilib`** — shared drawables, styles, themes, app icons.
- **`filebrowser`** — vendored file picker (`com.ankhsoft.filebrowser`) used by
  import/export.
- **`wheel`** — vendored Android Wheel widget (`kankan.wheel`) for picker dialogs.

Third-party deps of note: `MPAndroidChart` (graphs), `drag-sort-listview`
(reorderable lists).

## Build & test

Day-to-day development is done in **Android Studio** (Run config + the Gradle test
gutter). The Gradle wrapper is also available for the command line — on Windows use
`gradlew.bat` (or `./gradlew` from Git Bash). `local.properties` must point
`sdk.dir` at the Android SDK.

```sh
./gradlew assembleDebug                 # build the debug APK
./gradlew :nanotimer:test               # JUnit unit tests (nanotimer module)
./gradlew test                          # unit tests, all modules
./gradlew :nanotimer:connectedAndroidTest   # instrumented tests (needs a device/emulator)
```

Unit tests live in `nanotimer/src/test/...` and exercise the scramble solvers,
formatters, and stats (e.g. `ThreeSolverTest`, `TimesStatisticsTest`,
`CubeSessionTest`). Instrumented tests are under `*/src/androidTest/...`.
Tests are plain **JUnit 4**.

## Architecture notes

### Service / persistence layer (`services` module)
- `App.INSTANCE` (an enum singleton) is the app-wide entry point. It holds the
  `Context`, the `Service`, sound manager and dynamic translations, and is
  initialized lazily via `setContext` / `setApplicationContext`.
- `App.INSTANCE.getService()` returns the `Service` facade (interface in
  `services/.../Service.java`, implemented by `ServiceImpl`).
- **All `Service` methods are asynchronous**: they take a `DataCallback<T>` and
  run the DB work on a background thread (`ServiceImpl` extends `DBHelper` and
  wraps each call in `run(Runnable)`). Callbacks fire off the main thread — marshal
  back to the UI thread before touching views.
- `ServiceProvider` / `ServiceProviderImpl` hold the actual SQL; `ServiceImpl`
  just dispatches to them.
- Schema constants and `DB_VERSION` are in `services/.../db/DB.java`. Migrations
  live in `DBUpgradeScripts.java`. **When you change the schema, bump
  `DB.DB_VERSION` and add an upgrade script** — existing users' DBs are migrated,
  not recreated.

### Data model (`datamodel` module)
- `CubeType` is an enum of puzzles (2x2…7x7, Megaminx, Pyraminx, Skewb, Square-1,
  Clock, FTO), each with a stable integer `id` used in the DB and cache filenames.
- A `SolveType` is a user-defined practice category under a `CubeType` (lets you
  keep separate histories for the same puzzle), optionally with ordered
  `SolveTypeStep`s (multi-step timing) and an optional `blind` flag.
- `ScrambleType` / `ScrambleTypes` model special scrambles (F2L, last layer, PLL,
  etc.). The "default" scramble type means a normal full scramble.
- Stats/session logic: `CubeSession`, `TimesStatistics` (averages: Ao5/Ao12/…,
  Mo3 for blind, best/worst trimming).

### Scramblers (`nanotimer/scrambler`)
- `ScramblerFactory.getScrambler(CubeType)` returns a basic random-move
  `Scrambler` per puzzle (`scrambler/basic/`).
- `ScramblerService.INSTANCE` (enum singleton) manages **random-state** scrambles
  (`scrambler/randomstate/`) for 3x3, 2x2, Pyraminx, Square-1 and FTO — these are
  expensive Kociemba/two-phase-style searches, so they are:
  - generated on background threads (one per CPU core minus one),
  - cached **in memory** (capped at `MAX_SCRAMBLES_IN_MEMORY = 500`) and **on
    disk** (`randomstate_scrambles_<cubeTypeId>[_<scrambleType>]` files),
  - refilled lazily via `checkCache` whenever a scramble is consumed.
  - Quality/length is controlled by `ScramblesQuality` and `Options`.
- Square-1 solver uses precomputed pruning tables shipped as raw resources
  (`nanotimer/src/main/res/raw/square1_*.dat`).
- `RandomStateGenListener` / `RandomStateGenEvent` push generation progress to the
  UI and to a foreground notification (wired up in `App.initRandomStateGenListener`).

### GUI (`nanotimer/gui`)
- Activities: `MainScreenActivity` (launcher, drawer), `TimerActivity` (the timer),
  `GraphActivity`, `SolveTypesActivity`, `OptionsActivity`, `Export`/`ImportActivity`.
- **`TimerActivity` must stay a full-screen tap target.** A tap *anywhere* in the timer
  activity starts/stops the timer — there must be no dead zones where a press is ignored.
  This is deliberate: it guarantees the user can never mistakenly believe the timer started
  (or stopped) when it didn't because they happened to press an inert area. When adding or
  changing views in the timer screen, keep the whole surface clickable for start/stop — don't
  introduce regions or controls that swallow the touch without toggling the timer.
- `Options.INSTANCE` wraps `SharedPreferences` (keys in `res/xml/preferences.xml`).
- Import/export is CSV (`util/exportimport/`), shared via a `FileProvider`
  (authority `${applicationId}.fileprovider` — the debug build has an
  `applicationIdSuffix`, so never hardcode the authority).
- Localized into English, French (`values-fr`), Spanish (`values-es`) and
  Portuguese (`values-pt`). Add new user-facing strings to `strings.xml` in
  **all four** locales — forgetting one leaves that language showing English.
  Localized strings live in `nanotimer`, `datamodel` and `filebrowser`
  (`guilib` and `services` only hold `app_name`, untranslated).

### "Pro" unlock (legacy / inactive)
The app is now **completely free, open-source, and fully unlocked** — it used to
show ads and gate features behind a separate paid unlocker app
(`com.cube.nanotimerpro`). That mechanism is no longer used. `ProChecker` and
`App.INSTANCE.isProEnabled()` still exist but should be treated as dead/legacy
code; don't add new feature gating on top of it.

## Dead / legacy code (don't build on these)

Several subsystems are present but inactive — don't extend them without checking:
- **Pro unlocker** — `ProChecker` / `App.isProEnabled()` (see above).
- **Stackmat timer** — `util/stackmat/StackmatController`; permissions commented out
  in the manifest.
- **Charging-based scramble generation** — `ChargingStateService` /
  `ChargingStateReceiver`; the `<service>`/`<receiver>` and the `isFromPhonePlugged`
  code paths are commented out. The `FOREGROUND_SERVICE` permission is a leftover
  with no live service behind it.
- `ServiceProviderImpl.insertTestTimes` is test-only scaffolding.

## Conventions

- Indentation is **2 spaces**. Match the surrounding file's style.
- Singletons are commonly modeled as `enum X { INSTANCE }`.
- Keep DB work behind `Service`; don't query SQLite directly from the GUI.
- When adding a puzzle or scramble type, touch `CubeType`/`ScrambleTypes`
  (datamodel), the relevant scrambler + `ScramblerFactory`, and strings.
