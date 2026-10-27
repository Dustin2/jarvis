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
  ],
  "shoppingLists": [
    {
      "id": "5a7d…",
      "name": "Súper",
      "sortOrder": 0,
      "createdAt": "2026-10-27T17:00:00Z",
      "updatedAt": "2026-10-27T17:00:00Z"
    }
  ],
  "shoppingItems": [
    {
      "id": "9c41…",
      "listId": "5a7d…",
      "name": "Tortillas",
      "quantity": "2 kg",
      "checked": false,
      "sortOrder": 0,
      "createdAt": "2026-10-27T17:05:00Z",
      "updatedAt": "2026-10-27T17:05:00Z"
    }
  ],
  "categories": [
    {
      "id": "c1…",
      "name": "Comida",
      "kind": "EXPENSE",
      "monthlyBudgetCents": 300000,
      "sortOrder": 0,
      "archived": false,
      "createdAt": "2026-10-27T17:00:00Z",
      "updatedAt": "2026-10-27T17:00:00Z"
    }
  ],
  "movements": [
    {
      "id": "m1…",
      "kind": "EXPENSE",
      "amountCents": 8550,
      "categoryId": "c1…",
      "date": "2026-10-27",
      "note": "Tacos",
      "createdAt": "2026-10-27T18:00:00Z",
      "updatedAt": "2026-10-27T18:00:00Z"
    }
  ],
  "debts": [
    {
      "id": "d1…",
      "person": "Juan",
      "direction": "OWED_TO_ME",
      "amountCents": 50000,
      "dueDate": "2026-11-30",
      "note": null,
      "settledAt": null,
      "createdAt": "2026-10-27T18:10:00Z",
      "updatedAt": "2026-10-27T18:10:00Z"
    }
  ],
  "debtPayments": [
    {
      "id": "p1…",
      "debtId": "d1…",
      "amountCents": 20000,
      "date": "2026-10-27",
      "createdAt": "2026-10-27T18:15:00Z"
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
| `listId` | text | Must be the `id` of a shopping list in the same file |
| `quantity` | text or `null` | Free text, up to 40 characters |
| `checked` | `true` / `false` | The item is in the cart |
| `kind` | text | `EXPENSE` or `INCOME`. A movement's kind must match its category's |
| `amountCents`, `monthlyBudgetCents` | integer | Cents, no decimals: `8550` = $85.50. From 1 to 9999999999. Budgets only on `EXPENSE` categories |
| `categoryId` | text | Must be the `id` of a category in the same file |
| `direction` | text | `I_OWE` or `OWED_TO_ME` |
| `debtId` | text | Must be the `id` of a debt in the same file |
| `archived` | `true` / `false` | Archived category: hidden when registering, kept for past movements |
| `reminderAt` | local date-time or `null` | `YYYY-MM-DDTHH:MM`, **without** a zone: "9:00" wherever you are. Added after the first version; backups without it are still valid |

`notes`, `colorKey`, `projectId`, the dates, `completedAt`, `reminderAt`, `recurrence`, `seriesId` and `quantity` may be `null` or missing.

`categories`, `movements`, `debts` and `debtPayments` (schema v5) may be missing too: older backups restore with no finances, and the next app start creates the initial categories again. `monthlyBudgetCents`, `dueDate`, `settledAt` and the notes may be `null`.

`shoppingLists` and `shoppingItems` (schema v4) may be missing: older backups restore with no shopping lists, and the next app start creates "Súper" again.

## How the app restores

1. It reads and validates the **whole** file. If anything fails, it shows the reason and **changes nothing**.
2. It saves a copy of the current data (`before-restore.json`, internal to the app) so the restore can be undone.
3. It replaces all data with the file's data **in a single transaction**: either everything is restored or nothing is.

Restoring **replaces** the data; it does not merge. After a restore, reminders are rescheduled; those already in the past notify immediately.

Validation messages are shown to the user, so they are in Spanish.
