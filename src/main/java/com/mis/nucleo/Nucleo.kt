package com.mis.nucleo

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.Intent

/**
 * Punto de entrada del núcleo compartido entre apps (repo luchorcompartidos/nucleo-android;
 * hoy lo usa Programador de Mensajes como submódulo;
 * más adelante también Recordatorios).
 *
 * El núcleo no sabe si lo que suena es un recordatorio o un mensaje: trabaja con [Aviso]
 * y le pregunta todo a la app a través de [Manejador]. Cada app llama a [configurar]
 * en su `Application.onCreate()` (así los receivers lo encuentran aunque la app esté cerrada).
 */
object Nucleo {
    /** Versión del núcleo (independiente de la versión de cada app). */
    const val VERSION = "0.7.0"

    private var actual: Manejador? = null

    fun configurar(manejador: Manejador) { actual = manejador }

    val manejador: Manejador
        get() = actual ?: error("Núcleo sin configurar: falta Nucleo.configurar(...) en Application.onCreate()")

    /** Versión de la app que usa el núcleo (versionName del APK). */
    fun versionApp(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
}

/**
 * Lo único que el núcleo necesita saber de algo programado (recordatorio, mensaje…).
 * La app lo arma a partir de sus propios datos.
 */
data class Aviso(
    val id: Int,
    val titulo: String,
    val texto: String = "",            // cuerpo de la notificación ("" = "Programado para las HH:mm")
    val momento: Long,                 // próxima fecha y hora (epoch en ms)
    val activo: Boolean = true,        // false = ya venció y no vuelve a sonar por su fecha
    val alarma: Boolean = false,       // true = suena como alarma (pantalla completa, sonido en bucle)
    val pospuestoHasta: Long = 0,      // > 0 = hay una posposición pendiente
    val previo: Long = 0               // > 0 = aviso previo en ese momento (ej. 15 min antes); 0 = ninguno
)

/** Lo que cada app le resuelve al núcleo. */
interface Manejador {
    /** Todos los avisos "en juego" (sin borrados, archivados ni cumplidos). */
    fun avisos(ctx: Context): List<Aviso>

    /** Un aviso por id, o null si ya no existe o está fuera de juego. */
    fun aviso(ctx: Context, id: Int): Aviso?

    /**
     * Venció un aviso (su fecha o su posposición). La app decide qué hacer: normalmente
     * [Notificador.mostrar] si [avisar] y, si no es [pospuesto], calcular la próxima vez
     * (o marcarlo vencido), guardarlo y llamar a [Programador.programar].
     * [perdido]: venció con el celular apagado o la app detenida (se detecta al reprogramar).
     * [avisar]: false cuando se reprograma sin avisar lo perdido (ej. al restaurar una copia).
     */
    fun alVencer(ctx: Context, a: Aviso, pospuesto: Boolean, perdido: Boolean, avisar: Boolean)

    /** Guarda la posposición (0 = sin posposición). */
    fun guardarPospuesto(ctx: Context, id: Int, hasta: Long)

    /** Sonó el aviso previo ([Aviso.previo]). */
    fun alAvisoPrevio(ctx: Context, a: Aviso) {}

    /** Se tocó "Hecho" en la notificación (el núcleo ya la quitó de la bandeja). */
    fun alHecho(ctx: Context, id: Int) {}

    /** Pantalla que se abre al tocar la notificación. */
    val pantallaPrincipal: Class<out Activity>

    /** Pantalla "Posponer…" (recibe [Programador.EXTRA_ID]); null = sin ese botón. */
    val pantallaPosponer: Class<out Activity>? get() = null

    /** Pantalla de alarma a pantalla completa (recibe [Programador.EXTRA_ID]); null = no se usa. */
    val pantallaAlarma: Class<out Activity>? get() = null

    /** Botones propios de la app (ej. "Enviar"); van antes que los del núcleo. Android muestra hasta 3. */
    fun accionesExtra(ctx: Context, a: Aviso): List<Notification.Action> = emptyList()

    /**
     * false = la notificación al vencer va sin "Hecho" (deja lugar a un botón propio, ya que Android
     * muestra hasta 3). La app debe ofrecer "Hecho" en otro lado (ej. su pantalla de alarma).
     */
    fun mostrarHecho(ctx: Context, a: Aviso): Boolean = true

    /**
     * Apertura automática al vencer (ver [Apertura]): pantalla a abrir para este aviso, o null si no
     * corresponde (opción apagada, nada para hacer…). Solo se usa si la app pide `abrir` al mostrar el aviso.
     */
    fun aperturaAutomatica(ctx: Context, a: Aviso): Intent? = null

    /** Por qué [aperturaAutomatica] devolvió null (para el Registro). */
    fun motivoSinApertura(ctx: Context, a: Aviso): String = "la app no abre nada para este aviso"

    /** Nombre visible del canal de avisos comunes (Ajustes de Android > Notificaciones). */
    val nombreCanal: String get() = "Avisos"

    /** Ícono chico de las notificaciones. */
    val iconoNotificacion: Int get() = R.drawable.ic_notif
}
