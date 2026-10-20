# Personal installation and release signing

## Install the debug APK

Use the prerequisites and PowerShell commands in [README](../README.md). From
`D:\Hopper\projects\FocusBuddy`:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
Test-Path .\app\build\outputs\apk\debug\app-debug.apk
adb devices -l
.\gradlew.bat installDebug
adb shell am start -W -n com.focusbuddy/.MainActivity
```

Use a trusted USB cable, enable USB debugging, unlock the phone and accept its RSA
prompt. `unauthorized` means approval is still needed; `offline` means reconnect or
restart ADB. With several devices, use `adb -s SERIAL install -r
app/build/outputs/apk/debug/app-debug.apk` and `adb -s SERIAL shell am start -W -n
com.focusbuddy/.MainActivity` to select one explicitly. Do not install test fixtures
on a phone containing valuable FocusBuddy data.

Alternatively transfer the APK, open it with the phone's file manager and grant
that source permission to install unknown apps when prompted. Disable that source
permission afterward if desired. No external-storage permission is needed by the
app. Keep downloaded APKs from this project distinguishable from untrusted copies;
`Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256`
calculates a fingerprint for checking a transfer.

Debug is automatically signed by the local development key. It is debuggable;
release is explicitly non-debuggable and has no signing key configured in Git.
Both use `com.focusbuddy`. A different debug key or a personal release key cannot
update the existing debug installation. Preserve needed local records before
changing signing identities; uninstalling or clearing storage erases them. There
is no export or cloud restore in v1.

## Generate a signed personal release in Android Studio

No signing key or password has been generated for you. The Gradle command
`assembleRelease` creates **app-release-unsigned.apk**, which cannot be installed.
Use Android Studio locally:

1. Open and sync the project, run tests and select **Build > Generate Signed
   Bundle / APK** (wording can vary slightly by IDE version).
2. Choose **APK**, then module **app**.
3. Select an existing personal keystore or choose **Create new**. Save it outside
   the repository and build folders. Choose a unique alias, strong password and
   long validity (at least 25 years). Enter certificate information deliberately:
   certificate metadata is included in the APK.
4. Enter credentials locally in the wizard; do not put them in source files,
   `build.gradle.kts`, screenshots, chat, command history or committed properties.
   Decline password retention on shared machines.
5. Choose the **release** variant and a destination folder, then finish. Keep the
   signing schemes recommended by the wizard for the supported Android versions.
6. Use the wizard's **Locate** link for the exact signed APK. Its selected output
   differs from the unsigned CLI artifact. Verify it using Build Tools:

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs 'C:\chosen-output\app-release.apk'
```

