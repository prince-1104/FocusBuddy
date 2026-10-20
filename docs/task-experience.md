# Task and reminder experience

Task cards in Today and Explore open a live Room-backed details sheet. Add task
opens a separate creation sheet with the shared selected date. The graph-owned
TodayViewModel accepts an optional date/time through beginTask and beginReminder;
normal configuration changes retain immutable form state, child UUIDs and edits.
Forms are scrollable and keyboard-safe, with a persistent Save action and visible
validation feedback. Compose date/time dialogs supplement manual
ISO-date and 24-hour-time entry. Picker visibility is saveable. Process-death draft
recovery is not implemented; committed data remains durable.

## Forms and details

Task creation/editing includes required title, description, date, all-day or start/
optional end times, category, priority, private notes, recurrence, ordered subtasks,
and multiple reminder alerts. Subtasks support title edits, completion, removal and
move-up/move-down controls before the aggregate is saved. Details also supports
individual subtask completion, progress, edit, duplication, completion/reopening,
and confirmed deletion. Today and Explore cards show subtask progress.

A duplicate opens an editable copy with new task/child IDs, incomplete subtasks,
and disabled copied alerts. The original stays unchanged. Reopening a completed
task never silently re-enables canceled reminders. Recurring-task mutations apply
to the series, as the UI states. Per-occurrence exceptions remain separate work.

Task reminder presets are at event time and 5, 10 or 30 minutes before. All-day
presets explicitly use 09:00 local time. Offset and follow-task-recurrence metadata
are persisted, so editing the task recomputes preset triggers. Custom alerts retain
independent dates, times and recurrence. Each alert has enabled, sound and vibration
preferences and a formatted trigger summary. Existing custom sound URIs are retained;
this phase exposes sound on/off, without adding a system sound-file picker.

Standalone reminder creation is a distinct action on Today and Explore, requiring
no task. Its editor includes title, description, trigger date/time, recurrence,
sound, vibration and enabled state. Enabled reminders appear on their trigger-day
agenda. Manage reminders lists all standalone records, including disabled ones,
so they can be edited, re-enabled or deleted after leaving the agenda. Reminder
rows in Today open editing; Explore provides details with Edit reminder.

## Validation and transactions

EditorValidation and SaveTask own validation and date calculations outside Compose.
They reject empty titles/subtasks, invalid dates/times, DST gaps, reverse end times
and unsupported recurrence rules. Enabled newly created, re-enabled or rescheduled
past alerts require an explicit Keep past alerts choice; disabled historical alerts
can be stored. Unchanged historical records need no new acknowledgment. Past alerts
stay pending and are never silently shifted to now or another day.

Exact original instants, including seconds and the second occurrence of an overlap,
are preserved when visible date/time fields are unchanged. New ambiguous times use
the earlier offset. Changing a trigger clears its old snooze; disabling clears snooze.
Relative recurring alerts are projected from task occurrences before subtracting the
offset, preventing a weekly BYDAY rule from putting a previous-day alert on the wrong
weekday. Device timezone changes remain observable in calendar projections.

RoomTaskRepository.updateAggregate atomically updates content and replaces reminders
and ordered subtasks. It reads current parent completion inside the transaction, so
an editor cannot undo concurrent completion or reactivate its reminders. Foreign-key
or child-insert failures roll back the parent and all child changes. Individual
subtask completion updates only that child and the parent update timestamp.

Submission marks forms busy synchronously before launching a coroutine; repeat taps
are ignored. Forms cannot be dismissed during writes. Destructive deletion and dirty
form dismissal require confirmation. A dirty sheet stays expanded while the
discard dialog is open, so Cancel retains both the form and its visible sheet. Repository errors keep the form and edits open.
All successful mutations refresh the existing observable Today/Explore data sources.

## Schema and alarm boundary

Room version 3 adds nullable tasks.notes, nullable reminders.offsetMinutes, and
reminders.followTaskRecurrence with a false default. MIGRATION_2_3 is additive and
registered alongside MIGRATION_1_2. Exported schemas 2 and 3 remain checked in. Tests
exercise both v1-through-v3 and direct v2-to-v3 upgrades without destructive fallback.

SchedulingTaskRepository and SchedulingReminderRepository request reconciliation
through ReminderScheduler only after successful database commits. Relevant task and
reminder creation, updates, completion, deletion, disabling and snooze mutations use
this port. A scheduler exception does not turn an already committed save into a
failed submission that a user might duplicate. A real platform adapter must own its
retry/reboot reconciliation policy.

Android alarm/notification delivery remains unavailable: the application explicitly
installs UnavailableReminderScheduler and the UI says alerts are saved locally.
There are no OS alarms to cancel in this version, and no new notification permissions
or misleading delivery claims. Implementing Android scheduling, permissions, reboot
recovery and durable platform retries is the recommended next phase.

## Verification

JVM/Robolectric coverage includes rich aggregate creation/editing, reordered/removed/
completed subtasks, multiple/custom/preset alerts, persistence after database reopen,
rollback on conflicting child IDs, completion during editing, relative trigger changes,
midnight recurrence boundaries, exact timestamp/snooze preservation, standalone CRUD,
disabled-reminder management, prefilled date/time, dirty form retention, duplication,
submission guards, scheduling-port calls and adapter failure, and schema migrations.
Debug-only Compose previews cover task details and standalone editors in light/dark
and compact large-text layouts. Device rendering, keyboard interactions and TalkBack
still need manual verification; the Gradle build and lint do not replace device QA.
