package com.mis.nucleo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Programa y cancela las alarmas del sistema (AlarmManager). Base: Avisos.kt de Recordatorios. */
object Programador {
    const val EXTRA_ID = "id"
    const val EXTRA_POSPUESTO = "pospuesto"
    const val EXTRA_CODIGO = "codigo"
    const val EXTRA_PREVIO = "previo"
    const val EXTRA_PERSONALIZADO = "personalizado"
    private const val ACCION_ALARMA = "com.mis.nucleo.ALARMA"

    /** Motivo del último fallo al posponer, para mostrarlo al usuario. */
    var motivo = ""

    /** Tres alarmas posibles por aviso: la del momento, la de la posposición y el aviso previo. */
    private fun pendiente(ctx: Context, id: Int, pospuesto: Boolean, previo: Boolean = false): PendingIntent {
        val i = Intent(ctx, AlarmaReceiver::class.java)
            .setAction(ACCION_ALARMA)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_POSPUESTO, pospuesto)
            .putExtra(EXTRA_PREVIO, previo)
        val codigo = id * 4 + if (previo) 2 else if (pospuesto) 1 else 0
        return PendingIntent.getBroadcast(ctx, codigo, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Avisos con alarma sonora: modo "reloj despertador" (nunca se demora por ahorro de batería).
     * El resto: alarma exacta que también funciona en reposo.
     */
    private fun alarmaEn(ctx: Context, momento: Long, pi: PendingIntent, despertador: Boolean): String {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return when {
            despertador && am.canScheduleExactAlarms() -> {
                val ver = PendingIntent.getActivity(ctx, 0, Intent(ctx, Nucleo.manejador.pantallaPrincipal),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(momento, ver), pi)
                "despertador"
            }
            am.canScheduleExactAlarms() -> {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pi)
                "exacta"
            }
            else -> {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pi)
                "aproximada (falta permiso de alarmas exactas)"
            }
        }
    }

    /** Programa la alarma del momento (y el aviso previo, si tiene). No toca la posposición. */
    fun programar(ctx: Context, a: Aviso, registrar: Boolean = true) {
        cancelar(ctx, a.id, incluirPospuesto = false)
        if (!a.activo) return
        val modo = alarmaEn(ctx, a.momento, pendiente(ctx, a.id, false), a.alarma)
        if (a.previo > System.currentTimeMillis() && a.previo < a.momento) {
            alarmaEn(ctx, a.previo, pendiente(ctx, a.id, false, previo = true), false)
        }
        if (registrar) Registro.anotar(ctx, "#${a.id} «${a.titulo}» programado para ${Tiempo.completo(a.momento)} ($modo)")
    }

    /** Pospone según el código de [Posposicion]. Devuelve el momento elegido, o null si no se pudo ([motivo]). */
    fun posponer(ctx: Context, id: Int, codigo: String): Long? {
        val m = Nucleo.manejador
        val a = m.aviso(ctx, id)
        val momento = a?.let { Posposicion.momento(codigo, it.momento) }
        motivo = when {
            a == null -> "no se encontró el aviso (#$id)"
            momento == null -> "opción no válida ($codigo)"
            momento <= System.currentTimeMillis() -> "el momento elegido (${Tiempo.completo(momento)}) ya pasó"
            else -> ""
        }
        if (motivo.isNotEmpty() || a == null || momento == null) {
            Registro.anotar(ctx, "#$id NO se pudo posponer: $motivo")
            return null
        }
        m.guardarPospuesto(ctx, id, momento)
        val modo = alarmaEn(ctx, momento, pendiente(ctx, id, true), a.alarma)
        Notificador.mostrarPospuesta(ctx, a.copy(pospuestoHasta = momento), momento)
        Registro.anotar(ctx, "#$id pospuesto hasta ${Tiempo.completo(momento)} ($modo)")
        return momento
    }

    fun cancelar(ctx: Context, id: Int, incluirPospuesto: Boolean = true) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pendiente(ctx, id, false))
        am.cancel(pendiente(ctx, id, false, previo = true))
        if (incluirPospuesto) am.cancel(pendiente(ctx, id, true))
    }

    /** Antes de borrar un aviso: cancela sus alarmas y quita su notificación (los datos los borra la app). */
    fun quitar(ctx: Context, id: Int) {
        cancelar(ctx, id)
        Notificador.quitar(ctx, id)
    }

    fun cancelarTodo(ctx: Context) = Nucleo.manejador.avisos(ctx).forEach { quitar(ctx, it.id) }

    /**
     * Tras reiniciar el celular, cambiar la hora, actualizar la app, restaurar una copia o abrir la app.
     * [margenMs]: lo vencido hace menos que eso se reprograma (suena enseguida, una sola vez) en vez de
     * tratarse como perdido; evita avisos duplicados si una alarma está por dispararse al abrir la app.
     */
    fun reprogramarTodos(ctx: Context, avisarPerdidos: Boolean = true, margenMs: Long = 0, registrar: Boolean = true) {
        val m = Nucleo.manejador
        val ahora = System.currentTimeMillis() - margenMs
        m.avisos(ctx).forEach { a ->
            if (a.pospuestoHasta > 0) {
                if (a.pospuestoHasta <= ahora) {
                    m.guardarPospuesto(ctx, a.id, 0)
                    if (avisarPerdidos) m.alVencer(ctx, a.copy(pospuestoHasta = 0), pospuesto = true, perdido = true, avisar = true)
                } else {
                    alarmaEn(ctx, a.pospuestoHasta, pendiente(ctx, a.id, true), a.alarma)
                }
            }
            val b = m.aviso(ctx, a.id) ?: return@forEach   // se relee: la app pudo cambiarlo
            if (!b.activo) return@forEach
            if (b.momento <= ahora) {
                m.alVencer(ctx, b, pospuesto = false, perdido = true, avisar = avisarPerdidos)
            } else {
                programar(ctx, b, registrar)
            }
        }
    }
}

