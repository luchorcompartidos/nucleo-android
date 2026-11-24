package com.mis.nucleo

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tipos de repetición. Base: `Repeticion` y `Calculo` de Recordatorios (mismos códigos y mismas
 * claves JSON), para que su migración al núcleo no pierda datos.
 * El "codigo" es lo que se guarda: no cambia aunque se renombre el enum.
 */
enum class Repeticion(val codigo: String, val etiqueta: String) {
    NINGUNA("N", "No se repite"),
    DIARIA("D", "Todos los días"),
    SEMANAL("S", "Semanal"),
    MENSUAL("M", "El día NN de cada mes"),
    NSEMANA("P", "El n.º día de la semana del mes"),
    ANUAL("A", "Anual");

    companion object {
        fun desdeCodigo(c: String) = entries.firstOrNull { it.codigo == c } ?: NINGUNA
    }
}

/**
 * Cómo se repite algo programado.
 * [dias]: para semanal, 1 = lunes … 7 = domingo. [diaMes]: para mensual y anual.
 * [semanaMes] (1 a 4, 5 = último) y [diaSemana] (1 = lunes): para "El n.º día de la semana del mes".
 * [limite]: terminar después de X veces (0 = nunca). Las veces que ya ocurrió las cuenta la app.
 */
data class Regla(
    val tipo: Repeticion = Repeticion.NINGUNA,
    val dias: Set<Int> = emptySet(),
    val diaMes: Int = 1,
    val semanaMes: Int = 1,
    val diaSemana: Int = 1,
    val limite: Int = 0
) {
    val repite: Boolean get() = tipo != Repeticion.NINGUNA

    /** Agrega la regla a [o] con las mismas claves que Recordatorios ("rep", "dias", "diaMes", "semMes", "diaSem", "limite"). */
    fun ponerEn(o: JSONObject): JSONObject = o.put("rep", tipo.codigo).put("dias", JSONArray(dias.sorted()))
        .put("diaMes", diaMes).put("semMes", semanaMes).put("diaSem", diaSemana).put("limite", limite)

    companion object {
        val NINGUNA = Regla()

        fun desde(o: JSONObject): Regla {
            val d = o.optJSONArray("dias") ?: JSONArray()
            return Regla(
                tipo = Repeticion.desdeCodigo(o.optString("rep", "N")),
                dias = List(d.length()) { d.getInt(it) }.toSet(),
                diaMes = o.optInt("diaMes", 1), semanaMes = o.optInt("semMes", 1),
                diaSemana = o.optInt("diaSem", 1), limite = o.optInt("limite", 0)
            )
        }
    }
}

/** Cálculo de fechas según la [Regla] (igual que `Calculo` de Recordatorios). */
object Recurrencia {
    private val loc = Locale.forLanguageTag("es-AR")
    val ORDINALES = listOf("1.º", "2.º", "3.º", "4.º", "último")
    val DIAS_SEMANA = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    val LETRAS_DIAS = listOf("L", "M", "X", "J", "V", "S", "D")

    private fun zona() = ZoneId.systemDefault()
    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()
    private fun zdt(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zona())

    /** Próxima vez posterior a [despuesDe], tomando [momento] como la vez actual; null si no se repite. */
    fun siguiente(r: Regla, momento: Long, despuesDe: Long): Long? {
        val base = zdt(momento)
        var t = base
        when (r.tipo) {
            Repeticion.NINGUNA -> return null
            Repeticion.DIARIA -> {
                do { t = t.plusDays(1) } while (ms(t) <= despuesDe)
            }
            Repeticion.SEMANAL -> {
                if (r.dias.isEmpty()) return null
                do { t = t.plusDays(1) } while (t.dayOfWeek.value !in r.dias || ms(t) <= despuesDe)
            }
            Repeticion.MENSUAL -> {
                var mes = YearMonth.from(base)
                do {
                    mes = mes.plusMonths(1)
                    t = mes.atDay(min(r.diaMes, mes.lengthOfMonth())).atTime(base.toLocalTime()).atZone(zona())
                } while (ms(t) <= despuesDe)
            }
            Repeticion.NSEMANA -> {
                var mes = YearMonth.from(base)
                do {
                    mes = mes.plusMonths(1)
                    t = nEsimo(mes, r.semanaMes, r.diaSemana).atTime(base.toLocalTime()).atZone(zona())
                } while (ms(t) <= despuesDe)
            }
            Repeticion.ANUAL -> {
                var anio = base.year
                do {
                    anio++
                    val mes = YearMonth.of(anio, base.monthValue)
                    t = mes.atDay(min(r.diaMes, mes.lengthOfMonth())).atTime(base.toLocalTime()).atZone(zona())
                } while (ms(t) <= despuesDe)
            }
        }
        return ms(t)
    }

