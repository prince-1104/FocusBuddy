# FocusBuddy

Native Kotlin / Jetpack Compose personal productivity app. Version 1 is local-only:
no account, backend, subscription, analytics, or runtime network dependency.

## Workspace inspection

The workspace was empty (including hidden files) on 19 October 2026. There was no
Gradle configuration, manifest, entry point, dependency set, source code, or Git
repository to retain. No files were deleted or regenerated.

No Java, Gradle, or ADB executable was found in PATH. JAVA_HOME and Android SDK
environment variables were unset; no SDK or Android Studio was found in the
common locations inspected. This is detection evidence, not proof that no custom
installation exists elsewhere on this machine.

## Foundation

- One `app` module, namespace and application ID `com.focusbuddy`.
- Minimum Android 8.0 (API 26), compile/target API 36, Java/JVM 17.
  API 26 gives native java.time support for the future calendar.
- Today opens by default. Today, Explore, Focus, Mascot, and Profile have screen
  scaffolds with bottom navigation on phones and a navigation rail on wider screens.
  Destination state uses a ViewModel, StateFlow, lifecycle-aware collection, and
  SavedStateHandle. Screen scroll/date state survives destination changes.
- Warm ivory, lavender, and indigo theme with restrained category colors, rounded
  cards, scalable type, subtle elevation, and short navigation/progress animations.
  Light, dark, and device theme modes are selectable in Profile and saved locally.
  System bar icon contrast follows the selected appearance.
- Explore has a custom, locale-aware full-month layout and selected-day agenda
  placeholder. Date selection and month browsing work. Independent task and
  reminder-alert counts are supported; unavailable counts show a dash, not zero.
- Reusable buttons, task/reminder cards, category/priority/completion controls,
  horizontal date selector, progress, mascot/empty states, loading/error feedback,
  bottom sheet, and confirmation dialog live in `ui/components`.
- Today is connected to Room through a screen ViewModel, with real task creation,
  editing, completion, deletion, selected-day progress, and standalone reminders.
  See [Today dashboard](docs/today.md) for behavior and test coverage. The focus
  timer, reminder delivery, and mascot customization remain deferred.
- No network or notification permissions. System cloud backup is excluded.
- No DI framework: use constructor injection and a small application-owned
  dependency container when persistence introduces shared dependencies.
- Room now stores tasks, reminders, categories, subtasks, and focus sessions behind
  transactional repositories. DataStore stores mascot/appearance preferences and
  migrates the previous theme preference. See [offline persistence](docs/persistence.md)
  for APIs, migration policy, calendar semantics, tests, and limitations.
- Today reads persisted records; other feature screens remain scaffolds. No
  production samples seed the database. Android notification delivery and
  recurrence execution remain deferred.

## Structure and boundaries

Current files:

```text
gradle/                         Version catalog and verified Gradle wrapper
app/build.gradle.kts            Single Android application module
app/src/main/AndroidManifest.xml
app/src/main/java/com/focusbuddy/
  MainActivity.kt               Android entry point; sets Compose content
  ui/
    AppViewModel.kt             Navigation and appearance state
    FocusBuddyApp.kt            Five destinations; phone bar / wider-screen rail
    components/                Reusable controls, cards, feedback, dates, artwork
    screens/                   Stateless scaffolds and display-only UI state
    today/                     Production dashboard route, ViewModel, state, editor
    theme/Theme.kt              Color palettes, type, shapes, theme selection
  FocusBuddyApplication.kt      Application-scoped dependency container owner
  app/AppContainer.kt           Constructor injection and lazy storage singletons
  data/local/                   Room entities, DAOs, converters, database/migration
  data/repository/              Transactional repository implementations
  data/preferences/             DataStore settings and legacy theme migration
  data/time/                    Device timezone-change source
  domain/model/                 Android-independent records and preferences
  domain/repository/            Testable Flow/suspend repository contracts
  domain/time/                  Zone source contract and DST-aware month boundaries
app/schemas/                    Migration baseline and exported Room schemas
app/src/test/                   Real SQLite/DataStore persistence and migration tests
app/src/debug/java/com/focusbuddy/ui/preview/
  FoundationPreviews.kt         Isolated samples, screen and component previews
app/src/main/res/               Strings, platform theme, backup rules
.github/workflows/android.yml   Build, persistence unit tests, lint, verification artifacts
```

Proposed packages to add as features need them (not empty directories):

