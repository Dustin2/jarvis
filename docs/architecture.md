# Architecture

Jarvis follows Google's recommended app architecture: a UI layer and a data layer, with unidirectional data flow. It is deliberately small; this document says what exists, why, and **when** to add each piece that is missing on purpose.

## Layers

```
Screen (Compose)  →  ViewModel (StateFlow)  →  Repository  →  Room DAO  →  SQLite
        ↑ state flows up                         actions flow down ↓
```

| Layer | Responsibility | Example |
|---|---|---|
| Screen | Draws the state, sends user actions. No business rules | `shopping/ShoppingScreen.kt` |
| ViewModel | Combines repository flows into one screen state (`null` = loading); launches actions | `shopping/ShoppingViewModel.kt` |
| Repository | Business rules and validation (`cleanTitle`, recurrence, reminders) | `data/ShoppingRepository.kt` |
| DAO / Room | Queries and transactions. **Room is the source of truth** | `data/ShoppingDao.kt` |

Rules:

- Screens never touch DAOs; repositories never touch Compose.
- Every change goes to the database; screens observe it (`Flow`), so there is no second copy of the data to keep in sync.
- Multi-step changes that must not half-happen are DAO `@Transaction` methods (e.g. `TaskDao.finishOccurrence`, `ShoppingDao.deleteListIfNotLast`).
- Pure logic lives in plain functions with JVM unit tests (`Recurrence`, `reminderAction`, `dayPlan`, `shoppingText`); anything touching Room has device tests on the S25.

## Structure

- Single `:app` module, packages by feature: `today/`, `inbox/`, `tasks/`, `projects/`, `shopping/`, `finances/`, `reminders/`, `backup/`, `settings/`. Shared code in `data/` (entities, DAOs, repositories, backup) and `ui/` (theme and reusable components).
- A new feature gets its own package; its entities, DAO and repository go in `data/`.
- Manual dependency injection: `JarvisApplication` creates the database and repositories once; screens get them through `viewModel { … }` in `ui/JarvisApp.kt`.
- Background work uses WorkManager (the daily automatic backup); exact reminders use AlarmManager. Small settings live in DataStore.
- Concrete repositories (no interfaces) and UUID primary keys, so data created on two devices never collides once sync exists.

## What is missing on purpose, and when to add it

Each of these solves a real problem Jarvis does not have yet. Adding it earlier only adds code.

| Piece | Add it when | Today |
|---|---|---|
| Gradle modules (`:core:data`, `:feature:*`) | Incremental builds get slow, or several people work in parallel and step on each other | One module, builds in seconds |
| Hilt | Wiring dependencies by hand in `JarvisApplication` becomes long or error-prone (roughly 10+ dependencies, or scoped ones) | 6 repositories |
| Use cases (domain layer) | The same logic is needed by several ViewModels and does not belong to a single repository | Logic fits in repositories |
| Repository interfaces | There is a second implementation: sync with a server/Ubuntu, or a fake that tests really need | Device tests use in-memory Room |

Because the layers are already separate, each step is mechanical: moving a package to a module, or putting an interface in front of a repository, does not change the screens.

## Data compatibility

- Every schema change bumps the Room version with a tested migration (`MigrationTest`) and an exported schema in `app/schemas/`. Never `fallbackToDestructiveMigration`.
- The backup format (`docs/backup-format.md`) only gains optional fields within a version; old backups must keep restoring.
