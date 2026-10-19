# Today dashboard

Today uses `TodayRoute` for lifecycle-aware collection from `TodayViewModel`.
`ObserveDashboard` projects immutable domain state from a transactional
`RoomAgendaRepository` snapshot, preferences, the selected date, and a clock/timezone
source. The screen and its children receive state and callbacks, never repositories.
No runtime task/reminder fixtures or default category records are inserted.

## Behavior

- A compact greeting/date header, optional vector companion, swipeable date strip,
  selected-day progress, grouped task cards, standalone reminders, and Add Task FAB.
- Today and Explore share a graph-level date stored in `SavedStateHandle`. Explore
  can open the selected day's production dashboard. Back to Today uses the current
  device timezone, including when restoring state after process recreation.
- Date dots show independently persisted task and enabled-reminder counts. TalkBack
  labels announce both counts. The date strip includes 14 days on either side of
  the selection; selecting an edge date recenters it for continued navigation.
- Groups are Overdue, Up next, Later today, At your own pace, and Completed. On
  Today only, unfinished earlier-date tasks are carried into Overdue. Timed tasks
  become overdue at end time, or start time when there is no end. Later today means
  a timed task starts more than two hours away. Completed tasks are never overdue.
- Progress counts only tasks explicitly scheduled for the selected date, including
  completed tasks. Overdue carryover does not inflate that day's total. An empty
  day has 0/0 tasks and a zero progress value.
- Completion/reopening commits to Room and is reflected by Flow. Controls show an
  in-flight state; a failed write leaves persisted progress unchanged and reports
  an error. Completing a task disables its attached reminders transactionally.
- Cards open a task editor. Creation, editing, rescheduling, priority/category
  selection, notes, optional same-day times, and confirmed deletion are functional.
  Existing subtasks, recurrence metadata, and the latest completion state are
  preserved when content is edited. Unsaved changes require discard confirmation.
  Rescheduling a task preserves existing absolute reminder trigger timestamps;
  relative reminder-offset editing is not part of this phase.
- Validation belongs to `SaveTask`: nonblank title, ISO local date, 24-hour times,
  ordered start/end, and valid device-zone clock-change times. Nonexistent DST
  times are rejected; repeated times use the first occurrence and the editor says so.
- Show companion on Today and Gentle animations are persisted in DataStore. The
  latter controls navigation fades, date-strip centering, and progress animation.

The visible dashboard refreshes its time context every 30 seconds and on timezone
changes, so greeting, midnight behavior, and due states do not require a database
write. Observation stops shortly after leaving Today. Stable lazy-list keys preserve
task identity when groups change. Preview samples remain in the debug source set.

## Scope and verification

This phase does not implement OS notification delivery, reminder creation UI,
recurrence expansion, overnight task times, category management, a running focus
timer, or mascot customization. Attached enabled reminder records are displayed as
such; displaying a reminder does not claim an OS alarm was scheduled. Explore still
has a month/count scaffold and opens Today for the full daily agenda.

Dashboard tests exercise real Room repositories through the ViewModel for empty
state, progress, persistent completion/reopening, overdue carryover, selected-date
refresh, standalone reminders, editor create/edit/delete, error/retry, and companion
preferences. Pure domain tests cover grouping over time, invalid input, DST gaps and
overlaps. Graph ViewModel tests cover shared date selection and restoration.

Run `scripts/verify.ps1` on Windows, or the Gradle build/test/lint tasks documented
in `persistence.md`. Device visual QA and TalkBack interaction checks remain separate
from compilation, JVM tests, and lint. Unsaved editor drafts survive configuration
changes via the ViewModel, but are not persisted after process death.
