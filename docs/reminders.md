# Local reminder delivery

Room is the source of truth. `domain/reminders` contains occurrence identities,
planning and scheduler contracts; `data/reminders` contains the durable coordinator
and agenda projection; `platform/reminders` owns AlarmManager, notifications,
PendingIntents, broadcasts and JobScheduler. `ui/reminders` contains permission
explanations and the dedicated mascot reminder screen. Constructor injection
supplies a clock, alarm gateway and notification gateway for deterministic tests.

## Scheduling and recovery

Each occurrence has a stable UUID identity derived from reminder ID, trigger and
kind. Snoozes get a separate, durable child identity; repeated action delivery does
not create another snooze. Snoozing leaves the original recurring schedule intact.
The native notification provides Complete, Snooze 10 min and Open. The reminder
screen offers 5, 10, 15 and 30 minute snoozes, rescheduling and task/reminder details.
Completion of a standalone recurring reminder consumes only that occurrence;
completion of an attached task disables its reminders transactionally.

Only the next pending normal occurrence per reminder is registered with Android,
plus pending snoozes. A 35-day recurrence planning horizon is replenished after
delivery, on startup and by persisted periodic work. A nonrecurring reminder beyond
the horizon can be registered immediately. This limits outstanding system alarms.
The inherited recurrence implementation supports the date-based rules documented
in [Explore](explore.md); it is not a general RFC 5545 engine.

An independent alarm journal survives task/reminder deletion so reconciliation can
cancel native PendingIntents even after foreign-key cascades remove the ledger.
Boot generation, fingerprint, trigger and scheduling mode detect obsolete native
registrations. Alarm identities and notification tags remain distinct across
recurrence and snooze. A receiver revalidates the source record before delivery.
Editing, deletion, disabling and completion commit Room first, then request both
immediate application work and a durable reconciliation job. Saves cannot be
reported as failed merely because Android scheduling was temporarily unavailable.

Recovery runs after boot (credential unlock), app replacement, date/time/time-zone
changes and exact-access grant; it also runs at startup/resume and after permission
screen changes. Persisted periodic jobs and a 12-hour inexact maintenance alarm
provide additional recovery opportunities. Jobs are subject to Android scheduling
limits. Recomposition does not schedule alarms.

## Permissions and presentation

The reminder settings entry opens a contextual explanation; notifications are not
requested on first launch. Android 13+ uses POST_NOTIFICATIONS. The exact-alarm
settings flow uses SCHEDULE_EXACT_ALARM and `canScheduleExactAlarms()`; denial or a
SecurityException falls back to `setAndAllowWhileIdle`. Exact access is optional.
Revocation and notification denial cancel or rebuild derived state safely. Blocked
notifications, failed registrations and retry controls are visible in reminder
settings. Android channel choices can override app sound/vibration preferences.
Four sound/vibration combinations use separate channels only as needed, with one
default channel created at startup; custom sound URIs receive distinct channels.

Notification taps directly open a dedicated Activity. Receivers never launch an
Activity automatically. There is no full-screen intent, overlay, foreground
service, network dependency or background-activity-launch opt-in.

