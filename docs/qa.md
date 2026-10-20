# FocusBuddy QA audit

Scope: the existing app, including the in-progress focus timer, preferences and
task discovery implementation. Feature expansion stopped for this audit. The
initial debug app and instrumentation APK compiled; the first regression run
executed 139 tests, with 138 passing and one reproducing the focus-feedback race.

## Final verification — 20 October 2026

| Check | Result |
| --- | --- |
| Debug APK, unsigned release APK, instrumentation APK | All assembled successfully. |
| `testDebugUnitTest` | 156 tests across 23 suites; zero failures, errors or skips. Includes nine native-graphics Compose UI tests on host API 35. |
| `lintDebug` | Zero errors, 26 warnings; no baseline or suppressed failure added. Warnings include dependency update suggestions, existing unused strings/KTX/modifier suggestions, and adaptive-icon configuration/monochrome guidance. |
| `connectedDebugAndroidTest` | Attempted; failed to start with `DeviceException: No connected devices!`. No instrumentation tests executed on a device. |
| Packaged release permissions | No INTERNET permission. Notification, exact-alarm, boot, wake-lock and AndroidX internal receiver permissions only. |
| Host Activity runtime logs | No unhandled AndroidRuntime/FATAL crash. Logs retain framework warnings about host overlays/software ripple rendering, predictive-back opt-in, and a redundant reconciliation-job deadline. These are not evidence about physical-device logs. |
| Visual inspection | Inspected generated compact dark/large-text calendar, paused focus and larger light calendar renders. Separate task/bell counts and compact navigation labels remain legible. |

Final Gradle invocation: `assembleDebug assembleRelease assembleDebugAndroidTest
testDebugUnitTest lintDebug --max-workers=2` (all tasks in `:app`); successful in
3m 30s. Detailed HTML/JUnit results and host screenshots are generated under
`app/build/reports/` and `app/build/test-results/`. Local command logs are in the
ignored `.tooling/qa-*.log` files. Native-library packaging previously reported
two dependencies whose symbols could not be stripped; they were packaged intact.

The final lint run initially rejected an Activity cast from `LocalContext` in the
reminder theme. It now obtains the Activity through Compose's `LocalActivity`;
the complete build, tests and lint were rerun successfully after that correction.

## Defects found and corrected

| Finding | Correction / regression evidence |
| --- | --- |
| A snooze and regular occurrence at the same instant merged into one count | Preserve occurrence identities through Explore and Today; `CalendarRepositoryTest.snoozeAndNormalOccurrenceAtSameInstantKeepSeparateCountsAndStableKeys` checks two alerts and unique agenda keys. |
| Focus completion feedback lost when the history Flow arrived later | Retain the pending session identity until its terminal history record arrives; `FocusViewModelTest.invalidDurationNeverStartsAndCompletionFeedbackSurvivesHistoryFlowLag` reproduced the original failure. |
| Task title / reminder sound edits canceled future snoozes | Rebase pending snooze fingerprints transactionally only when scheduling and attachment are unchanged. A regression checks delivery with the new title/sound and cancellation after a timing edit. |
| Discovery treated a future instant as overdue after a time-zone change | Compare the original occurrence instant; also exclude rules ending before their seed from Upcoming. `TaskDiscoveryTest` covers both. |
| Back navigation could bypass Clear All Data's busy lock | Block back while a settings/reset write is in flight. Cancel pending editor, focus, category and Studio writes before clearing. |
| Root surface and reminder system bars did not consistently follow dark mode | Explicit themed root surface and theme-aware reminder activity system-bar style; real-Activity JVM dark renders inspected. The permission entry remains in Settings and editors rather than crowding Today. |
| Large count chips and bottom-navigation labels wrapped at 320dp / 150% text | Adapt compact visual count caps while retaining complete accessible counts; keep navigation labels on one line with scalable typography. |
| Launcher icon missing; legacy backup setting implicit | Add the original bundled Mallow adaptive icon and explicit legacy backup exclusion. |

The new direct-migration test initially had a fixture error: exported tables with
no indices omit that JSON member. The fixture now treats that omission as an empty
index list; Room's migration validation and record-preservation assertions remain
unchanged. A UI test also needed to wait for Pause to render after the database
transition; its persisted-state assertions were retained.

## Functional evidence