```text
com.focusbuddy/
  app/                         Application-owned dependency container
  ui/screens/                  Add screen ViewModels with real feature state
  ui/components/               Mascot and shared product components
  ui/theme/                    Colors, typography, shapes
  domain/model/                Android-independent Task, Reminder, DaySummary
  domain/repository/           Repository contracts returning Flow / suspend APIs
  domain/usecase/              Date/count rules when reusable logic warrants them
  data/local/                  Room entities, DAOs, database, migrations
  data/repository/             Repository implementations and entity mapping
  notifications/               Channels, permission coordination, scheduling, receivers
```

UI observes ViewModels; ViewModels depend on domain contracts. Repository
implementations own Room access. Domain types must not import Compose, Room, or
Android notification APIs. Notification handling remains outside Composables and
DAOs. Keep the single module until there is a concrete reason to split it.

## Implementation sequence

1. Establish a working local build with JDK 17 and Android SDK 36.
2. Define tasks and separate reminders, their date/time semantics, and repository
   contracts. Add Room and compatible stable KSP, schema export, migrations, Flow
   queries, and constructor injection. Verify persistence across process restarts.
3. Build a Today vertical slice: create/edit/complete tasks with durable storage,
   ViewModel state, and the existing task cards and empty/error components.
4. Connect Explore's custom full-month grid and selected-day agenda. Every date
   must expose independent taskCount and reminderAlertCount values, including
   explicit zero counts. Count reminder records separately from tasks; do not
   infer reminder counts from task totals. Define timezone boundaries and count
   semantics before writing aggregate queries. Avoid a Google Calendar layout.
5. Add Android reminder scheduling and notifications, permission handling,
   rescheduling after reboot/timezone changes, and reminder cancellation.
6. Verify database migrations, month boundaries/leap years, locale/timezone
   behavior, accessibility, and notification behavior on supported API levels.

## Build

Install JDK 17 and an Android SDK with `platforms;android-36`, platform tools, and
Build Tools 35.0.0 (AGP's default). Set JAVA_HOME and ANDROID_HOME, or put the local
SDK path in untracked `local.properties`. Then run:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
```

Initial verification: the command was run and exited 1 before Gradle started:
`JAVA_HOME is not set and no 'java' command could be found in your PATH.`
Consequently Kotlin compilation, dependency resolution, Android lint, and runtime
behavior were not verified during initial setup. The UI foundation's local build
attempt has the same Java blocker. GitHub Actions now runs the same Gradle tasks
for each push to main and pull request; see the run attached to the current commit.
XML files parse successfully. The official
Gradle 8.13 wrapper JAR checksum and distribution checksum were verified.
The UI foundation and debug previews compiled in CI. The first lint run exposed
an API-27 navigation-bar attribute in the original API-26 base theme; this redundant
attribute was removed because runtime edge-to-edge setup owns system bar appearance.

## Compose previews

Open `app/src/debug/java/com/focusbuddy/ui/preview/FoundationPreviews.kt` in Android
Studio with the debug variant selected. Previews cover all five destinations,
light/dark appearances, 320dp large-text layouts, phone/tablet navigation, the
component gallery, a bottom sheet, and a confirmation dialog. The task/reminder
fixtures live only in this debug file and cannot be imported by production code.
The production Today entry point reads Room-backed state; it never reads sample data.
The gallery supports interactive completion controls; the navigation and profile
previews also support interactive UI changes without constructing a ViewModel.

Rendering previews and testing TalkBack, touch behavior, system bars, and animations
on an emulator or physical device remain part of visual/device verification.

## Dependency provenance

Versions are pinned stable releases, not an assertion that these are the newest:

- [AGP 8.13.2 / Gradle 8.13 / JDK 17 / supported SDK levels](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Kotlin 2.2.21](https://kotlinlang.org/docs/releases.html); Compose compiler plugin matches Kotlin.
- [Compose BOM 2025.08.00](https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html)
- [Activity Compose 1.11.0](https://developer.android.com/jetpack/androidx/releases/activity#1.11.0)
- [Lifecycle 2.9.4](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.4)
- [Coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2)

- [Room 2.8.5](https://developer.android.com/jetpack/androidx/releases/room)
- [DataStore 1.2.1](https://developer.android.com/jetpack/androidx/releases/datastore)
- [KSP 2.2.21-2.0.5](https://github.com/google/ksp/releases/tag/2.2.21-2.0.5), matching the Kotlin compiler line
- [Robolectric 4.16.1](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.16.1)
- [AndroidX Test Core 1.7.0](https://developer.android.com/jetpack/androidx/releases/test)

Dependency downloads require a connection during setup; the application itself
has no network requirement.

## Delivery workflow

After each completed feature, run the relevant available checks, commit its
changes, and push to `https://github.com/prince-1104/FocusBuddy.git`. Report any
blocked checks explicitly; never describe an unverified build as passing. Keep
secrets, machine-specific configuration, and generated build files out of commits.