See Android's [alarm guidance](https://developer.android.com/develop/background-work/services/alarms),
[notification permission guidance](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
and [background activity restrictions](https://developer.android.com/guide/components/activities/secure-bal).

## Delivery policy and limitations

- No guaranteed exact delivery: Doze, battery restrictions, OEM task killers,
  alarm quotas, denied permissions and channel settings may delay or suppress alerts.
- Force-stop prevents recovery until the user launches the app again. Recovery
  after reboot requires unlocking credential-protected storage. A killed process
  is different from force-stop; OS alarms can restart a receiver process.
- Previously registered alarms may deliver up to two hours late. Older alarms,
  new past reminders and alerts that became due while permission was denied expire
  without catch-up notifications. The editor explains past trigger behavior.
- A durable claim is committed before notification posting to favor at-most-once
  delivery. A crash between that commit and posting can lose that alert. Room and
  NotificationManager cannot participate in one atomic transaction.
- A schedule edit or cancellation racing notification posting is rechecked and
  reconciled, but OS notification posting and database changes are not atomic.
- Ledger/journal tables currently have no archival cleanup policy. Long-lived
  installations can accumulate occurrence tombstones; add retention maintenance
  before large-scale recurrence use. Never prune pending snoozes or uncanceled
  native journal identities.

## Database migration

Schema 4 adds `reminder_occurrences` and `alarm_journal`; 3→4 preserves all existing
records and initializes empty derived scheduling state. Source reminders are
reconciled on startup. The exported schema and migration tests cover the supported
1→2→3→4 path. There is no destructive fallback. Alarms from older releases do not
need migration because those releases did not schedule native alarms.

## Verification

JVM tests use real Room SQLite via Robolectric, injected clocks and fake platform
gateways. They cover durable identities, duplicate callbacks, edits/deletes,
completion, snooze recurrence, permission transitions, exact/inexact modes, past
alerts, recovery generations, process/database reopening, delivery-aware agenda
counts and concurrent durable claims. Separate Robolectric adapter tests inspect
PendingIntents, channels, native notifications and recovery jobs. These are JVM
tests, not emulator or physical-device evidence.

`ReminderDeviceTest` exercises real AlarmManager and NotificationManager without
an Activity. `ReminderProcessDeathTest` has host-driven seed/verify/cleanup phases.
On an explicitly selected **disposable API 33+ device**, run:

```powershell
.\scripts\verify-reminders-device.ps1 -Serial emulator-5554
```

The host script changes this app's permission/app-op state, runs denied/fallback
and real-delivery tests, then seeds an alarm, uses `am kill` (not force-stop),
asserts that the process is absent before delivery, waits and verifies exactly one
notification. It removes its fixtures and resets the exact-alarm app-op to default.
It leaves notification permission granted. Read its output for skipped assumptions;
a skipped test is not verification. Shell grants do not validate the human system
permission flow.

Local result on 20 October 2026: app and instrumentation APK compilation succeeded;
117 JVM tests passed with zero failures or skips; lint reported zero errors and 25
warnings (dependency updates, existing UI/resource items and URI KTX suggestions).
The connected instrumentation task was attempted and failed with **No connected
devices**. Neither emulator nor physical-device notification delivery was verified.

Outstanding device matrix: permission dialogs/settings, tapping all notification
actions, reboot/unlock before a future trigger, time-zone/time edits, denied then
revoked exact access, channel blocking, battery saver/Doze, force-stop/relaunch,
TalkBack/large fonts and a real manufacturer's power management. No emulator or
physical device was connected during development; device results must be recorded
separately before declaring delivery production-validated.

## Clock picker and reminder recovery — 21 October 2026

Version 0.1.1 (code 2) adds an adjustable analog clock to the shared time field,
with hour/minute selection and a keyboard toggle. Task start/end times and custom
reminder times use the same control, honoring 12/24-hour preferences. Cancel leaves
the draft unchanged; picker state survives normal saved-state restoration.

A deterministic regression reproduced this delivery defect: an OS registration
failure persisted `alarmMode=Error`, but recovery just after the due time expired
that occurrence instead of retrying it. Previously attempted, still-active Error
rows now retry within the existing two-hour lateness bound. Newly entered past
alerts, permission-blocked alerts and alerts beyond that bound still expire.
Editing, completion, deletion and fingerprint checks continue to prevent obsolete
alerts from delivering.

Receiver errors/timeouts before a delivery claim now request forced recovery.
Without it, the registration journal could still describe a consumed OS alarm as
installed, and normal reconciliation could skip reinstalling it. Forced recovery
reinstalls still-pending valid occurrences within the lateness bound. A failure
after the durable delivery claim still has the documented at-most-once crash
window. Receiver errors are logged locally under `FocusBuddyReminders` for ADB
diagnosis; no analytics or remote reporting was added.

After successfully saving enabled alerts, the editor offers contextual setup if
notifications or precise-alarm access are unavailable. Notification permission and
exact access are reported separately. The permission screen also lists blocked
reminder channels and links to app info for battery/background settings. Android
channel choices are preserved. A task time alone does not create an alert; the
editor explicitly points to At event/custom alerts and Profile reminder defaults.

The user reports a Realme P2 Pro with notifications enabled, precise timing allowed
and a future alert added. That device-specific failure has **not** been reproduced
on the phone: it is not connected for ADB inspection. The retry defect above is a
confirmed code defect, not a confirmed diagnosis of that report.

For this phone, check the individual reminder channels (not only the app-wide
notification switch). In Android app info, review Battery usage/App battery
management and Allow background activity/auto-launch where offered. Labels vary
by realme UI version. [Official Realme notification troubleshooting](https://www.realme.com/bd/support/kw/doc/2134413)
describes these controls. Do not clear app data or uninstall as a troubleshooting
shortcut; that deletes local records. After changing settings, reopen FocusBuddy
and test a new enabled alert several minutes in the future with the screen off.
Exact access does not override manufacturer battery restrictions or channel choices.

Host integration tests send alarm PendingIntents to the actual production receiver
and inspect NotificationManager output without opening an Activity. AGP's unit-test
manifest omits app receivers, so the host harness explicitly registers the receiver;
the production manifest declaration is inspected separately. This is stronger than
fake-gateway tests but is still not emulator or physical-device delivery evidence.
See the latest verification record in [installation](installation.md).