Verification must exit successfully and the signer fingerprint must match your
personal key. Do not label a file signed just because its filename says release.
Install and run the manual checklist below on the signed artifact too.
For future updates, increment `versionCode` and set an appropriate `versionName`
in `app/build.gradle.kts`, then use the **same signing key** and package ID.
[Android's signing guide](https://developer.android.com/studio/publish/app-signing)
explains the signing wizard and certificate/update requirements.

## Preserve the signing identity

Keep the keystore outside Git and outside temporary/build folders. Store at least
two encrypted backups on separate trusted media/locations; keep alias, passwords,
certificate fingerprint and recovery instructions in a password manager with
appropriate access controls. Test that a backup can be opened before relying on
it. Keep passwords separate from unencrypted keystore copies. Never commit private
keys: `.jks`, `.keystore`, `keystore.properties` and `signing.properties` are ignored
as a guard, but other filenames still require care. The Android debug key is not
an appropriate permanent personal release identity. Losing a self-managed release
key prevents normal updates of its installed app; this project has no cloud key
recovery service.

## Reminder setup and offline behavior

First launch opens Today without a login. In Profile, choose theme, time format,
week start and reminder defaults; Mascot Studio saves bundled companion choices.
Use Profile > Notification delivery or the contextual editor prompt to enable
notifications on Android 13+. Check the app's notification channels as well.
Allow Alarms & reminders in system settings when precise user-selected times are
needed. The app checks exact-alarm access and uses inexact fallback without it.
Declining permissions must leave tasks and calendars usable.

All app content and original mascot artwork are bundled; Room/DataStore operate
locally. No INTERNET permission or runtime credentials are required. Build-time
dependency downloads are separate. Test first launch and normal CRUD with airplane
mode enabled; compile and source inspection alone do not prove device behavior.
Notification sounds use system resources; channel choices and Do Not Disturb may
silence them. There are no downloadable sound/animation assets.

Delivery may be delayed or suppressed by Doze, battery saver, per-app battery
restrictions, manufacturer task killers, force-stop, alarm quotas, blocked channels
or denied permissions. Review app battery settings only when necessary; do not
assume changing them guarantees delivery. Unlock after reboot for encrypted local
storage recovery; reopen after force-stop. Very old/past reminders expire instead
of replaying indefinitely. Recurring edits/completion apply to the stored series.
See [delivery limits](reminders.md) and [QA evidence](qa.md) for the supported
recurrence subset, claim/post crash window and occurrence-history retention limit.
The focus timer persists its state and catches up when reopened; it does not
promise a background focus-completion notification.

## Troubleshooting

| Symptom | Check / action |
| --- | --- |
| Java missing or incompatible JVM | Set JAVA_HOME and the IDE's Gradle JDK to JDK 17, reopen terminal, run `java -version` and `.\gradlew.bat --version`. |
| SDK location / licenses error | Install Platform 36 and Build Tools 35.0.0 in SDK Manager, accept licenses, set ANDROID_HOME or local.properties. |
| Dependency resolution fails | Initial setup requires access to Google Maven, Maven Central and Gradle distribution hosts. Check proxy/network; `--offline` only works once dependencies are cached. No app API key fixes this. |
| Memory pressure | Append `--max-workers=2`, close unneeded emulators/IDEs; do not skip failing tests. |
| No devices / driver problem | Check `adb devices -l`, USB mode/cable and Windows OEM USB driver; authorize debugging or start an AVD. |
| Multiple devices | Select one serial explicitly with `adb -s SERIAL ...`. |
| INSTALL_FAILED_UPDATE_INCOMPATIBLE | Existing package has another signing certificate. Reuse its key; do not erase local data as an automatic fix. |
| INSTALL_FAILED_VERSION_DOWNGRADE | Build an update with a higher versionCode using the existing key. |
| Parse/install failure | Android API must be 26+, verify APK transfer hash, available space and signing. Unsigned release is not installable. |
| Missing alerts | Check notification permission/channel, exact access and battery restrictions; reopen after force-stop. Test a future alert rather than an already expired trigger. |
| Wrong calendar date | Check device date/time/time zone and week-start setting; reminders count by trigger date, independently of task date. |
| Crash or unexpected state | Capture `adb logcat -d -b crash` and reproduction details. Logs can contain personal titles: redact before sharing. Keep data intact while diagnosing. |

## Manual device smoke-test checklist

Record APK hash, device/model, Android version, build variant, signing fingerprint
and permission state. This checklist is **not marked passed** until run on hardware
or an emulator; repeat critical notification checks on your actual phone.

- [ ] Install, see the FocusBuddy icon/name, launch Today without credentials.
- [ ] Enable airplane mode; relaunch, create two tasks for the same day, edit,
      duplicate, complete, reopen and delete one. Restart app; retained data persists.
- [ ] In Explore select another date, create a task, check its task count and Today
      synchronization; edit its date and check both affected dates.
- [ ] Create standalone and multiple attached reminders with future times. Set an
      attached reminder on another date; verify that date's separate bell count.
- [ ] With notifications denied, verify safe explanation and usable task CRUD.
      Enable notifications, deny exact access and verify the inexact explanation.
      Grant exact access, then revoke it and reopen to check recovery/fallback.
- [ ] Schedule an alert a few minutes ahead, leave the app/background or lock the
      phone. Check one notification, correct title/time, Open to the correct ID,
      Complete, Snooze and Reschedule. Test a canceled/deleted/complete task's alert
      does not arrive. Check recurrence and that snoozing retains the base schedule.
- [ ] Reboot/unlock before a future reminder; test with battery saver/Doze on your
      phone. Record delay rather than assuming exact delivery. Force-stop/relaunch
      separately; a forced-stopped app cannot be expected to deliver until reopened.
- [ ] Change mascot/accent/personality, animation and sound settings; preview and
      save. Switch light/dark/system, week start and time format; relaunch and verify.
- [ ] Start, pause, navigate, rotate/recreate, resume and stop focus; verify history
      and countdown. Background it and return after its deadline to check catch-up.
- [ ] Scroll every screen on compact and larger layouts and large text. Check bottom
      navigation/system bars, dialogs, touch targets and TalkBack labels.
- [ ] On a disposable data set only: cancel Clear All Data first, verify retention,
      then confirm and verify local records/preferences and scheduled alerts clear.

## Packaging verification record

20 October 2026, Windows, JDK 17, Android SDK 36 / Build Tools 35.0.0:

| Check | Actual result |
| --- | --- |
| Host tests (explicit rerun) | 156 tests passed; zero failures, errors or skips. Includes nine Compose host UI tests. |
| Debug build | `assembleDebug` successful; APK exists, 13,482,892 bytes. |
| Release build | `assembleRelease` successful; unsigned APK only, non-debuggable. Signature verification correctly rejects this unsigned artifact. |
| Lint | Zero errors, 26 non-blocking warnings; see [QA](qa.md) for warning categories. |
| Full packaging build | `testDebugUnitTest --rerun assembleDebug assembleRelease lintDebug --max-workers=2` successful in 4m 52s. |
| APK identity | `com.focusbuddy`, versionCode 1, versionName 0.1.0, min SDK 26, target 36; FocusBuddy label and MainActivity launcher confirmed with aapt2. |
| Signing | Debug APK verifies with APK Signature Scheme v2; certificate CN=Android Debug. No personal release signing key was created or stored. |
| Manifest/resources | Packaged permissions exclude INTERNET; private alarm/action receivers, exported recovery receiver and permission-protected job verified. Launcher/reminder icons and compiled MascotKt are present in the APK. |
| Installation attempt | `installDebug` failed with `DeviceException: No connected devices!`; ADB listed no devices. |
| Launch / offline smoke test | Not run on an emulator or physical device. Host Activity tests passed; source/dependency inspection found no runtime network or credential dependency. These do not verify on-device offline operation or alert delivery. |

Exact debug APK:
`D:\Hopper\projects\FocusBuddy\app\build\outputs\apk\debug\app-debug.apk`.

SHA-256:
`7D693D55A9ECD9EE42D4BF2CACF79D45FF06C9A9B319A411A0A89B8BA8EC53FC`.

Debug certificate SHA-256:
`f9241606d85baec2fc02777191328e90c31597cc66e5d6124a3ff6955a9c9c2d`.
This identifies the local development certificate, not a personal release key.

Local command logs: ignored `.tooling/packaging-verification.log` and
`.tooling/packaging-install.log`. HTML/JUnit evidence lives under
`app/build/reports/` and `app/build/test-results/`. The APK is an ignored local
build output, not an attachment in the Git repository. The manual checklist above
and real permission/background notification checks remain pending on a device.
