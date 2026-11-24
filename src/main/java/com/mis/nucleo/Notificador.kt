package com.mis.nucleo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Bundle

/** Canales y notificaciones de los avisos. Base: Avisos.kt de Recordatorios. */
object Notificador {
    // Mismos ids de canal que Recordatorios: al migrarla, se conservan los ajustes del usuario.
    private const val CANAL = "recordatorios"
    private const val CANAL_ALARMA = "alarmas_v1"
    private const val CANAL_MUDO = "alarmas_silenciadas_v1"
    private const val CANAL_ESTADO = "pospuestos_v1"
    const val ACCION_HECHO = "com.mis.nucleo.HECHO"
    const val ACCION_POSPONER = "com.mis.nucleo.POSPONER"
    const val ACCION_CALLAR = "com.mis.nucleo.CALLAR"
    private const val EXTRA_ES_POSPUESTA = "pospuesta"

    fun crearCanales(ctx: Context) {
        val normal = NotificationChannel(CANAL, Nucleo.manejador.nombreCanal, NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Avisos programados"; enableVibration(true) }
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarma = NotificationChannel(CANAL_ALARMA, "Alarmas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos que suenan como alarma"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audio)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600)
        }
        val mudo = NotificationChannel(CANAL_MUDO, "Alarmas silenciadas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarma ya silenciada con «Callar»"
            setSound(null, null)
            enableVibration(false)
        }
        val estado = NotificationChannel(CANAL_ESTADO, "Pospuestos", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Avisos pospuestos que siguen en la bandeja" }
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(normal, alarma, mudo, estado))
    }

    /**
     * Quita las notificaciones de un aviso. Cada notificación usa una etiqueta propia, así una nueva
     * nunca queda "pegada" a una anterior.
     */
    fun quitar(ctx: Context, id: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.id == id }.forEach { nm.cancel(it.tag, it.id) }
        nm.cancel(id)
    }

    /** true si la notificación visible de ese aviso viene de una posposición. */
    fun esPospuesta(ctx: Context, id: Int) = ctx.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == id }?.notification?.extras?.getBoolean(EXTRA_ES_POSPUESTA) == true

    /** true si la notificación visible de ese aviso es una alarma sonando (no silenciada ni pospuesta). */
    fun sonando(ctx: Context, id: Int) = ctx.getSystemService(NotificationManager::class.java)
        .activeNotifications.any { it.id == id && it.notification.channelId == CANAL_ALARMA }

    /** Deja la alarma en la bandeja y en pantalla, pero sin sonido. */
    fun silenciar(ctx: Context, id: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.activeNotifications.none { it.id == id }) return
        val a = Nucleo.manejador.aviso(ctx, id) ?: return
        mostrar(ctx, a, comoAlarma = true, silenciada = true, pospuesta = esPospuesta(ctx, id))
    }

    private fun abrir(ctx: Context, id: Int) = PendingIntent.getActivity(ctx, id,
        Intent(ctx, Nucleo.manejador.pantallaPrincipal)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun posponerElegir(ctx: Context, id: Int, personalizado: Boolean = false): PendingIntent? {
        val pantalla = Nucleo.manejador.pantallaPosponer ?: return null
        return PendingIntent.getActivity(ctx, id + if (personalizado) 1_000_000 else 0,
            Intent(ctx, pantalla).putExtra(Programador.EXTRA_ID, id)
                .putExtra(Programador.EXTRA_PERSONALIZADO, personalizado)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pantallaAlarma(ctx: Context, id: Int): PendingIntent? {
        val pantalla = Nucleo.manejador.pantallaAlarma ?: return null
        return PendingIntent.getActivity(ctx, id,
            Intent(ctx, pantalla).putExtra(Programador.EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun boton(ctx: Context, texto: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(ctx, Nucleo.manejador.iconoNotificacion), texto, pi).build()

    private fun accion(ctx: Context, id: Int, accion: String, codigo: String = ""): PendingIntent {
        val i = Intent(ctx, AccionReceiver::class.java).setAction(accion)
            .putExtra(Programador.EXTRA_ID, id).putExtra(Programador.EXTRA_CODIGO, codigo)
        return PendingIntent.getBroadcast(ctx, id, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Botón de posponer rápido (o "Posponer personalizado…" si así está en Ajustes y hay pantalla). */
    private fun posponerRapido(ctx: Context, id: Int): Notification.Action? {
        val rapido = Ajustes.posponerRapido(ctx)
        val pi = if (rapido == Posposicion.PERSONALIZADO) posponerElegir(ctx, id, personalizado = true)
                 else accion(ctx, id, ACCION_POSPONER, rapido)
        return pi?.let { boton(ctx, Posposicion.textoBoton(rapido), it) }
    }

    /**
     * Vuelve a dibujar la notificación visible de un aviso con sus datos actuales (ej. el progreso de un envío),
     * sin sonar ni emerger otra vez. Si no hay notificación visible, no hace nada.
     */
    fun actualizar(ctx: Context, id: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val sbn = nm.activeNotifications.firstOrNull { it.id == id } ?: return
        val a = Nucleo.manejador.aviso(ctx, id) ?: return
        val pospuesta = sbn.notification.extras?.getBoolean(EXTRA_ES_POSPUESTA) == true
        when (sbn.notification.channelId) {
            CANAL_ESTADO -> if (a.pospuestoHasta > 0) mostrarPospuesta(ctx, a, a.pospuestoHasta, tag = sbn.tag)
            CANAL -> mostrar(ctx, a, comoAlarma = false, pospuesta = pospuesta, tag = sbn.tag)
            else -> mostrar(ctx, a, comoAlarma = true, silenciada = true, pospuesta = pospuesta, tag = sbn.tag)
        }
    }

    /**
     * Al posponer: la notificación sigue en la bandeja, sin sonido, indicando hasta cuándo.
     * [tag] != null: reemplaza esa notificación sin volver a avisar (ver [actualizar]).
     */
    fun mostrarPospuesta(ctx: Context, a: Aviso, momento: Long, tag: String? = null) {
        crearCanales(ctx)
        val m = Nucleo.manejador
        if (tag == null) quitar(ctx, a.id)
        val texto = "Pospuesto hasta el ${Tiempo.completo(momento)}" + if (a.texto.isBlank()) "" else "\n${a.texto}"
        val b = Notification.Builder(ctx, CANAL_ESTADO)
            .setSmallIcon(m.iconoNotificacion)
            .setContentTitle(a.titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(abrir(ctx, a.id))
            .setAutoCancel(false)
            .setOnlyAlertOnce(tag != null)
        m.accionesExtra(ctx, a).forEach { b.addAction(it) }
        b.addAction(boton(ctx, "Hecho", accion(ctx, a.id, ACCION_HECHO)))
        posponerElegir(ctx, a.id)?.let { b.addAction(boton(ctx, "Posponer…", it)) }
        ctx.getSystemService(NotificationManager::class.java).notify(tag ?: "p${System.currentTimeMillis()}", a.id, b.build())
    }

    /**
     * Muestra el aviso. [comoAlarma]: canal de alarma, pantalla completa (si la app tiene esa pantalla)
     * y sonido en bucle hasta una acción. [silenciada]: alarma ya callada. [pospuesta]: viene de una posposición.
     * [tag] != null: reemplaza esa notificación sin volver a avisar (ver [actualizar]).
     */
    fun mostrar(ctx: Context, a: Aviso, comoAlarma: Boolean = a.alarma, silenciada: Boolean = false,
                pospuesta: Boolean = false, tag: String? = null) {
        crearCanales(ctx)
        val m = Nucleo.manejador
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Una sola notificación por aviso: la anterior se quita para que la nueva aparezca como nueva
        // (con sonido, emergente y arriba); además corta un sonido en curso.
        if (tag == null) quitar(ctx, a.id)
        val base = a.texto.ifBlank { "Programado para las " + Tiempo.hora(a.momento) }
        val texto = if (pospuesta) "{\"Original Vencido\"}\n$base" else base
        val canal = when {
            !comoAlarma -> CANAL
            silenciada -> CANAL_MUDO
            else -> CANAL_ALARMA
        }
        val b = Notification.Builder(ctx, canal)
            .setSmallIcon(m.iconoNotificacion)
            .setContentTitle(a.titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setAutoCancel(false)   // queda en la bandeja hasta tocar Hecho o deslizarla
            .setOnlyAlertOnce(tag != null)
            .addExtras(Bundle().apply { putBoolean(EXTRA_ES_POSPUESTA, pospuesta) })
        m.accionesExtra(ctx, a).forEach { b.addAction(it) }
        if (m.mostrarHecho(ctx, a)) b.addAction(boton(ctx, "Hecho", accion(ctx, a.id, ACCION_HECHO)))

        val elegir = posponerElegir(ctx, a.id)
        if (comoAlarma) {
            // Pantalla completa: con el celular bloqueado abre la pantalla de alarma;
            // desbloqueado, la notificación emergente queda fija hasta que se haga una acción.
            val pantalla = pantallaAlarma(ctx, a.id)
            b.setCategory(Notification.CATEGORY_ALARM).setContentIntent(pantalla ?: abrir(ctx, a.id))
            if (!silenciada && pantalla != null) b.setFullScreenIntent(pantalla, true)
            elegir?.let { b.addAction(boton(ctx, "Posponer…", it)) }
            if (!silenciada) b.addAction(boton(ctx, "Callar", accion(ctx, a.id, ACCION_CALLAR)))
            else posponerRapido(ctx, a.id)?.let { b.addAction(it) }
        } else {
            b.setCategory(Notification.CATEGORY_REMINDER)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(abrir(ctx, a.id))
            posponerRapido(ctx, a.id)?.let { b.addAction(it) }
            elegir?.let { b.addAction(boton(ctx, "Posponer…", it)) }
        }
        val n = b.build()
        // Sonido en bucle hasta que se toque Hecho, Posponer o Callar (o se abra la bandeja).
        if (comoAlarma && !silenciada) n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(tag ?: "n${System.currentTimeMillis()}", a.id, n)
        if (!silenciada && tag == null) Registro.anotar(ctx, "#${a.id} notificación mostrada (" +
            (if (comoAlarma) "alarma" else "común") + ", notificaciones " +
            (if (nm.areNotificationsEnabled()) "activadas" else "DESACTIVADAS") + ")")
    }
}
