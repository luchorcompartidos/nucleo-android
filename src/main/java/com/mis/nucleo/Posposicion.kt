package com.mis.nucleo

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Opciones de posponer (las mismas de Recordatorios). Códigos: "m:N" minutos, "h:N" horas, "d:N" días,
 * "w:N" semanas, "M:N" meses, "y:N" años, "manana" = mañana a la hora del aviso, "t:ms" = momento puntual.
 */
data class OpcionPosponer(val codigo: String, val texto: String)

object Posposicion {
    const val POR_DEFECTO = "m:10"
    const val PERSONALIZADO = "personalizado"

    val OPCIONES: List<OpcionPosponer> =
        listOf(1, 2, 3, 4, 5, 10, 15, 30, 45, 55).map { OpcionPosponer("m:$it", if (it == 1) "1 minuto" else "$it minutos") } +
        listOf(1, 2, 3, 5, 12).map { OpcionPosponer("h:$it", if (it == 1) "1 hora" else "$it horas") } +
        listOf(
            OpcionPosponer("manana", "Mañana a la misma hora"),
            OpcionPosponer("w:1", "1 semana"),
            OpcionPosponer("M:1", "1 mes"),
            OpcionPosponer("y:1", "1 año")
        )

    /** Opciones elegibles como botón rápido: el set completo más "Personalizado…". */
    val OPCIONES_RAPIDO = OPCIONES + OpcionPosponer(PERSONALIZADO, "Personalizado…")

    fun texto(codigo: String) = OPCIONES_RAPIDO.firstOrNull { it.codigo == codigo }?.texto ?: "10 minutos"

    /** Texto para un botón: "Posponer 10 minutos", "Posponer personalizado…". */
    fun textoBoton(codigo: String) = "Posponer " + texto(codigo).replaceFirstChar { it.lowercase() }

    /** Momento al que se pospone. [original]: fecha/hora del aviso (para "manana"). */
    fun momento(codigo: String, original: Long, ahora: ZonedDateTime = ZonedDateTime.now()): Long? {
        if (codigo == "manana") {
            val hora = Tiempo.zdt(original).toLocalTime().withSecond(0).withNano(0)
            return ahora.toLocalDate().plusDays(1).atTime(hora).atZone(ahora.zone).toInstant().toEpochMilli()
        }
        val partes = codigo.split(":")
        val n = partes.getOrNull(1)?.toLongOrNull() ?: return null
        val t = when (partes[0]) {
            "t" -> return n
            "m" -> ahora.plusMinutes(n)
            "h" -> ahora.plusHours(n)
            "d" -> ahora.plusDays(n)
            "w" -> ahora.plusWeeks(n)
            "M" -> ahora.plusMonths(n)
            "y" -> ahora.plusYears(n)
            else -> return null
        }
        return t.withNano(0).toInstant().toEpochMilli()
    }
}

/** Ajustes compartidos de avisos (mismas claves que Recordatorios, en las preferencias "ajustes"). */
object Ajustes {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    fun alarmaPorDefecto(ctx: Context) = prefs(ctx).getBoolean("alarma_defecto", false)
    fun guardarAlarmaPorDefecto(ctx: Context, valor: Boolean) =
        prefs(ctx).edit().putBoolean("alarma_defecto", valor).apply()
    fun posponerRapido(ctx: Context): String =
        prefs(ctx).getString("posponer_rapido", Posposicion.POR_DEFECTO) ?: Posposicion.POR_DEFECTO
    fun guardarPosponerRapido(ctx: Context, codigo: String) =
        prefs(ctx).edit().putString("posponer_rapido", codigo).apply()

    // Últimos valores usados en "Personalizado".
    fun ultimoPlazo(ctx: Context): Pair<Int, String> =
        prefs(ctx).getInt("plazo_n", 1) to (prefs(ctx).getString("plazo_u", "m") ?: "m")
    fun guardarUltimoPlazo(ctx: Context, n: Int, unidad: String) =
        prefs(ctx).edit().putInt("plazo_n", n).putString("plazo_u", unidad).apply()
    fun ultimaHora(ctx: Context): LocalTime? =
        prefs(ctx).getInt("hora_min", -1).takeIf { it >= 0 }?.let { LocalTime.of(it / 60, it % 60) }
    fun guardarUltimaHora(ctx: Context, t: LocalTime) =
        prefs(ctx).edit().putInt("hora_min", t.hour * 60 + t.minute).apply()

