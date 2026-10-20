# Mascot Studio

Mallow (bunny), Luma (owl) and Pip (sprout) are original code-native vector
illustrations in `ui/components/Mascot.kt`. They use one shared visual language,
with explicit resting, upcoming-reminder, task-complete and day-complete states.
No external artwork, asset downloads, Lottie/Rive files, network services or
generated messages are used. Static vectors are always available; animation
does not require loading an asset.

The Studio's screen-level ViewModel owns the unsaved draft and save/error state.
SavedStateHandle keeps the draft through configuration and process recreation.
Selection, color, personality, theme, visibility, animation and sound changes are
previewed before Save. Discard reloads saved settings. Reset requires confirmation
and changes the preview; Save applies the reset. Unsaved drafts remain available
when moving between destinations.

Configuration uses the existing DataStore repository, shared by the app and the
dedicated reminder Activity. The Studio saves atomically without overwriting the
current calendar week-start preference. No Room schema change is required. Legacy
`Encouraging` maps to Friendly and `Quiet` maps to Focused. An unknown mascot ID
falls back to Mallow. All variants support light, dark and system appearances and
the four restrained accent colors. Preferences are configuration, never demo data.

`LocalMascotSettings` supplies shared appearance; child artwork receives explicit
settings and visual state inputs. Domain templates and completion classification
are independent of Compose. A successful task write produces a transient reaction;
finishing the current day's nonempty task list uses a distinct response. Reopening,
failed writes and empty days do not celebrate. Reactions are collected only while
the screen is active and never loop or interrupt other applications.

Animation is a short vertical settling motion. It respects the app toggle and
Android's animator scale/Remove animations setting. Personality changes the local
message templates and restrained animation timing. Companion sound is opt-in and
uses a short native tone only for a successful completion or an explicit sound
preview. It respects silent/vibrate and Do Not Disturb states. Entering a reminder
does not play another companion sound. Notification alert sound and vibration
remain independently controlled by the reminder and Android channels.

Notification taps continue to pass the immutable **occurrence key** in the explicit
Activity PendingIntent. The reminder repository resolves that key to its original
reminder and task IDs. This preserves recurring/snoozed occurrence identity; the
screen's Complete and Snooze operate on that occurrence, while Reschedule opens
the associated task or standalone reminder editor. The Activity uses the saved
theme, selected companion and bundled message templates. Missing/deleted records
remain safe and cannot resolve to another reminder.

## Verification

Verified locally on 20 October 2026: debug app and instrumentation APK builds,
all 125 JVM tests (including three native-graphics Compose UI tests), and lint
passed. No tests were skipped. Lint reported zero errors and 25 warnings. The
compact gallery screenshot was inspected. No emulator or physical-device mascot,
audio or notification interaction checks were run in this phase.

Compose UI tests use Robolectric native graphics with a real DataStore file.
They exercise gallery selection, an unsaved reminder preview, personality and
visibility, reset confirmation, save/reopen persistence and restored drafts.
Domain tests cover contextual completion and all personality templates; repository
tests cover legacy mapping and preserving week start. The existing real-Room
dashboard test now verifies task/day completion events after successful writes.
Reminder tests verify distinct PendingIntent destinations and occurrence-to-source
lookup, including deleted records.

Compose test artifacts use the existing pinned BOM rather than a new version;
see Android's [Compose testing setup](https://developer.android.com/develop/ui/compose/testing).
They are test/debug dependencies, with no new release runtime dependency.

The UI test writes `app/build/reports/mascot-studio.png` for local visual review.
Debug previews include the gallery, a 320dp large-text Studio and a dark reminder
card in `ui/preview/MascotPreviews.kt`. Preview configuration never supplies runtime
application records. Emulator/physical-device validation of audio, TalkBack,
system reduced motion and notification taps remains a separate device check.