    /** "El n.º [día] del mes": n de 1 a 4, o 5 = último. */
    fun nEsimo(mes: YearMonth, n: Int, diaSemana: Int): LocalDate {
        val dow = DayOfWeek.of(diaSemana.coerceIn(1, 7))
        return if (n >= 5) mes.atEndOfMonth().with(TemporalAdjusters.lastInMonth(dow))
               else mes.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(n.coerceAtLeast(1), dow))
    }

    /** Al guardar: primera vez válida (un día elegido y en el futuro) a partir de la fecha y hora elegidas. */
    fun ajustarInicio(r: Regla, momento: Long, ahora: Long): Long {
        var m = momento
        if (r.tipo == Repeticion.SEMANAL && r.dias.isNotEmpty()) {
            var t = zdt(m)
            while (t.dayOfWeek.value !in r.dias) t = t.plusDays(1)
            m = ms(t)
        }
        if (r.tipo == Repeticion.NSEMANA) {
            // Primera vez: el n.º día elegido del mes de la fecha elegida (o del mes siguiente si ya pasó).
            val base = zdt(m)
            m = ms(nEsimo(YearMonth.from(base), r.semanaMes, r.diaSemana).atTime(base.toLocalTime()).atZone(zona()))
        }
        if (r.repite && m <= ahora) siguiente(r, m, ahora)?.let { m = it }
        return m
    }

    fun textoNSemana(n: Int, dia: Int) =
        "El ${ORDINALES[(n - 1).coerceIn(0, 4)]} ${DIAS_SEMANA[(dia - 1).coerceIn(0, 6)]} de cada mes"

    /** Etiqueta corta para listas ("Diaria", "Semanal"…), o "" si no se repite. */
    fun corta(r: Regla): String = when (r.tipo) {
        Repeticion.NINGUNA -> ""
        Repeticion.DIARIA -> "Diaria"
        Repeticion.SEMANAL -> "Semanal"
        Repeticion.MENSUAL, Repeticion.NSEMANA -> "Mensual"
        Repeticion.ANUAL -> "Anual"
    }

    /**
     * Descripción completa ("Todos los días", "lunes, jueves", "El día 10 de cada mes"…) y, si tiene límite,
     * cuántas veces van ([ocurrencias]). [momento]: la próxima vez (para la anual). "" si no se repite.
     */
    fun texto(r: Regla, momento: Long, ocurrencias: Int = 0): String {
        val base = when (r.tipo) {
            Repeticion.NINGUNA -> return ""
            Repeticion.DIARIA -> "Todos los días"
            Repeticion.SEMANAL -> r.dias.sorted().joinToString(", ") { DIAS_SEMANA[(it - 1).coerceIn(0, 6)] }
                .replaceFirstChar { it.uppercase() }
            Repeticion.MENSUAL -> "El día ${r.diaMes} de cada mes"
            Repeticion.NSEMANA -> textoNSemana(r.semanaMes, r.diaSemana)
            Repeticion.ANUAL -> "Anual (" + zdt(momento).format(DateTimeFormatter.ofPattern("d 'de' MMMM", loc)) + ")"
        }
        return base + if (r.limite > 0) " · $ocurrencias/${r.limite} veces" else ""
    }
}