    fun exportar(ctx: Context): JSONObject = JSONObject()
        .put("alarma_defecto", alarmaPorDefecto(ctx))
        .put("posponer_rapido", posponerRapido(ctx))
        .put("plazo_n", prefs(ctx).getInt("plazo_n", 1))
        .put("plazo_u", prefs(ctx).getString("plazo_u", "m"))
        .put("hora_min", prefs(ctx).getInt("hora_min", -1))

    fun importar(ctx: Context, o: JSONObject) {
        guardarAlarmaPorDefecto(ctx, o.optBoolean("alarma_defecto", false))
        guardarPosponerRapido(ctx, o.optString("posponer_rapido", Posposicion.POR_DEFECTO))
        prefs(ctx).edit()
            .putInt("plazo_n", o.optInt("plazo_n", 1))
            .putString("plazo_u", o.optString("plazo_u", "m"))
            .putInt("hora_min", o.optInt("hora_min", -1))
            .apply()
    }
}

/** Fechas y horas en formato argentino. */
object Tiempo {
    private val loc = Locale.forLanguageTag("es-AR")
    private val fFecha = DateTimeFormatter.ofPattern("EEE d MMM yyyy", loc)
    private val fHora = DateTimeFormatter.ofPattern("HH:mm", loc)
    private val fCompleto = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", loc)

    fun zdt(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    fun fecha(ms: Long): String = zdt(ms).format(fFecha)
    fun hora(ms: Long): String = zdt(ms).format(fHora)
    fun completo(ms: Long): String = zdt(ms).format(fCompleto)
}

/** Registro de eventos (últimos 80) para diagnosticar por qué algo sonó o no. */
object Registro {
    private const val MAX = 80
    private val formato = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")

    fun anotar(ctx: Context, texto: String) {
        val p = ctx.getSharedPreferences("registro", Context.MODE_PRIVATE)
        val arr = JSONArray(p.getString("l", "[]"))
        arr.put(Tiempo.zdt(System.currentTimeMillis()).format(formato) + " · " + texto)
        val nuevo = JSONArray()
        for (i in maxOf(0, arr.length() - MAX) until arr.length()) nuevo.put(arr.getString(i))
        p.edit().putString("l", nuevo.toString()).apply()
    }

    fun texto(ctx: Context): String {
        val arr = JSONArray(ctx.getSharedPreferences("registro", Context.MODE_PRIVATE).getString("l", "[]"))
        if (arr.length() == 0) return "Todavía no hay eventos."
        return (arr.length() - 1 downTo 0).joinToString("\n") { arr.getString(it) }
    }
}

/** Permisos que necesitan los avisos para sonar a tiempo. */
object Permisos {
    /** Primer permiso que falta, con el texto a mostrar y la pantalla de Ajustes que lo resuelve; null = todo OK. */
    fun faltante(ctx: Context, usaAlarmas: Boolean): Pair<String, Intent>? {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val paquete = Uri.parse("package:${ctx.packageName}")
        return when {
            !nm.areNotificationsEnabled() -> "Las notificaciones están desactivadas. Tocá acá para activarlas." to
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            !am.canScheduleExactAlarms() -> "Falta el permiso de alarmas exactas. Tocá acá para habilitarlo." to
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, paquete)
            usaAlarmas && !nm.canUseFullScreenIntent() ->
                "Para que las alarmas aparezcan a pantalla completa falta un permiso. Tocá acá para habilitarlo." to
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, paquete)
            ctx.getSystemService(ActivityManager::class.java).isBackgroundRestricted ->
                "La batería de la app está en modo «Restringido» y los avisos pueden no sonar. " +
                    "Tocá acá y en «Batería» elegí «Optimizado» o «Sin restricciones»." to
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, paquete)
            else -> null
        }
    }
}
