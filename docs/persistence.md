# Offline persistence

## Ownership and boundaries

`FocusBuddyApplication` owns one `AppContainer`. The container lazily constructs a
single Room database, repository implementations, a device-time-zone source, and
the process's single Preferences DataStore. Constructor injection keeps repositories
testable without a DI framework. There is no backend, network permission, login,
seed database, or production sample record.

Domain records and repository interfaces are Android-independent. Room annotations,
DAOs, converters, and relationships stay under `data/local`; SQL access stays behind
the implementations under `data/repository`. Composables receive display state and
callbacks. `AppViewModel` observes the preference repository through immutable
`AppUiState`. Today now consumes persisted agenda snapshots through its own
screen ViewModel; the remaining feature screens are scaffolds.

## Records

| Storage | Record | Relationships / representation |
| --- | --- | --- |
| Room `tasks` | Task | UUID; required title; optional description; explicit local date; optional start/end instants; all-day; named priority; completion; category; recurrence rule; created/updated instants |
| Room `reminders` | Reminder | UUID; optional task; optional title/description (title required standalone); trigger instant; enabled; recurrence; sound URI/enabled and vibration; nullable snoozed-until instant |
| Room `categories` | Category | UUID, name, ARGB color, stable icon key |
| Room `subtasks` | Subtask | UUID, required task, title, completion, nonnegative sort order |
| Room `focus_sessions` | FocusSession | UUID, optional task, start/end instants, accumulated duration seconds, completion |
| Preferences DataStore | MascotSettings | Selected mascot, personality, supported ARGB palette and selected color, theme, animation and sound preferences |

UUIDs and enums are stored as stable strings, local dates as epoch-day integers,
timestamps as UTC epoch milliseconds, and colors as unsigned ARGB values in Longs.
Timestamp precision is milliseconds. Focus duration represents accumulated active
time (excluding pauses), independently of wall-clock start/end time.

Preference defaults describe the app's actual initial configuration. They do not
create any task, reminder, category, or focus session. Existing `appearance` /
`theme_mode` SharedPreferences migrate once to DataStore; existing DataStore values
win. Preference read failures propagate; the ViewModel presents a retry message
instead of silently deleting or resetting the file.

## Repository behavior

- `TaskRepository.create` atomically inserts the task, its reminders, and subtasks.
  Related IDs must match the parent. A failure rolls back the whole aggregate.
- `TaskRepository.update` preserves creation time, updates modification time using
  an injected Clock, and handles completion in the same transaction. `setCompleted`
  also disables **every** attached reminder, including recurring and snoozed records,
  and clears their snoozes. Undo completion does not re-enable old alerts.
- `ReminderRepository` supports standalone and attached records. Enabled reminders
  cannot be created or re-enabled for completed tasks. Parent checks and reminder
  writes share a transaction with task completion/deletion, preventing races.
  Snooze preserves the original trigger and records a durable effective trigger.
- Task deletion cascades reminders and subtasks through foreign keys. Focus history
  remains, with a null task association. Category deletion clears task category IDs.
- Aggregate updates use `UPDATE` and inserts use `ABORT`, never SQLite `REPLACE`,
  which could silently cascade-delete child records.
- Replacing subtasks is transactional and deterministic ordering is sort order then
  UUID. Task and reminder relations are read inside Room transactions.
- Flow APIs observe persisted data. Calendar snapshots read task counts and alerts
  in one transaction rather than combining independently timed query results.

Indexes cover task local-date/completion queries, task category lookups, reminder
task relations, enabled trigger/snooze searches, subtask ordering, and focus history.
The database is file-backed, app-private, and uses WAL; main-thread queries and
destructive migration fallbacks are not enabled.

## Calendar and recurrence semantics

`CalendarRepository.observeMonth` returns every date, including explicit zeroes.
Task counts include completed and incomplete persisted tasks, placed by their
explicit scheduled local date. Alert counts include enabled reminder records whose
effective trigger falls on that local date, excluding completed-task attachments.
Multiple reminders on one task count separately. A snoozed alert counts once, at
its snoozed-until instant. These are separate counts, not a task/reminder join count.

Month boundaries use `atStartOfDay(zone)` and the next month's exclusive boundary,
not fixed 24-hour arithmetic. `DeviceTimeZoneSource` reads `ZoneId.systemDefault()`
and reacts to the protected timezone-change broadcast, restarting the month query
without requiring a database write. Task dates remain unchanged when the device
timezone changes; reminder dates can move.

Recurrence rules remain stored on their series records. The domain calendar engine
now projects date-based daily, weekly, monthly, and yearly occurrences for visible
ranges; no generated rows or seeded data are written. See [Explore](explore.md) for
supported rule fields, timezone policies, series semantics, and limitations.

## Schema and migrations

There was **no shipped Room database** before this phase. New installations now create
schema version 3 directly. The task-editor phase adds an additive v2-to-v3 migration
for notes and relative reminder metadata; see [task experience](task-experience.md). `app/schemas/v1.sql` is an explicit, pre-release version-1
baseline; `MIGRATION_1_2` adds nullable `reminders.snoozedUntil` and its enabled/snooze
index without modifying existing records. Migration tests open a real version-1
SQLite file, apply the migration through Room, and exercise Room's complete target
schema validation. A deliberately broken migration must fail validation, and an
unknown newer version must fail rather than erase data.

Room exports the authoritative version-2 JSON under `app/schemas`. Review and commit
every future exported schema with its migration and preservation tests. The v1 SQL
fixture is not a historical release claim; there is no legacy task database or
older app data format to import. No downgrade path is supported.

## Verification and limits

The unit suite uses Robolectric (API 35) and real file-backed Room/SQLite and
DataStore files, not mocked DAOs or fake production repositories. It covers CRUD,
completion and deletion rules, aggregate rollback, FK behavior, multiple reminders,
standalone reminders, snooze, Flow emissions, file close/reopen persistence, theme
migration, month boundaries, DST, leap years, and timezone-change emissions.

Run:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

On Windows, `scripts/verify.ps1` runs the same checks and detects ignored portable
JDK/SDK installations under `.tooling` when environment variables are unset. It
sets variables for the current process only; no system configuration is changed.

The original Windows environment lacked Java/Android tooling; ignored portable
JDK/SDK tools now support local verification through `scripts/verify.ps1`. The data-layer debug build,
31 unit tests, and lint passed in [CI](https://github.com/prince-1104/FocusBuddy/actions/runs/39279904092).
CI preserves generated schemas, JUnit XML,
test reports, and lint reports as `persistence-verification` artifacts.

File close/reopen tests simulate repository/process recreation; they do not replace
a physical device reboot or power-loss test. Actual notification delivery, OS alarm
cancellation/rescheduling, boot recovery, permission handling, and delivery reconciliation
are not implemented here. There are no previously scheduled FocusBuddy
alarms in the UI foundation. When delivery is added it must reconcile persisted
enabled state after reboot and re-check that state before delivering an alert.
Sound URI access permissions will also need to be persisted when a sound picker is
implemented. Storage is single-process and is not encrypted by an app-specific key;
it relies on Android's app sandbox/device protection. App cloud backup stays disabled.
