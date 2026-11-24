package com.mis.nucleo

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Apertura automática de una pantalla de la app cuando vence un aviso (ej. Mensajes abre WhatsApp solo).
 *
 * Android no deja abrir pantallas desde segundo plano, salvo que la app tenga el permiso especial
 * "Mostrar sobre otras apps" (SYSTEM_ALERT_WINDOW). Ese permiso no se puede otorgar al instalar:
 * lo da el usuario en Ajustes del sistema ([intentPermiso]).
 *
 * Qué abrir lo decide la app ([Manejador.aperturaAutomatica]); el núcleo solo revisa que se pueda
 * (permiso concedido y celular desbloqueado) y lo abre. La notificación sale siempre igual, de respaldo:
 * si Android bloquea la apertura igual, la app no se entera.
 */
object Apertura {
    /** El usuario dio el permiso "Mostrar sobre otras apps". */
    fun permitida(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** Pantalla de Ajustes del sistema donde se da el permiso, ya en esta app. */
    fun intentPermiso(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Pantalla apagada o celular bloqueado: no se abre nada (queda la alarma / notificación). */
    fun bloqueado(ctx: Context): Boolean {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        return km.isKeyguardLocked || !pm.isInteractive
    }

    /**
     * Intenta abrir lo que indique la app para este aviso y lo anota en el [Registro].
     * Devuelve true si se le pidió a Android abrirlo (Android igual puede bloquearlo sin avisar:
     * por eso la app debería anotar cuando su pantalla efectivamente se abre).
     */
    fun intentar(ctx: Context, a: Aviso): Boolean {
        val m = Nucleo.manejador
        val intent = m.aperturaAutomatica(ctx, a)
        val motivo = when {
            intent == null -> m.motivoSinApertura(ctx, a)
            !permitida(ctx) -> "falta el permiso «Mostrar sobre otras apps»"
            bloqueado(ctx) -> "celular bloqueado o pantalla apagada"
            else -> null
        }
        if (motivo != null || intent == null) {
            Registro.anotar(ctx, "#${a.id} apertura automática: NO ($motivo)")
            return false
        }
        return try {
            // Con el permiso de superposición, Android permite abrir la pantalla desde el receiver.
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Registro.anotar(ctx, "#${a.id} apertura automática: pedida a Android")
            true
        } catch (e: Exception) {
            Registro.anotar(ctx, "#${a.id} apertura automática: NO (error: ${e.javaClass.simpleName})")
            false
        }
    }
}
