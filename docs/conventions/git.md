# Flujo de Git

El mismo flujo que Pro-Hielo (`docs/conventions/git.md` en ese repo), adaptado a Jarvis.

## Ramas

```text
main            versión estable, siempre instalable; solo recibe PRs desde develop
└── develop     integración de trabajo terminado; de aquí salen las ramas de tarea
     ├── feature/<nombre>   funcionalidad nueva      feature/recurring-tasks
     ├── fix/<nombre>       corrección de un error   fix/reminder-timezone
     ├── docs/<tema>        solo documentación       docs/backup-format
     └── chore/<tarea>      mantenimiento            chore/update-dependencies
```

- Nunca commitear directo a `main`. Evitar commitear directo a `develop`.
- GitHub Free no permite proteger ramas en repos privados: estas reglas se cumplen por disciplina (y los agentes las siguen vía `CLAUDE.md`).

## Flujo de una tarea

```bash
git checkout develop && git pull
git checkout -b feature/recurring-tasks
# ... cambios, con commits pequeños y enfocados ...
git push -u origin feature/recurring-tasks
gh pr create --base develop      # la plantilla de PR se llena sola
```

Antes de mergear: el CI está en verde, y si se tocó la base de datos o los recordatorios, también pasaron las pruebas de dispositivo **en el emulador** (`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`). Se mergea con merge commit.

## Commits

Conventional Commits simplificado, **en inglés**:

```text
type(scope): description
```

| Tipo | Uso |
|---|---|
| `feat` | Funcionalidad nueva |
| `fix` | Corrección de un error |
| `docs` | Solo documentación |
| `chore` | Mantenimiento (build, dependencias, CI) |
| `refactor` | Cambio interno sin cambiar el comportamiento |
| `test` | Agregar o corregir pruebas |
| `style` | Solo formato |

Alcances (`scope`) de Jarvis: `tasks`, `inbox`, `today`, `projects`, `reminders`, `backup`, `settings`, `data` (Room, migraciones), `ui` (tema, componentes compartidos), `build`, `deps`, `ci`, `git`, `docs`.

Ejemplos:

```text
feat(reminders): repeat reminders for recurring tasks
fix(today): count tasks scheduled for yesterday as today
chore(deps): update Compose BOM to 2026.10.00
```

## Releases

Cuando `develop` está estable: PR `develop → main` titulada `release: vX.Y.Z — <resumen>`, luego etiqueta y GitHub Release.

| Parte | Sube cuando… |
|---|---|
| MAJOR | Hay un cambio incompatible (p. ej. un respaldo viejo ya no se puede restaurar) |
| MINOR | Hay funcionalidad nueva |
| PATCH | Solo correcciones |

Al publicar una versión, actualizar `versionName` y subir `versionCode` en `app/build.gradle.kts`: Android usa `versionCode` para saber que es una actualización.
