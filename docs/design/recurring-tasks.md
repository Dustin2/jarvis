# Recurring tasks (milestone 6.1)

Approved on 2026-10-27.

## Model: every occurrence is a real task

Completing (or cancelling) a recurring task creates the next occurrence: same title, notes, project and priority, with the next date and the reminder at the same time of day. Only the next occurrence exists, so lists do not fill up, and finished occurrences remain as history (the base for future habits and streaks).

Rejected alternatives: rolling the same task forward (loses history) and generating occurrences ahead of time (orphans when the rule changes, inflated lists).

## Rules

| Frequency | Options |
|---|---|
| Daily | every N days |
| Weekly | every N weeks, on chosen weekdays |
| Monthly | every N months, on day X (every 12 months = yearly) |

- Day 31 (or 29/30) in shorter months falls on the last day of the month.
- The next date is the first occurrence strictly after `max(anchor, today)`, where the anchor is the task's due date or, if absent, its scheduled date. Late completions do not pile up missed occurrences; early completions do not repeat the same period.
- Weekly intervals are counted from the anchor's week (weeks start on Monday); monthly intervals from the anchor's month.

## Actions

| Action on an occurrence | Result |
|---|---|
| Complete | creates the next one |
| Cancel ("not this time") | creates the next one |
| Delete | deletes it, creates nothing |
| Stop repeating | the open occurrence stays, no more are created |
| Reopen a finished one | reopened, nothing created |
| Change the rule | applies from then on |

There is never more than one open occurrence per series: the next one is created only if the series has no other open task, in the same transaction as the status change.

## Dates and reminder

- Repeating requires a date. Enabling it on a task without dates sets the scheduled date to the first occurrence.
- The next occurrence moves the anchor to the next date and shifts the other date by the same number of days.
- Reminders are cleared when they fire, so the reminder time is kept in the rule. Setting or removing the reminder of a recurring task updates it.

## Storage

Schema v3 adds two nullable columns to `tasks` (Room auto-migration):

- `recurrence`: an iCalendar RRULE subset, plus a reminder time extension:
  - `FREQ=DAILY;INTERVAL=3`
  - `FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE,FR`
  - `FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=10;X-TIME=09:00`
- `seriesId`: links the occurrences of a series (the id of the task where repeating was enabled).

Both are optional fields in the backup format; the format stays at version 1.

## Out of scope

Habits and streaks, rules like "last Friday of the month" or "every weekday", and "N days after completion".
