# Home screen widget (milestone 6.6)

Approved on 2026-10-27 (option B of the mockups).

## Behavior

- One widget, "Mi día" (4×2 by default, resizable): title and "N abiertas · N vencidas", then the overdue tasks followed by today's (same rules as the "Mi día" screen, `planDay`), scrolling if they do not fit.
- The checkbox completes the task without opening the app, with the same rules as the app (a recurring task creates its next occurrence). Tapping the text opens the task; tapping the title opens the app; "+" opens the app on the capture sheet.
- Colors follow the wallpaper (Material You), light and dark, through `GlanceTheme`.

## Updating

- While the app process lives, any change to open tasks (app, widget, reminders) redraws the widget (`JarvisApplication` collects `TaskRepository.open`).
- `updatePeriodMillis` = 1 hour, so after midnight tasks move to "Vencida" at most an hour late.

## Technical

- Jetpack Glance 1.2.0 (`glance-appwidget`, `glance-material3`): new dependencies.
- `MainActivity` reads `EXTRA_TASK_ID` (already used by reminders) and the new `EXTRA_CAPTURE` from both `onCreate` and `onNewIntent`.
- No database or backup changes.

## Out of scope

The resizable variant with the month's spending (option C), choosing a project, typing directly in the widget, shopping widgets, a generated preview in the widget picker.
