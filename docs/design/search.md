# Search (milestone 6.5)

Approved on 2026-10-27.

## Scope

| Section | Fields |
|---|---|
| Tasks (open and finished) | title, notes |
| Projects (not archived) | name |
| Shopping items | name, quantity (shows the list) |
| Movements | note, category name |
| Debts | person, note |

## Behavior

- A search icon in the "Mi día" and "Tareas" headers opens the search screen with the keyboard ready.
- Results update while typing (150 ms debounce), grouped by section, up to 5 per group with "Ver todos (n)".
- Case and accents are ignored both ways ("cafe" ↔ "Café"); the matching part is shown in bold.
- Open tasks come before finished ones, unchecked items before checked ones, newest movements first, open debts first.
- Each result leads to its place: task, project and debt details; an item opens the Compras tab on its list; a movement opens Finanzas on its month with the movement's edit sheet.

## Implementation

Everything is filtered in memory (`search/Search.kt`), which is what makes accent-insensitive matching possible (SQLite `LIKE` only folds ASCII case). Instant for personal data; with tens of thousands of rows, move to an SQLite FTS table. No schema, backup or dependency changes.

## Out of scope

Recent searches, filters, searching settings, voice search (the keyboard's microphone already works).
