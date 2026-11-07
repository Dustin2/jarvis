# Contributing

How to build, test and change Jarvis. Read `architecture.md` before changing code and `conventions/git.md` before opening a pull request.

## Language

- Everything in the repository is in English: code, comments, docs, commit messages and pull requests.
- The only Spanish text is what the user sees in the app: `res/values/strings.xml` and user-facing messages such as backup validation errors.

## Project rules

- Follow `architecture.md`. Do not add dependencies, layers, interfaces or abstractions without a concrete need; that document lists when each one becomes worth it.
- Single `:app` module, packages by feature (`today/`, `inbox/`, `tasks/`, `projects/`, `shopping/`, `finances/`, `search/`, `reminders/`, `backup/`, `widget/`, `settings/`, `ask/`, `trash/`). Shared code lives in `data/` and `ui/`.
- Room: `exportSchema = true`, versioned migrations, never `fallbackToDestructiveMigration`.
- Before pinning a dependency version, check Google Maven or Maven Central `maven-metadata.xml`, or the official docs.
- Material 3 1.5.0 is a beta on purpose (Expressive). Call it out in the pull request when a change uses APIs marked `@ExperimentalMaterial3ExpressiveApi`.
- Never commit databases, backups or personal data.

## Commands

```bash
./gradlew :app:assembleDebug                                   # build
./gradlew :app:lintDebug                                       # lint
./gradlew :app:testDebugUnitTest                               # JVM unit tests
./gradlew :app:installDebug                                    # debug build ("Jarvis Dev")
ANDROID_SERIAL=<device-serial> ./gradlew :app:installRelease   # optimized build on a device
ANDROID_SERIAL=<device-serial> ./gradlew :app:connectedDebugAndroidTest  # device tests
```

Set `ANDROID_SERIAL` (from `adb devices`) when more than one device is connected; `installDebug` without it installs on all of them. Use the SDK's `adb` (`~/Android/Sdk/platform-tools/adb`).

## Testing on a device

- Pure logic (recurrence, reminders, day planning, text formatting) has JVM unit tests. Anything that touches Room has device tests.
- Run device tests on a real phone. The debug variant is a separate app (`dev.dux.organizer.debug`, "Jarvis Dev"): `connectedDebugAndroidTest` installs it and uninstalls it at the end without touching the real app (`dev.dux.organizer`) or its data.
- Never run tests or commands that uninstall or clear `dev.dux.organizer`.

## Release signing

The release build is signed with the debug key (`~/.android/debug.keystore`) so it installs over the debug build without losing data. If that key is lost, the app cannot be updated without uninstalling it: keep a backup of the key and a recent JSON export.

## Git workflow

See `conventions/git.md`: `main` ← `develop` ← `feature/*`, `fix/*`, `docs/*`, `chore/*`; one pull request per task into `develop`; commits `type(scope): description`.
