# Jarvis backup format (version 1)

A backup is a UTF-8 JSON file with **all** of the app's data. It can be read and edited by hand, but an edited file must follow these rules or the app rejects it without touching the current data.

```json
{
  "format": "dev.dux.organizer.backup",
  "version": 1,
  "exportedAt": "2026-10-25T18:30:00Z",
  "projects": [
    {
      "id": "8f0c…",
      "name": "Pro-Hielo",
      "colorKey": null,
      "status": "ACTIVE",
      "sortOrder": 0,
      "createdAt": "2026-10-25T17:00:00Z",
      "updatedAt": "2026-10-25T17:00:00Z"
    }
  ],
  "tasks": [
    {
      "id": "1b2e…",
      "title": "Revisar facturas",
      "notes": null,
      "status": "TODO",
      "priority": "HIGH",
      "projectId": "8f0c…",
      "dueDate": "2026-10-27",
      "scheduledDate": null,
      "createdAt": "2026-10-25T17:05:00Z",
      "updatedAt": "2026-10-25T17:10:00Z",
      "completedAt": null,
      "reminderAt": "2026-10-27T09:00",
      "recurrence": "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=27;X-TIME=09:00",
      "seriesId": "1b2e…"
    }
  ]
}
```

## Fields

| Field | Type | Rules |
|---|---|---|
| `format` | text | Always `dev.dux.organizer.backup` |
| `version` | integer | Format version. The app accepts its own version and older ones; it rejects newer versions |
| `exportedAt` | instant | Informational only |
| `id` | text | Unique within its list (UUID) |
| `name` / `title` | text | 1–200 characters, not counting leading or trailing spaces |
| `status` (project) | text | `ACTIVE`, `PAUSED`, `DONE`, `ARCHIVED` |
| `status` (task) | text | `INBOX`, `TODO`, `DONE`, `CANCELLED` |
| `priority` | text | `NONE`, `LOW`, `MEDIUM`, `HIGH` |
| `projectId` | text or `null` | Must be the `id` of a project in the same file |
| `dueDate`, `scheduledDate` | date or `null` | `YYYY-MM-DD`, no time or time zone |
| `createdAt`, `updatedAt`, `completedAt` | instant or `null` | ISO-8601 in UTC, e.g. `2026-10-25T18:30:00Z` |
| `recurrence` | text or `null` | Repeat rule, an iCalendar RRULE subset (see `docs/design/recurring-tasks.md`). Added in schema v3 |
| `seriesId` | text or `null` | Links the occurrences of a recurring task. Added in schema v3 |
| `reminderAt` | local date-time or `null` | `YYYY-MM-DDTHH:MM`, **without** a zone: "9:00" wherever you are. Added after the first version; backups without it are still valid |

`notes`, `colorKey`, `projectId`, the dates, `completedAt`, `reminderAt`, `recurrence` and `seriesId` may be `null` or missing.

## How the app restores

1. It reads and validates the **whole** file. If anything fails, it shows the reason and **changes nothing**.
2. It saves a copy of the current data (`before-restore.json`, internal to the app) so the restore can be undone.
3. It replaces all data with the file's data **in a single transaction**: either everything is restored or nothing is.

Restoring **replaces** the data; it does not merge. After a restore, reminders are rescheduled; those already in the past notify immediately.

Validation messages are shown to the user, so they are in Spanish.
