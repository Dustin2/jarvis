# Shopping lists (milestone 6.2)

Approved on 2026-10-27.

## Model: own tables, not tasks

An item only needs a name, an optional quantity and a check mark. As tasks, every item would carry status, priority and dates, show up in Tareas and "Mi día", and make checking items off in the store slower.

- `shopping_lists`: name and order. "Súper" is created on first run.
- `shopping_items`: list, name, free-text quantity ("2", "1 kg", "un paquete"), checked and order. Deleting a list deletes its items (`ON DELETE CASCADE`).
- The last list cannot be deleted: the tab always has one, and "empty table" keeps meaning "first run" for seeding.

## Use

- Fifth tab "Compras". Chips switch lists; "Nueva lista" creates one; the menu renames or deletes (with confirmation).
- The floating button becomes "Agregar": items are added in a burst, the keyboard stays open and each one is confirmed with "Agregado: …".
- The whole row toggles the check mark. Checked items move to "En el carrito" until "Quitar comprados" (deletes them) or "Desmarcar todo" (for lists bought every week).
- Share sends what is still pending as plain text through Android's share menu.

## Storage

Schema v4 adds the two tables (Room auto-migration). The backup gets two optional arrays, `shoppingLists` and `shoppingItems`; the format stays at version 1 and restoring replaces them in the same transaction as everything else.

## Out of scope

Suggestions while typing, prices and totals (with finances), drag to reorder, grouping by aisle, and live shared lists (would need a server).
