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
