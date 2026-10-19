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
- Today opens by default. Explore is a clearly labeled placeholder, without a
  calendar implementation yet. Destination state uses a ViewModel, StateFlow,
  lifecycle-aware collection, and SavedStateHandle.
- Warm neutral / moss theme with light and dark palettes. This is the foundation,
  not the finished mascot or product visual design.
- No network or notification permissions. System cloud backup is excluded.
- No DI framework: use constructor injection and a small application-owned
  dependency container when persistence introduces shared dependencies.
- Room, KSP, repositories, task models, and notification components are deliberately
  deferred to the first persistence slice; no fake or in-memory task store is shipped.

## Structure and boundaries

Current files:

```text
gradle/                         Version catalog and verified Gradle wrapper
app/build.gradle.kts            Single Android application module
app/src/main/AndroidManifest.xml
app/src/main/java/com/focusbuddy/
  MainActivity.kt               Android entry point; sets Compose content
  ui/
    AppViewModel.kt             Saved destination state
    FocusBuddyApp.kt            Today/Explore foundation shell
    theme/Theme.kt              Product color foundation
app/src/main/res/               Strings, platform theme, backup rules
```

Proposed packages to add as features need them (not empty directories):

```text
com.focusbuddy/
  app/                         Application-owned dependency container
  ui/today/                    Today screen, state, ViewModel
  ui/explore/                  Custom month grid, selected-day agenda, ViewModel
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
   ViewModel state, and accessible empty/error states. Develop the mascot and
   product typography/components here.
4. Build Explore's custom full-month grid and selected-day agenda. Every date
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
behavior have **not** been verified. XML files parse successfully. The official
Gradle 8.13 wrapper JAR checksum and distribution checksum were verified.

## Dependency provenance

Versions are pinned stable releases, not an assertion that these are the newest:

- [AGP 8.13.2 / Gradle 8.13 / JDK 17 / supported SDK levels](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Kotlin 2.2.21](https://kotlinlang.org/docs/releases.html); Compose compiler plugin matches Kotlin.
- [Compose BOM 2025.08.00](https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html)
- [Activity Compose 1.11.0](https://developer.android.com/jetpack/androidx/releases/activity#1.11.0)
- [Lifecycle 2.9.4](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.4)
- [Coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2)

Room/KSP versions will be verified together when persistence is introduced.
Dependency downloads require a connection during setup; the application itself
has no network requirement.

## Delivery workflow

After each completed feature, run the relevant available checks, commit its
changes, and push to `https://github.com/prince-1104/FocusBuddy.git`. Report any
blocked checks explicitly; never describe an unverified build as passing. Keep
secrets, machine-specific configuration, and generated build files out of commits.