/** Suena la alarma: valida que siga correspondiendo y le avisa a la app. */
class AlarmaReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val m = Nucleo.manejador
        val a = m.aviso(ctx, intent.getIntExtra(Programador.EXTRA_ID, -1)) ?: return
        val ahora = System.currentTimeMillis()
        if (intent.getBooleanExtra(Programador.EXTRA_PREVIO, false)) {
            if (!a.activo || a.previo <= 0 || a.momento <= ahora || a.previo > ahora + 60_000) {
                Registro.anotar(ctx, "#${a.id} aviso previo ignorado (ya no corresponde)")
                return
            }
            Registro.anotar(ctx, "#${a.id} aviso previo")
            m.alAvisoPrevio(ctx, a)
            return
        }
        if (intent.getBooleanExtra(Programador.EXTRA_POSPUESTO, false)) {
            if (a.pospuestoHasta == 0L) return   // la posposición se canceló (por ejemplo, al editar)
            m.guardarPospuesto(ctx, a.id, 0)
            Registro.anotar(ctx, "#${a.id} sonó la posposición")
            m.alVencer(ctx, a.copy(pospuestoHasta = 0), pospuesto = true, perdido = false, avisar = true)
            return
        }
        // Ignora alarmas viejas (por ejemplo, si el aviso se editó).
        if (!a.activo || a.momento > ahora + 60_000) {
            Registro.anotar(ctx, "#${a.id} alarma ignorada (activo=${a.activo}, próxima=${Tiempo.completo(a.momento)})")
            return
        }
        Registro.anotar(ctx, "#${a.id} sonó la alarma")
        m.alVencer(ctx, a, pospuesto = false, perdido = false, avisar = true)
    }
}

/** Botones "Hecho", "Posponer" y "Callar" de la notificación. */
class AccionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(Programador.EXTRA_ID, -1)
        when (intent.action) {
            Notificador.ACCION_CALLAR -> Notificador.silenciar(ctx, id)
            Notificador.ACCION_POSPONER ->
                Programador.posponer(ctx, id,
                    intent.getStringExtra(Programador.EXTRA_CODIGO) ?: Posposicion.POR_DEFECTO)
            else -> {
                Notificador.quitar(ctx, id)
                Registro.anotar(ctx, "#$id marcado «Hecho»")
                Nucleo.manejador.alHecho(ctx, id)
            }
        }
    }
}

/** Reinicio del celular, actualización de la app, cambio de hora o del permiso de alarmas exactas. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Programador.reprogramarTodos(ctx)
}
