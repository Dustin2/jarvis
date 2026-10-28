# Navigation (UX review)

Approved on 2026-10-27: option A of the mockups (https://claude.ai/artifact/B6ioAUA4y3uSonnG7CmXKZ).

## Bottom bar

Mi día · Tareas · Proyectos · Compras · Finanzas.

- **Finanzas** is a tab: expenses are logged daily. The "Mi día" card switches to it. Opened from a search result it is still a pushed screen with a back arrow, on the movement's month.
- **Bandeja** is a view inside Tareas, not a tab: it is the first step of a task. Tareas has three chips with counts: "Bandeja (n)", "Pendientes (n)" (default), "Terminadas (n)".

## One rule for every tab header

`ui/ScreenHeader.kt`: title, search, the screen's own actions (settings in Mi día, share in Compras), and a "⋮" menu holding the "Nuevo…" entries ("Nuevo proyecto", "Nueva lista", "Nueva categoría", plus rename/delete where they apply). The floating button always creates the screen's main thing (capture a task, add an item, register a movement or a new debt).

## Rejected

- B, a "Más" tab with Proyectos, Finanzas and Ajustes: two taps to reach daily screens.
- C, keeping today's tabs with a Finanzas icon in the header: Finanzas stays hidden.
