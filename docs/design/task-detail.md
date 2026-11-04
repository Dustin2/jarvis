# Task detail (UX review)

Approved on 2026-10-28: option D4 of the mockups, a hybrid of the D1 list and the D3 icons.

- Title and notes without boxes (plain text fields).
- The planning options form one rounded block of identical rows (`ui/Fields.kt` `PlanRow`): icon in a 40 dp circle, name above, value below, at least 64 dp tall. The circle is filled when the option has a value and outlined when it is empty, so what is planned shows at a glance. Each row is announced as "Fecha límite: Hoy".
- Rows: Fecha límite, Programada para, Prioridad (dropdown), Proyecto (dropdown), Recordatorio, Repetir; each opens the same picker as before.
- A full-width "Completar" button is fixed at the bottom; on a finished or cancelled task it becomes "Reabrir" and the status shows under the title.
- The four status chips are gone. The "⋮" menu holds the other status changes ("Mover a Pendientes" from the Bandeja, "Regresar a la Bandeja", "Cancelar tarea") and "Eliminar".

Rejected: the first mockup (status chips replaced by a button but still boxed fields), D2 (summary chips; less room for each value at large font) and D3 (icon-only toolbar; values hidden behind icons).
