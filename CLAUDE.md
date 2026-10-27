# Jarvis

Personal organization app for Android. Kotlin + Jetpack Compose + Material 3 Expressive. Local-first: Room is the source of truth. No backend, no AI, no paid dependencies.

## Language

- Everything in the repository is in English: code, comments, docs, commit messages and pull requests.
- The only Spanish text is what the user sees in the app: `res/values/strings.xml` (and user-facing messages such as backup validation errors).
- Talk to the user in Spanish.

## Rules

- Work in milestones: do not start a milestone or a substantial change without the user's approval. Explain decisions (the user is learning).
- Before pinning versions, check Google / Maven Central `maven-metadata.xml` or the official docs; never from memory.
- Follow `docs/architecture.md`. Do not add dependencies, layers, interfaces or abstractions without a concrete need; that document lists when each one becomes worth it. Single `:app` module, packages by feature (`today/`, `inbox/`, `tasks/`, `projects/`, `shopping/`, `finances/`, `reminders/`, `backup/`, `settings/`, `data/`, `ui/`).
- Room: `exportSchema = true`, versioned migrations, never `fallbackToDestructiveMigration`.
- Never commit databases, backups or personal data.
- Material 3 1.5.0 is a beta on purpose (Expressive). Say so before using APIs marked `@ExperimentalMaterial3ExpressiveApi`.
- Git: follow `docs/conventions/git.md` (same workflow as Pro-Hielo). `main` ← `develop` ← `feature/*`, `fix/*`, `docs/*`, `chore/*`; never commit to `main`; one PR per task into `develop`; commits `type(scope): description`.

## Commands

```bash
./gradlew :app:assembleDebug              # build
ANDROID_SERIAL=<device-serial> ./gradlew :app:installRelease  # install on the S25 (optimized build)
./gradlew :app:installDebug               # debug build ("Jarvis Dev", slower)
./gradlew :app:lintDebug                  # lint
ANDROID_SERIAL=<device-serial> ./gradlew :app:connectedDebugAndroidTest  # device tests on the S25 (against "Jarvis Dev")
```

Test on the connected S25, not on emulators. The debug variant is a separate app (`dev.dux.organizer.debug`, "Jarvis Dev"): `connectedDebugAndroidTest` installs it and uninstalls it at the end without touching the real app (`dev.dux.organizer`) or its data. Never run tests or commands that uninstall or clear `dev.dux.organizer`. Do not interact with the phone's screen while the user may be using it.

Always set `ANDROID_SERIAL` when more than one device is connected: `installDebug` without it installs on all of them.

The release build is signed with the debug key (`~/.android/debug.keystore`) so it installs over the debug build without losing data. If that key is lost, the app cannot be updated without uninstalling it: keep a backup of it and a recent JSON export.

Use the SDK's `adb` (`~/Android/Sdk/platform-tools/adb`), not `/usr/bin/adb`.
