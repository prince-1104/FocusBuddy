# FocusBuddy

A native Kotlin / Jetpack Compose personal productivity companion. Today is the
home screen; Explore provides a custom month calendar and selected-day agenda.
Tasks, reminders, focus history and preferences are stored locally with Room and
DataStore. No backend, account, subscription, analytics, API key or runtime network
connection is required. Initial Gradle dependency downloads require internet access.

## Personal installation

From the project root in Windows PowerShell, with JDK and SDK configured:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

`installDebug` requires an authorized Android device or running emulator. Enable
Developer options and USB debugging, connect by USB, accept the device's RSA
prompt, and check `adb devices -l`. Then launch from the FocusBuddy launcher icon,
or run `adb shell am start -W -n com.focusbuddy/.MainActivity`.

The generated, debug-key-signed APK is:
`app/build/outputs/apk/debug/app-debug.apk`. On this workspace:
`D:\Hopper\projects\FocusBuddy\app\build\outputs\apk\debug\app-debug.apk`.
For manual installation, copy that APK to your phone, open it and temporarily allow
installation from that file manager when Android asks. The debug build is suitable
for personal evaluation; it is debuggable and is not a production-signed release.

Package/namespace: **com.focusbuddy**. Display name: **FocusBuddy**.
Version: **0.1.1**, version code **2** in `app/build.gradle.kts`.
Debug and release share the package, so they cannot coexist. An update needs the
same signing certificate. Do not uninstall an existing installation just to fix a
signature mismatch: uninstalling removes local data, and v1 has no export/backup.

See [installation, signing and smoke tests](docs/installation.md) before using the
app for reminders you depend on. Device testing is still outstanding.

## Windows development setup

- Android 8.0 / API 26 or newer device. Compile and target SDK are API 36.
- Android Studio Narwhal 3 Feature Drop (2025.1.3) or a newer stable version that
  supports AGP 8.13.2. Use JDK 17 for Gradle and command-line builds; a newer IDE's
  bundled JDK need not be the project's Gradle JDK.
- SDK Manager: Android SDK Platform 36, Build Tools 35.0.0, Platform-Tools and
  Command-line Tools. Optional Emulator and an API 35/36 system image for testing.
- Gradle 8.13 comes from the checked-in wrapper; do not install a global Gradle.
  Versions are pinned in `gradle/libs.versions.toml`; no automatic upgrades.

Open the project folder in Android Studio and sync Gradle. Set the Gradle JDK to
17 under Settings > Build, Execution, Deployment > Build Tools > Gradle. Accept
SDK licenses. For PowerShell (replace paths with your actual installations):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
.\gradlew.bat --version
```

Alternatively Android Studio writes the SDK path to ignored `local.properties`.
Never commit machine-specific paths, keystores or passwords. The local
`scripts/verify.ps1` helper uses configured tools, falling back to this workspace's
ignored `.tooling` JDK/SDK; those tools are not distributed in Git.

## Build and verification

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat installDebug
```

Connected tasks require a device. Use `--max-workers=2` on memory-constrained
machines. To rerun host tests even when up to date:
`.\gradlew.bat :app:testDebugUnitTest --rerun`. To use the local tool helper:
`.\scripts\verify.ps1 -Tasks @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:lintDebug', '--max-workers=2')`.

| Output | Location from repository root |
| --- | --- |
| Debug APK, automatically debug signed | `app/build/outputs/apk/debug/app-debug.apk` |
| Unsigned release from `assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Instrumentation APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Host test HTML / XML | `app/build/reports/tests/testDebugUnitTest/` / `app/build/test-results/testDebugUnitTest/` |
| Lint / host UI renders | `app/build/reports/lint-results-debug.html` / `app/build/reports/qa/` |

An unsigned release APK cannot be installed. Follow the Android Studio signing
steps in [installation](docs/installation.md); the signed wizard output is the
location you select, not necessarily the unsigned Gradle output above.

## Permissions and reminders

Allow notifications on Android 13+ when prompted. In Profile > Notification
delivery, review notification permission, channel settings and optional exact-alarm
access. Allow Alarms & reminders through the supported system settings flow if you
want precise reminders. Without that access the app uses inexact scheduling;
notification denial/channel blocking prevents visible alerts. There is no internet,
location, contacts or external-storage permission.

Alarm and action receivers are private. The exported recovery receiver handles
boot, app replacement, date/time/time-zone and exact-access changes. The recovery
job service is protected by `BIND_JOB_SERVICE`. `RECEIVE_BOOT_COMPLETED` and a
bounded `WAKE_LOCK` support recovery/delivery. System backup is excluded.

Doze, battery saver, vendor restrictions, force-stop, disabled channels and denied
permissions can suppress or delay alerts. Exact delivery is not guaranteed on all
devices. Reboot recovery requires unlocking; force-stop requires reopening the app.
Review per-app battery settings only if needed, and test on your own phone. The
focus timer catches up on return; it does not post a background completion alarm.
See [reminder behavior and device checks](docs/reminders.md) for recurrence,
late-alert, crash-window and occurrence-history limitations.

## Architecture and resources

One `app` module with constructor injection through `app/AppContainer.kt`.
UI collects screen ViewModels with StateFlow and lifecycle-aware collection;
domain rules remain separate from Room, DataStore and Android notification APIs.

```text
app/src/main/java/com/focusbuddy/
  app/                         Dependency container
  ui/                          Today, Explore, Focus, Mascot Studio, Profile/settings
  ui/components/               Reusable controls and original Canvas mascot vectors
  ui/theme/                    Ivory/lavender/indigo palettes, typography and shapes
  domain/                      Models, repository contracts, recurrence/date/focus rules
  data/local/                  Room schema, DAOs, migrations, durable focus state
  data/repository/              Transactional repositories
  data/preferences/            DataStore preferences
  data/reminders/              Durable reminder ledger and delivery coordinator
  platform/                    Android alarms, notifications, recovery and focus clock
app/src/main/res/               Bundled launcher/reminder icons, strings and platform theme
app/src/debug/                  Compose previews and isolated preview fixtures
app/src/test/                   Domain, real Room/DataStore and host Compose UI tests
app/src/androidTest/            Device notification and UI tests
app/schemas/                    Versioned Room schemas and migration fixtures
```

All three original mascot variants and message templates are bundled Kotlin code;
no asset download, Lottie/Rive file or cloud service is needed. Preview fixtures
are debug-only and do not seed production data. In Android Studio select the debug
variant and open `ui/preview/FoundationPreviews.kt` or `MascotPreviews.kt` under
`app/src/debug/java/com/focusbuddy/`.

## Documentation and verification limits

- [Personal installation, signing, troubleshooting and manual checklist](docs/installation.md)
- [QA evidence and remaining device checks](docs/qa.md)
- [Offline persistence and migrations](docs/persistence.md)
- [Today](docs/today.md), [Explore](docs/explore.md), [task editing](docs/task-experience.md)
- [Reminder delivery](docs/reminders.md), [Mascot Studio](docs/mascot-studio.md)

Host tests and native-graphics screenshots are not emulator/physical-device
verification. Check the packaging record in the installation guide for this run's
actual results. No cloud features were added for installation.

Compatibility and signing references: [AGP 8.13 requirements](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[Android Studio/AGP compatibility](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility),
[Android app signing](https://developer.android.com/studio/publish/app-signing).
After each completed feature, run available checks, commit and push to the
configured FocusBuddy repository; keep generated outputs and secrets out of Git.
