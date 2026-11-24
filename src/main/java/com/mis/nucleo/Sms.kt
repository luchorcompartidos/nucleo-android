package com.mis.nucleo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.text.InputType
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * SMS: largo, costo y envío. Mismas funciones y comportamiento que Recordatorios (`Envios.medir`,
 * `descripcionTamanio`, `convertirGsm`, `caracteresUnicode`, `convertible`, `paraSms`), más el envío
 * con resultado: Android avisa si cada SMS salió y el núcleo se lo pasa a la app por
 * [Manejador.alResultadoSms]. El permiso `SEND_SMS` lo declara cada app que manda SMS.
 */
object Sms {
    /** Ajustes seguros para que el SMS ocupe menos: "→" (fuerza Unicode) pasa a "->". */
    fun paraSms(t: String) = t.replace("→", "->")

    /** Cálculo oficial de Android: [cantidad de SMS, caracteres usados, restantes, 1 = GSM-7 / 3 = Unicode]. */
    fun medir(t: String): IntArray = SmsMessage.calculateLength(t, false)

    fun partes(t: String) = medir(t)[0]

    fun unicode(t: String) = medir(t)[3] == SmsMessage.ENCODING_16BIT

    private fun esGsm(ch: String) = medir(ch)[3] != SmsMessage.ENCODING_16BIT

    /**
     * "Convertir caracteres": reemplaza solo los caracteres que fuerzan Unicode (según el propio Android)
     * por equivalentes estándar: letras sin tilde, comillas y guiones simples, "..." en vez de "…".
     * Los que no tengan equivalente quedan como están.
     */
    fun convertirGsm(t: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            val ch = String(Character.toChars(cp))
            i += Character.charCount(cp)
            if (esGsm(ch)) { sb.append(ch); continue }
            val reemplazo = when (ch) {
                "“", "”", "«", "»" -> "\""
                "‘", "’" -> "'"
                "–", "—" -> "-"
                "…" -> "..."
                "→" -> "->"
                "•", "·", "‣", "◦", "▪", "●" -> "-"   // viñetas
                "º", "°" -> "o"
                "ª" -> "a"
                "´", "`" -> "'"
                " ", "\t" -> " "   // espacio duro y tabulación
                "̶" -> ""   // marca de tachado
                else -> java.text.Normalizer.normalize(ch, java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
            }
            sb.append(if (reemplazo.isNotEmpty() && reemplazo.all { esGsm(it.toString()) }) reemplazo
                      else if (reemplazo.isEmpty()) "" else ch)
        }
        return sb.toString()
    }

