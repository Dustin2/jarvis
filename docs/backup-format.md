# Formato del respaldo de Jarvis (versión 1)

Un respaldo es un archivo JSON en UTF-8 que contiene **todos** los datos de la app. Se puede leer y editar a mano, pero si se edita, debe seguir estas reglas o la app lo rechazará sin tocar los datos actuales.

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
      "reminderAt": "2026-10-27T09:00"
    }
  ]
}
```

## Campos

| Campo | Tipo | Reglas |
|---|---|---|
| `format` | texto | Siempre `dev.dux.organizer.backup` |
| `version` | entero | Versión del formato. La app acepta su versión y anteriores; rechaza versiones más nuevas |
| `exportedAt` | instante | Solo informativo |
| `id` | texto | Único dentro de su lista (UUID) |
| `name` / `title` | texto | 1–200 caracteres, sin contar espacios al inicio o al final |
| `status` (proyecto) | texto | `ACTIVE`, `PAUSED`, `DONE`, `ARCHIVED` |
| `status` (tarea) | texto | `INBOX`, `TODO`, `DONE`, `CANCELLED` |
| `priority` | texto | `NONE`, `LOW`, `MEDIUM`, `HIGH` |
| `projectId` | texto o `null` | Debe ser el `id` de un proyecto del mismo archivo |
| `dueDate`, `scheduledDate` | fecha o `null` | `AAAA-MM-DD`, sin hora ni zona horaria |
| `createdAt`, `updatedAt`, `completedAt` | instante o `null` | ISO-8601 en UTC, p. ej. `2026-10-25T18:30:00Z` |
| `reminderAt` | fecha y hora local o `null` | `AAAA-MM-DDTHH:MM`, **sin** zona: "las 9:00" donde estés. Se agregó después de la primera versión; los respaldos sin este campo siguen siendo válidos |

`notes`, `colorKey`, `projectId`, las fechas, `completedAt` y `reminderAt` pueden ser `null` o no aparecer.

## Cómo restaura la app

1. Lee y valida el archivo **completo**. Si algo falla, muestra el motivo y **no cambia nada**.
2. Guarda una copia de los datos actuales (`antes-de-restaurar.json`, interna de la app) para poder deshacer.
3. Reemplaza todos los datos por los del archivo **en una sola transacción**: o se restaura todo, o nada.

Restaurar **reemplaza** los datos, no los combina. Después de restaurar, los recordatorios se vuelven a programar; los que ya pasaron avisan de inmediato.
