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
- Mensajes de commit en inglés.

## Comandos

```bash
./gradlew :app:assembleDebug              # compilar
./gradlew :app:installDebug               # instalar en el S25 conectado
./gradlew :app:lintDebug                  # lint
./gradlew :app:connectedDebugAndroidTest  # pruebas en dispositivo
```

⚠️ `connectedDebugAndroidTest` **desinstala la app al terminar y borra sus datos**. Cuando el usuario ya tenga datos reales en el S25, correr esas pruebas en el emulador (`Medium_Phone`) y no en el teléfono.

Usar el `adb` del SDK (`~/Android/Sdk/platform-tools/adb`), no `/usr/bin/adb`.