    /** Caracteres que siguen forzando Unicode (para mostrarlos al usuario). */
    fun caracteresUnicode(t: String): String {
        val lista = mutableListOf<String>()
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            val ch = String(Character.toChars(cp))
            i += Character.charCount(cp)
            if (!esGsm(ch) && ch !in lista) lista += ch
        }
        return lista.joinToString(" ")
    }

    /** ¿La conversión cambiaría el texto? */
    fun convertible(t: String) = convertirGsm(t) != t

    /** Descripción del tamaño: "2 SMS · 95 caracteres · codificación Unicode (70 por SMS; 67 si se divide)". */
    fun descripcionTamanio(t: String): String {
        val m = medir(t)
        val uni = m[3] == SmsMessage.ENCODING_16BIT
        val cod = if (uni) "Unicode (70 por SMS; 67 si se divide)" else "estándar (160 por SMS; 153 si se divide)"
        val causa = if (uni) "\nFuerzan Unicode: " + caracteresUnicode(t) else ""
        return "${m[0]} SMS · ${t.length} caracteres · codificación $cod$causa"
    }

    /** Línea corta para el editor: "142 caracteres · 1 SMS". */
    fun resumenCorto(t: String): String {
        val n = partes(t)
        return "${t.length} caracteres · $n SMS" + if (n > 1) " (se divide)" else ""
    }

    fun permitido(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Manda un SMS (dividido si hace falta) con la SIM predeterminada. [ref] identifica el envío para la app:
     * cuando Android confirma todas las partes (o falla alguna) se llama a [Manejador.alResultadoSms] con esa ref.
     * Devuelve null si se le pasó a Android, o el motivo por el que no se pudo.
     */
    @android.annotation.SuppressLint("MissingPermission")   // el permiso se revisa con permitido()
    fun enviar(ctx: Context, numero: String, texto: String, ref: String, etiqueta: String = numero): String? {
        if (!permitido(ctx)) return "falta el permiso de SMS".also { Registro.anotar(ctx, "SMS a $etiqueta NO enviado: $it") }
        val num = numero.trim()
        if (num.count { it.isDigit() } < 6) return "número inválido".also { Registro.anotar(ctx, "SMS a $etiqueta NO enviado: $it") }
        return try {
            val sms = ctx.getSystemService(SmsManager::class.java) ?: return "el celular no puede mandar SMS"
            val partes = sms.divideMessage(texto)
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val base = p.getInt("ultimo_codigo", 0)
            p.edit().putInt("ultimo_codigo", (base + partes.size) % 1_000_000).putString(ref, "0|${partes.size}|").commit()
            val avisos = ArrayList<PendingIntent>()
            partes.indices.forEach { i ->
                val resultado = Intent(ctx, SmsResultadoReceiver::class.java).setAction(ACCION_ENVIADO)
                    .putExtra(EXTRA_REF, ref).putExtra(EXTRA_ETIQUETA, etiqueta)
                // Mutable: Android completa el resultado del envío en el intent (es explícito, a un receiver propio).
                avisos += PendingIntent.getBroadcast(ctx, CODIGO_BASE + (base + i) % 1_000_000, resultado,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            }
            sms.sendMultipartTextMessage(num, null, partes, avisos, null)
            Registro.anotar(ctx, "SMS a $etiqueta pasado a Android (${partes.size} parte(s))")
            null
        } catch (e: Exception) {
            Registro.anotar(ctx, "Falló el SMS a $etiqueta: ${e.message}")
            "error al mandar: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Texto de un código de error de Android (resultado del envío). */
    fun error(codigo: Int): String = when (codigo) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "falla general"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "sin señal"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "modo avión o radio apagada"
        SmsManager.RESULT_ERROR_NULL_PDU -> "mensaje inválido"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "límite de SMS superado"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED, SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED ->
            "número corto no permitido"
        else -> "error $codigo"
    }

    internal const val PREFS = "sms_envios"
    internal const val ACCION_ENVIADO = "com.mis.nucleo.SMS_ENVIADO"
    internal const val EXTRA_REF = "ref"
    internal const val EXTRA_ETIQUETA = "etiqueta"
    private const val CODIGO_BASE = 5_000_000
}

/**
 * Resultado de cada parte de un SMS. Con todas confirmadas avisa a la app que salió; con la primera
 * que falla, avisa que falló (las demás partes de ese SMS ya no cambian el resultado).
 */
class SmsResultadoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Sms.ACCION_ENVIADO) return
        val ref = i.getStringExtra(Sms.EXTRA_REF) ?: return
        val etiqueta = i.getStringExtra(Sms.EXTRA_ETIQUETA) ?: ""
        val p = ctx.getSharedPreferences(Sms.PREFS, Context.MODE_PRIVATE)
        val estado = p.getString(ref, null)?.split("|") ?: return   // ya se informó
        var ok = estado[0].toIntOrNull() ?: 0
        val total = estado.getOrNull(1)?.toIntOrNull() ?: 1
        if (resultCode != Activity.RESULT_OK) {
            p.edit().remove(ref).commit()
            val motivo = Sms.error(resultCode)
            Registro.anotar(ctx, "SMS a $etiqueta NO salió ($motivo)")
            Nucleo.manejador.alResultadoSms(ctx, ref, false, motivo)
            return
        }
        ok++
        if (ok < total) { p.edit().putString(ref, "$ok|$total|").commit(); return }
        p.edit().remove(ref).commit()
        Registro.anotar(ctx, "SMS a $etiqueta enviado (confirmado por Android)")
        Nucleo.manejador.alResultadoSms(ctx, ref, true, "")
    }
}

