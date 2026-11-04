# Git Workflow

The same workflow as Pro-Hielo (`docs/conventions/git.md` in that repository), adapted to Jarvis.

## Branches

```text
main            stable version, always installable; only receives PRs from develop
└── develop     integration of finished work; task branches start here
     ├── feature/<name>   new functionality    feature/recurring-tasks
     ├── fix/<name>       bug fix              fix/reminder-timezone
     ├── docs/<topic>     documentation only   docs/backup-format
     └── chore/<task>     maintenance          chore/update-dependencies
```

- Never commit directly to `main`. Avoid committing directly to `develop`.
- GitHub Free does not allow branch protection on private repositories: these rules are kept by discipline.

## Task flow

```bash
git checkout develop && git pull
git checkout -b feature/recurring-tasks
# ... changes, in small focused commits ...
git push -u origin feature/recurring-tasks
gh pr create --base develop      # the PR template fills in automatically
```

Before merging: CI is green, and if the change touches the database or reminders, the device tests also passed **on the S25** (`ANDROID_SERIAL=<device-serial> ./gradlew :app:connectedDebugAndroidTest`, which runs against the separate "Jarvis Dev" app). Merge with a merge commit, then delete the task branch.

## Commits

Simplified Conventional Commits:

```text
type(scope): description
```

| Type | Use |
|---|---|
| `feat` | New functionality |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `chore` | Maintenance (build, dependencies, CI) |
| `refactor` | Internal change without changing behavior |
| `test` | Add or fix tests |
| `style` | Formatting only |

Jarvis scopes: `tasks`, `inbox`, `today`, `projects`, `reminders`, `backup`, `settings`, `data` (Room, migrations), `ui` (theme, shared components), `build`, `deps`, `ci`, `git`, `docs`.

Examples:

```text
feat(reminders): repeat reminders for recurring tasks
fix(today): count tasks scheduled for yesterday as today
chore(deps): update Compose BOM to 2026.10.00
```

## Releases

When `develop` is stable:

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts` through a `chore/release-vX.Y.Z` PR into `develop`. Android uses `versionCode` to recognize an update.
2. Open a PR `develop → main` titled `release: vX.Y.Z — <summary>` and merge it with `gh pr merge <n> --merge`. **Do not delete the branch**: its head is `develop`. Afterwards, check that `develop` still exists (`git ls-remote --heads origin`).
3. Tag `vX.Y.Z` on `main` and publish a GitHub Release.
4. Fast-forward `develop` to `main` so it includes the release merge.

| Part | Goes up when… |
|---|---|
| MAJOR | There is an incompatible change (e.g. an old backup can no longer be restored) |
| MINOR | There is new functionality |
| PATCH | Only fixes |
