# Jarvis

App Android personal de organización. Kotlin + Jetpack Compose + Material 3 Expressive. Local-first: Room es la fuente de verdad. Sin backend, sin IA, sin dependencias de pago.

## Reglas

- Trabajo por hitos: no empezar un hito ni un cambio sustancial sin aprobación del usuario. Explicar decisiones (el usuario está aprendiendo).
- Antes de fijar versiones, consultar maven-metadata de Google/Maven Central o la documentación oficial; nunca de memoria.
- No agregar dependencias, capas, interfaces ni abstracciones sin una necesidad concreta. Un solo módulo `:app`, paquetes por función (`today/`, `inbox/`, `tasks/`, `projects/`, `data/`, `ui/theme/`).
- Room: `exportSchema = true`, migraciones versionadas, nunca `fallbackToDestructiveMigration`.
- Nunca subir bases de datos, respaldos ni datos personales al repo.
- Material 3 1.5.0 está en beta a propósito (Expressive). Avisar antes de usar APIs con `@ExperimentalMaterial3ExpressiveApi`.
- Textos de la UI en español, en `res/values/strings.xml`.
- Git: seguir `docs/conventions/git.md` (mismo flujo que Pro-Hielo). `main` ← `develop` ← `feature/*`, `fix/*`, `docs/*`, `chore/*`; nunca commitear a `main`; una PR por tarea hacia `develop`; commits `type(scope): description` en inglés.

## Comandos

```bash
./gradlew :app:assembleDebug              # compilar
ANDROID_SERIAL=<device-serial> ./gradlew :app:installRelease  # instalar en el S25 (versión optimizada)
./gradlew :app:installDebug               # versión de depuración (más lenta)
./gradlew :app:lintDebug                  # lint
ANDROID_SERIAL=<device-serial> ./gradlew :app:connectedDebugAndroidTest  # pruebas en el S25 (contra "Jarvis Dev")
```

Las pruebas se hacen en el S25 conectado, no en emuladores. La variante debug es otra app (`dev.dux.organizer.debug`, "Jarvis Dev"): `connectedDebugAndroidTest` la instala y la desinstala al terminar sin tocar la app real (`dev.dux.organizer`) ni sus datos. Nunca correr pruebas ni comandos que desinstalen o limpien `dev.dux.organizer`.

Siempre indicar `ANDROID_SERIAL` cuando hay más de un dispositivo conectado: `installDebug` sin él instala en todos.

La versión release se firma con la llave de debug (`~/.android/debug.keystore`) para poder actualizar encima de la de debug sin perder datos. Si esa llave se pierde, no se puede actualizar la app sin desinstalarla: respaldarla, y tener siempre un export JSON reciente.

Usar el `adb` del SDK (`~/Android/Sdk/platform-tools/adb`), no `/usr/bin/adb`.
