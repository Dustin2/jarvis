# Jarvis

A personal organization app for Android, built with Kotlin and Jetpack Compose. It is local-first: everything is stored on the phone, works offline and never needs an account.

## Features

- **Today, inbox and tasks.** A daily plan, an inbox to capture things quickly, and tasks with recurrence, reminders and undo.
- **Projects.** Group tasks into projects, archive them, and restore deleted tasks and projects from the trash.
- **Shopping lists and finances.** Several shopping lists, and income and expense tracking by category.
- **Search, reminders and widget.** Search across everything, exact-time reminders, a morning brief and a weekly summary, and a home-screen widget.
- **Backups.** Daily automatic backups and JSON export and import (see `docs/backup-format.md`).
- **Ask Jarvis.** A voice assistant that runs a language model on the phone (Gemma 4 E2B through LiteRT-LM, on the GPU). It creates reminders, edits, moves and deletes tasks, and answers questions about spending, without sending your data anywhere. The model is downloaded once from Hugging Face on first use.

## Tech stack

- Kotlin, Jetpack Compose and Material 3 Expressive
- Room for storage, with versioned migrations and an exported schema
- Coroutines and Flow, ViewModel and StateFlow
- WorkManager (daily backup), AlarmManager (exact reminders), DataStore (settings)
- Jetpack Glance for the widget
- LiteRT-LM for on-device inference
- JUnit for unit tests, instrumented tests for the data layer, GitHub Actions for CI

Minimum Android version: 13 (API 33).

## Architecture

Screen (Compose) → ViewModel (StateFlow) → Repository → Room DAO → SQLite, with Room as the single source of truth. A single `:app` module organized by feature, with manual dependency injection. `docs/architecture.md` explains each layer and, just as important, which pieces are missing on purpose and when they would become worth adding.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

See `docs/contributing.md` for tests, device setup and project rules, and `docs/conventions/git.md` for the Git workflow.
