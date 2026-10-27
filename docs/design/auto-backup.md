# Automatic backup (milestone 6.4)

Approved on 2026-10-27.

## Why

All data lives only on the phone. The manual JSON export depends on remembering to do it; with money and debts in the app, losing the data hurts more.

## Behavior

- In Ajustes → Respaldo automático the user picks a folder once (Storage Access Framework, `OpenDocumentTree`). The app keeps a persistable permission for that folder only.
- Once a day (WorkManager periodic work, battery not low) it writes `jarvis-auto-YYYY-MM-DD.json` in the normal backup format, replacing a file from earlier the same day.
- It keeps the newest 14. Cleanup only matches `jarvis-auto-YYYY-MM-DD.json`: manual exports (`jarvis-respaldo-…`) and any other file are never deleted.
- Ajustes shows the folder, the last successful backup and whether the last attempt failed, with "Respaldar ahora", "Cambiar carpeta" and "Desactivar".
- If it is on and nothing was saved for more than 3 days (counted from when it was enabled if it never worked), "Mi día" shows a warning card that opens Ajustes.
- Restoring is unchanged: the user picks one of these files.

## Getting the backup off the phone

A folder on the phone does not survive losing the phone. Recommended: sync the folder to the Ubuntu PC with Syncthing (configured by the user, no app code, no cloud). Android's Google cloud backup stays off (`allowBackup="false"`) for privacy.

## Technical

- New dependencies: WorkManager (daily work that survives restarts and runs with the app closed) and DataStore (folder, enabled time, last success, last failure).
- Files are listed, created and deleted with `DocumentsContract`, so no extra library is needed.
- No database or backup format changes.

## Out of scope

Encrypted backups, configurable frequency or count, restoring from an in-app list.