/** Estilo de los diálogos del núcleo (el mismo que usan las apps). */
private fun Activity.temaNucleo(): Int = R.style.DialogoApp

private fun Activity.toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

/**
 * Si el SMS ocupa más de una parte, muestra el aviso: Enviar igual / Editar SMS / No enviar SMS
 * (y "Convertir caracteres" si cambia algo). [alDecidir] recibe el texto a enviar, o null si no se envía.
 * Igual que Recordatorios.
 */
fun Activity.controlarTamanioSms(texto: String, destinatarios: Int, alDecidir: (String?) -> Unit) {
    val partes = Sms.partes(texto)
    if (partes <= 1) { alDecidir(texto); return }
    var decidido = false
    val c = ContextThemeWrapper(this, temaNucleo())
    val m = (20 * resources.displayMetrics.density).toInt()
    val caja = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(m, m / 2, m, 0)
        addView(TextView(c).apply {
            text = "Tamaño: ${Sms.descripcionTamanio(texto)}\n" +
                "Destinatarios por SMS: $destinatarios (cada uno recibe $partes SMS).\n\nTexto:\n$texto"
        })
    }
    var dialogo: AlertDialog? = null
    if (Sms.convertible(texto)) caja.addView(Button(c).apply {
        text = "Convertir caracteres"
        setOnClickListener {
            decidido = true
            dialogo?.dismiss()
            controlarTamanioSms(Sms.convertirGsm(texto), destinatarios, alDecidir)   // vuelve a validar
        }
    })
    dialogo = AlertDialog.Builder(this, temaNucleo())
        .setTitle("El SMS se dividiría en $partes partes")
        .setView(ScrollView(c).apply { addView(caja) })
        .setPositiveButton("Enviar igual") { _, _ -> decidido = true; alDecidir(texto) }
        .setNeutralButton("Editar SMS") { _, _ -> decidido = true; editarSms(texto, destinatarios, alDecidir) }
        .setNegativeButton("No enviar SMS") { _, _ -> decidido = true; alDecidir(null) }
        .setOnDismissListener { if (!decidido) alDecidir(null) }
        .show()
}

/** Editor del SMS con contador en vivo de caracteres y cantidad de SMS. Solo afecta a ese envío. */
fun Activity.editarSms(texto: String, destinatarios: Int, alDecidir: (String?) -> Unit) {
    val c = ContextThemeWrapper(this, temaNucleo())
    val m = (20 * resources.displayMetrics.density).toInt()
    val contador = TextView(c).apply { textSize = 13f }
    val campo = EditText(c).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 4
        setText(texto)
    }
    fun refrescar() {
        val t = campo.text.toString()
        contador.text = Sms.descripcionTamanio(t) + if (Sms.partes(t) <= 1) "  ✓ entra en 1 SMS" else ""
    }
    campo.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = refrescar()
    })
    refrescar()
    val caja = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(m, m / 2, m, 0)
        addView(campo); addView(contador)
        addView(TextView(c).apply { text = "Solo cambia este envío."; textSize = 12f })
    }
    var decidido = false
    AlertDialog.Builder(this, temaNucleo())
        .setTitle("Editar SMS")
        .setView(ScrollView(c).apply { addView(caja) })
        .setPositiveButton("Enviar") { _, _ ->
            decidido = true
            val nuevo = campo.text.toString()
            if (nuevo.isBlank()) { toast("El SMS quedó vacío: no se envía"); alDecidir(null) }
            else controlarTamanioSms(nuevo, destinatarios, alDecidir)   // si sigue largo, vuelve a avisar
        }
        .setNegativeButton("No enviar SMS") { _, _ -> decidido = true; alDecidir(null) }
        .setOnDismissListener { if (!decidido) alDecidir(null) }
        .show()
}
