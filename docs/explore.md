# Explore calendar and agenda

Explore reads the Room-backed CalendarRepository through ExploreViewModel. No
runtime demonstration records or counts are generated. Constructor injection uses
the existing application container; dependency versions and schema version 2 stay
unchanged. Week-start preferences use an additive DataStore key, so no SQL
migration is needed.

## Interaction and presentation

- Seven columns, locale weekday labels, blank cells at month boundaries, previous
  and next month controls, and a Today shortcut. Browsing months selects the same
  day number clamped to the target month. Date selection is shared with Today in
  the graph SavedStateHandle, including process recreation.
- Selected dates use indigo fill. Current dates have a border and underlined number;
  screen-reader descriptions announce today, selection, full date, and both counts.
- A numeric task indicator and separate drawn bell/alert count remain distinct.
  Zero indicators are hidden; visible counts above 99 abbreviate to 99+, while
  accessibility descriptions retain exact totals. Loading/errors never invent zeroes.
- Week start can follow the device locale or any weekday. The preference is saved
  atomically without overwriting concurrent theme or mascot changes.
- The agenda shows all-day tasks first, then interleaves timed tasks, standalone
  alerts, and attached alerts chronologically, with stable occurrence keys. Category
  color, priority, completion, and reminder association remain visible.
- Selected-day totals include completed and remaining tasks plus active alerts.
  Add opens the existing real task editor with the selected date. Task rows open
  editing; repeating rows explicitly open the series editor. Reminder rows open a
  details sheet with actual occurrence time, task association, sound/vibration,
  snooze and recurrence information, and an explicit disable action (whole series
  for a recurring reminder). This is persisted and immediately updates counts.
- Today has an Open month and agenda shortcut. Switching back retains the date.
  Empty, loading, repository error/retry, and write-error states are implemented.

## Data and recurrence rules

One invalidation-driven batch read loads range task candidates, enabled reminder
candidates, missing associated tasks, and categories inside a transaction. No query
is issued per calendar cell. Projection runs off the main thread. Changes to tasks,
reminders, categories, and device timezone invalidate the relevant view. The Today
29-day selector uses the same projection, including recurring selected-day tasks.

Unique task totals use (task UUID, local occurrence date). Each enabled alert uses
(reminder UUID, actual trigger instant), independent of the task's scheduled date.
Completed-task attachments, disabled alerts, and missing task attachments are
excluded. A task can have many alerts. Snooze moves the seed alert once; it does
not shift every future series occurrence or double-count a coincident trigger.
Task dates remain explicit local dates. Reminder instants use the current device
zone, with midnight-to-next-midnight boundaries rather than fixed 24-hour days.

The date-based RRULE subset supports DAILY, WEEKLY, MONTHLY, YEARLY, INTERVAL,
COUNT, inclusive UNTIL, BYDAY (including monthly/yearly ordinals), BYMONTHDAY
(including negative days), BYMONTH, and WKST. Invalid calendar days are skipped
(e.g. a monthly 31st or yearly February 29). Duplicate selectors never multiply a
task's daily count. UTC UNTIL on reminders is filtered against exact instants after
local date expansion. Supported rule concepts are based on
[RFC 5545 section 3.3.10](https://www.rfc-editor.org/rfc/rfc5545#section-3.3.10).

## Explicit limitations

This is not a general iCalendar importer: sub-day frequencies, BYSETPOS,
BYYEARDAY, BYWEEKNO, RDATE/EXDATE, and occurrence exceptions are not supported.
Unsupported fields fail into the calendar error/retry state rather than producing
plausible but wrong counts. Existing editors preserve recurrence rules; a recurrence
creation/configuration UI is separate work. Edits, deletion, and completion operate
on the stored task series, not individual occurrences. Reopening a task does not
restore disabled reminders.

Recurring wall-clock reminders use the current device zone. Subsequent DST gaps
shift forward using java.time's zone resolution; overlaps use the earlier offset.
The seed instant is preserved exactly, including a second overlap occurrence.
A stored rule has no fixed original timezone. Enabled undelivered overdue alerts
remain pending until disabled; no delivered/dismissed occurrence ledger or Android
notification scheduling exists yet. Delivery must eventually reconcile those states
and per-occurrence snoozes before presenting historical delivery totals.

## Verification

Tests cover all month alignments and all seven week starts across a leap cycle,
leap years, independent/empty task and alert dates, attached alerts on another day,
recurrence intervals/count/until/selectors, DST and timezone boundaries, snoozes,
chronological agenda order, Room invalidations after edits/completion/deletion/
disabling, shared Today counts, persisted preferences, and ViewModel selection,
details/disable, error/retry, and midnight refresh. Debug-only Compose previews cover
light/dark and compact large-text layouts. Device rendering and TalkBack still need
manual verification; JVM tests and lint do not replace those checks.
