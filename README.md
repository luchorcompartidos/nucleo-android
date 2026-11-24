# nucleo-android

Núcleo compartido de las apps Android de la caja de apps (Kotlin nativo, sin librerías externas).
Es genérico: no conoce "recordatorios" ni "mensajes". Cada app responde por `Manejador`.

Incluye: alarmas exactas (`Programador` + receivers), notificaciones (`Notificador`),
posponer (`Posposicion`, `Ajustes`, `Tiempo`, `Registro`, `Permisos`), repetición (`Repeticion`, `Regla`, `Recurrencia`), apertura automática al vencer (`Apertura`, permiso "Mostrar sobre otras apps"), versión desde las
Releases de GitHub (`VersionGitHub`) y tema/paleta/botones comunes.

## Cómo se usa en una app
1. Agregarlo como submódulo en la carpeta `nucleo/` de la app:
   ```
   git submodule add https://github.com/luchorcompartidos/nucleo-android.git nucleo
   ```
2. En `settings.gradle.kts` de la app: `include(":nucleo")`.
3. En `app/build.gradle.kts`: `implementation(project(":nucleo"))`.
4. En GitHub Actions, el checkout con `submodules: true`.
5. La app llama a `Nucleo.configurar(...)` en su `Application.onCreate()`.

Requisitos: minSdk 34, compileSdk 35, `android.useAndroidX=false`.
Las versiones de plugins (AGP / Kotlin) las define la app que lo incluye.

## Actualizar el núcleo dentro de una app
```
cd nucleo && git pull origin main && cd ..
git add nucleo && git commit -m "Núcleo a vX.Y.Z"
```
La versión del núcleo está en `Nucleo.VERSION` (independiente de la versión de cada app);
opcionalmente, también como tag `vX.Y.Z` en este repo.