| Area | Tests / evidence |
| --- | --- |
| Task create/edit/duplicate/complete/reopen/delete, subtasks, transactional rollback | `RepositoryTest`, `TaskEditorRepositoryTest`, `TaskExperienceViewModelTest`, `TodayViewModelTest` use real Room. |
| Repeated taps and selected Explore date creation | `MainActivityQaTest.selectedExploreDateCreationDoubleTapCountsAndEditingStaySynchronized` invokes the actual save action twice, asserts one stored task, edits its date, checks both affected counts, and opens Today. |
| Standalone / attached / multiple reminders, trigger-date counts, unrelated tasks and reminders | `CalendarRepositoryTest`, `ExploreCalendarTest`, `TaskExperienceViewModelTest`. |
| Month transitions, leap years, five/six rows, all weekday alignments and seven week starts | `ExploreCalendarTest.everyMonthBoundaryAndWeekStartOverEntireLeapCycle`, month-boundary/DST repository tests, and calendar UI tests. |
| Recurrence, snooze, cancel, completion, stale/deleted alarms and duplicate callbacks | `ReminderEngineTest`, real Room plus injected clock and fake gateways. Adapter tests inspect actual framework objects through Robolectric. |
| Permission denial and exact-access grant/revoke | Engine and Android adapter tests cover blocked notifications, denied access, fallback modes and recovery. Human system dialogs and real background delivery are not device-verified. |
| Mascot, themes, sound settings, restored previews | `MascotStudioUiTest`, preference persistence tests, Activity dark-theme recreation test. Actual sound playback is not verified. |
| Preferences, defaults and time formats | `PreferencesRepositoryTest`, `EditorDefaultsTest`; new settings survive store reopening and Studio saves; invalid stored values fall back safely. |
| Focus transitions, pauses, repeated actions, stop, background catch-up, process/database recreation, reboot interruption and deleted task association | `FocusTimerRepositoryTest` uses real Room and an injected monotonic clock; `FocusViewModelTest` covers control guards and restored inputs. |
| Focus navigation and Activity recreation | `MainActivityQaTest.focusPauseNavigationThemeAndActivityRecreationPreserveTheSession` starts, pauses, changes theme, recreates the Activity, resumes and stops, verifying persistent history. |
| Search, category, priority, completion, local-day changes and saved filters | `TaskDiscoveryTest` and real-Room `TaskDiscoveryViewModelTest`. |
| Clear All Data and confirmation | Engine test removes all source records and derived alarms; `MainActivityQaTest.clearAllRequiresConfirmationAndRemovesRecordsAndPreferences` checks Cancel retains data and Confirm removes it and resets preferences. |
| Database upgrades | Real SQLite validation for 1→5, 2→5, 3→5 and direct 4→5, preserving existing records. Broken migrations and unknown future versions fail without destructive fallback. |

## Visual and accessibility evidence

Robolectric native-graphics tests render the **real MainActivity** at 320dp and a
calendar shell at 320dp/150% text and 800dp. They exercise scrolling, five compact
navigation destinations, the tablet navigation rail, count semantics, dialogs and
selected dates. Screenshots are under `app/build/reports/qa/` and are retained by
CI. They are host renders, not screenshots from an emulator or physical device.
MainActivity QA tests also capture warning/error logs and reject an unhandled
AndroidRuntime crash. Palette and semantic inspection do not substitute for a
TalkBack session or hardware contrast/display evaluation.

## Environment and limits

ADB returned no connected devices; the SDK contains no emulator or system image.
Consequently the APK has not been exercised on an emulator or physical device in
this audit. `connectedDebugAndroidTest` must be reported separately from compiled
instrumentation tests. Device reboot/unlock, killed-process notification delivery,
OEM battery management, real permission/settings interactions, real audio,
TalkBack and additional Android API levels remain unverified.

Host tests use API 35 with JDK 17; the app compiles/targets API 36 and supports API
26+. Successful compilation is not evidence of runtime coverage at every API level.
Release output is unsigned and is not a store-ready signed release.

The focus timer derives its deadline from persisted monotonic clock state, not a
Composable loop. It catches up when observed again; it does not promise a focus
completion notification while backgrounded. Paused sessions remain paused until
resumed/stopped. Reboot interrupts a running segment and records known elapsed
time at the last transition rather than inventing time. See Android's
[monotonic clock reference](https://developer.android.com/reference/android/os/SystemClock).

Recurring task edits/completion apply to the whole stored series; the recurrence
engine supports the documented date-based subset, not all RFC 5545 rules. Delivery
still has the explicit at-most-once claim/post crash window, power-management
constraints and unbounded occurrence-history retention described in
[reminders](reminders.md). No cloud/login/payment behavior was added.
