# DESARROLLO — nucleo-android

> Archivo para retomar el trabajo en un chat nuevo.
> Uso: *"Seguimos con el núcleo Android. Leé `DESARROLLO.md` del repo luchorcompartidos/nucleo-android."*
> Mantenerlo corto (idealmente menos de 1 página). Lo viejo va resumido en "Historial".

---

## 1. Ficha rápida
| Campo | Valor |
|---|---|
| App | Núcleo compartido Android (módulo librería, no es una app instalable) |
| Repo | luchorcompartidos/nucleo-android |
| URL publicada | — (se usa como submódulo `nucleo/`) |
| Tipo | Módulo Android library (Kotlin nativo) |
| Estado | En uso por Programador de Mensajes |
| Última versión | **v0.5.0** (tag, 23-11-2026; sin Release) |
| Se conecta con | luchorcompartidos/mensajes (submódulo) · a futuro luchorcompartidos/recordatorios |

## 2. Etapa actual
**Repo creado** con el contenido de `nucleo/` de Mensajes (núcleo 0.5.0). Mensajes ya lo usa como submódulo.
- En curso: —

## 3. Próximos pasos
1. Migrar Recordatorios a este núcleo, en etapa aparte (ver DESARROLLO.md de Mensajes y de Recordatorios).
2. Al migrar: alinear la apertura de WhatsApp de Mensajes (`WhatsApp.abrir`) con `EnviarWhatsappActivity` de Recordatorios.

## 4. Decisiones tomadas
- 2026-11-23 Núcleo genérico: no conoce recordatorios ni mensajes; la app responde por `Manejador`.
- 2026-11-23 Conserva ids de canales y claves de Ajustes ("ajustes") y Registro ("registro") de Recordatorios, para que su migración no pierda configuración.
- 2026-11-23 Se usa como submódulo en la carpeta `nucleo/` de cada app (checkout con `submodules: true`).
- 2026-11-23 Versión propia (`Nucleo.VERSION` + tag `vX.Y.Z`), independiente de cada app; sin Releases ni workflow (no genera APK).

## 5. Pendientes / dudas abiertas
- [ ] ¿Pasar `Contactos`/`Agenda` y las pantallas comunes al núcleo cuando se migre Recordatorios?

## 6. Datos técnicos clave
- Archivos (`src/main/java/com/mis/nucleo/`): `Nucleo.kt` (configurar, `Aviso`, `Manejador`), `Programador.kt` (alarmas + `AlarmaReceiver`/`AccionReceiver`/`ArranqueReceiver`), `Notificador.kt`, `Posposicion.kt` (+ `Ajustes`, `Tiempo`, `Registro`, `Permisos`), `Version.kt` (`VersionGitHub`); `src/main/res` (tema, paleta, botones, `ic_notif`).
- Permisos en su manifiesto: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `INTERNET`; receivers declarados acá.
- minSdk 34, compileSdk 35, `useAndroidX=false`; versiones de AGP/Kotlin las pone la app.
- Cómo se actualiza en una app: ver README.md.

## 7. Historial (resumido)
- 2026-11-23: Repo creado desde `nucleo/` de Mensajes (núcleo 0.5.0) → tag **v0.5.0**.

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
