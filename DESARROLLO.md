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
| Última versión | **0.9.0** (`Nucleo.VERSION`, 24-11-2026; tags v0.5.0 a v0.8.0 creados; tag v0.9.0: ver sección 7) |
| Se conecta con | luchorcompartidos/mensajes (submódulo) · a futuro luchorcompartidos/recordatorios |

## 2. Etapa actual
**0.9.0 — SMS: en camino y prueba** (`Sms.kt`): se suman `enCamino(ref)` (el SMS se pasó a Android y falta la confirmación), `olvidar(ref)`, y el SMS de prueba `enviarPrueba(numero, app)` con `PREFIJO_PRUEBA` / `esPrueba(ref)` para reconocer su resultado en `Manejador.alResultadoSms`. Antes estaban en Mensajes (`SmsApp`). La usa Mensajes desde su v0.0.17.
- Base 0.8.0: funciones de Recordatorios (`paraSms`, `medir`, `convertirGsm`, `caracteresUnicode`, `convertible`, `descripcionTamanio`) + `partes`, `resumenCorto`, `permitido`, `enviar` con resultado (`SmsResultadoReceiver`), y los diálogos `controlarTamanioSms` / `editarSms`. El permiso `SEND_SMS` lo declara cada app.
- En curso: —.

## 3. Próximos pasos
1. Si el tag v0.9.0 no quedó creado (ver sección 7), crearlo como los anteriores (sección 4).
2. Migrar Recordatorios a este núcleo (incluye cambiar su `Calculo`/`Repeticion` por `Recurrencia`/`Regla`), en etapa aparte (ver DESARROLLO.md de Mensajes y de Recordatorios).
3. Al migrar: el aviso del resultado del SMS de prueba (notificación) sigue en cada app; alinear la apertura de WhatsApp de Mensajes (`WhatsApp.abrir`) con `EnviarWhatsappActivity` de Recordatorios.

## 4. Decisiones tomadas
- 2026-11-23 Núcleo genérico: no conoce recordatorios ni mensajes; la app responde por `Manejador`.
- 2026-11-23 Conserva ids de canales y claves de Ajustes ("ajustes") y Registro ("registro") de Recordatorios, para que su migración no pierda configuración.
- 2026-11-23 Se usa como submódulo en la carpeta `nucleo/` de cada app (checkout con `submodules: true`).
- 2026-11-24 Repetición en el núcleo: tipos, cálculo de la próxima vez, primera vez válida y textos. Mismos códigos ("N","D","S","M","P","A") y claves JSON ("rep","dias","diaMes","semMes","diaSem","limite") que Recordatorios. Las veces ya ocurridas las cuenta cada app.
- 2026-11-24 Apertura automática: el núcleo revisa permiso de superposición y bloqueo (`KeyguardManager` + pantalla encendida) y abre con `startActivity` desde el receiver (excepción de Android por el permiso); la app decide qué abrir. Cada intento queda en el Registro ("pedida a Android" / "NO (motivo)").
- 2026-11-24 SMS en el núcleo con el mismo comportamiento que Recordatorios; el envío informa el resultado (Recordatorios no lo usa: al migrar puede seguir sin él). `SEND_SMS` no va en el manifiesto del núcleo, para no sumarlo a apps que no mandan SMS.
- 2026-11-24 `enCamino`, `olvidar` y el SMS de prueba pasan al núcleo (0.9.0); la notificación con el resultado de la prueba queda en cada app (usa su ícono y su canal).
- 2026-11-23 Versión propia (`Nucleo.VERSION`), independiente de cada app; sin Releases ni workflow (no genera APK). El tag `vX.Y.Z` lo crea el usuario desde la web publicando un pre-release (Releases → Draft a new release → tag nuevo → Target: el commit de esa versión en "Recent commits" → "Set as a pre-release" → Publish). Un borrador no crea el tag: hay que publicarlo. Claude no puede crear tags desde su sesión. Cada app fija el commit exacto del núcleo por el submódulo.

## 5. Pendientes / dudas abiertas
- [ ] ¿Pasar `Contactos`/`Agenda` y las pantallas comunes al núcleo cuando se migre Recordatorios?

## 6. Datos técnicos clave
- Archivos (`src/main/java/com/mis/nucleo/`): `Nucleo.kt` (configurar, `Aviso`, `Manejador`), `Programador.kt` (alarmas + `AlarmaReceiver`/`AccionReceiver`/`ArranqueReceiver`), `Notificador.kt`, `Posposicion.kt` (+ `Ajustes`, `Tiempo`, `Registro`, `Permisos`), `Version.kt` (`VersionGitHub`), `Repeticion.kt` (`Repeticion`, `Regla`, `Recurrencia`), `Apertura.kt` (`Apertura`), `Sms.kt` (`Sms` —incluye `enCamino`, `olvidar`, `enviarPrueba`—, `SmsResultadoReceiver`, `controlarTamanioSms`, `editarSms`); `src/main/res` (tema, paleta, botones, `ic_notif`).
- Permisos en su manifiesto: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, `INTERNET`, `SYSTEM_ALERT_WINDOW` (lo da el usuario en Ajustes del sistema); receivers declarados acá (incluye `SmsResultadoReceiver`); `SEND_SMS` lo pone la app.
- minSdk 34, compileSdk 35, `useAndroidX=false`; versiones de AGP/Kotlin las pone la app.
- Cómo se actualiza en una app: ver README.md.

## 7. Historial (resumido)
- 2026-11-23: Repo creado desde `nucleo/` de Mensajes (núcleo 0.5.0); tag pendiente.
- 2026-11-24: 0.6.0: repetición (`Repeticion.kt`) para Mensajes v0.0.11.
- 2026-11-24: 0.7.0: apertura automática (`Apertura.kt`) para Mensajes v0.0.12.
- 2026-11-24: Tags creados por el usuario como pre-release: **v0.5.0** → `bbd4209`, **v0.6.0** → `f8014aa`, **v0.7.0** → `983a254`.
- 2026-11-24: 0.8.0: SMS (`Sms.kt`) para Mensajes v0.0.15. Tag **v0.8.0** → `4dc1b7d` creado por el usuario.
- 2026-11-24: 0.9.0: `Sms.enCamino`, `olvidar` y `enviarPrueba` (antes en Mensajes) para Mensajes v0.0.17.

---
*Al cerrar cada sesión: actualizar secciones 1, 2, 3 y 7, y subir el archivo al repo.*
