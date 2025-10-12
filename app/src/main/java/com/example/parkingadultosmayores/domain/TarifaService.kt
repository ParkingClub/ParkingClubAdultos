// file: domain/TarifaService.kt
package com.example.parkingadultosmayores.domain

import com.example.parkingadultosmayores.data.model.IngresoRecord
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.max

/**
 * Servicio central de tarifas y cálculo de cobros.
 * Mantener AQUÍ la tabla de precios y reglas.
 *
 * Política unificada (simple/offline):
 *  - Jornadas soportadas: Dia, Noche, Diario, Nocturno, Completo (alias de Diario/Nocturno = tarifa plana)
 *  - Tarifas base (mismas que venías cobrando en Salida; añadimos "Completo" como alias):
 *      Carro: Dia=0.75, Noche=1.00, Diario=5.00, Nocturno=5.00, Completo=5.00
 *      Moto : Dia=0.75, Noche=1.00, Diario=5.00, Nocturno=5.00, Completo=5.00
 *  - Tarifa plana: Diario/Nocturno/Completo cobran base una sola vez (no por horas).
 *  - No plana: redondeo hacia arriba por hora, mínimo 1h, máximo 6h.
 */
object TarifaService {

    // Normaliza textos para ser tolerantes a mayúsculas/minúsculas
    private fun n(s: String?) = (s ?: "").trim().lowercase(Locale.getDefault())

    fun esTarifaPlana(jornada: String): Boolean {
        return when (n(jornada)) {
            "diario", "nocturno", "completo" -> true
            else -> false
        }
    }

    fun tarifaBase(tipoVehiculo: String, jornada: String): Double {
        val t = n(tipoVehiculo)
        val j = n(jornada)

        return when (t) {
            "carro" -> when (j) {
                "dia"       -> 0.75
                "noche"     -> 1.00
                "diario"    -> 5.00
                "nocturno"  -> 5.00
                "completo"  -> 5.00 // alias (plana)
                else        -> 0.0
            }
            "moto" -> when (j) {
                "dia"       -> 0.75
                "noche"     -> 1.00
                "diario"    -> 5.00
                "nocturno"  -> 5.00
                "completo"  -> 5.00 // alias (plana)
                else        -> 0.0
            }
            else -> 0.0
        }
    }

    data class CalculoCobro(
        val minutos: Long,
        val horasCobradas: Int,
        val base: Double,
        val total: Double,
        val horaSalida: String,
        val esTarifaPlana: Boolean
    )

    /**
     * Cálculo centralizado del cobro.
     * - Si es tarifa plana: total = base
     * - Si NO es plana: total = base * horasCobradas (ceil), min 1, max 6
     */
    fun calcularCobro(
        rec: IngresoRecord,
        now: Date = Date(),
        maxHorasNoPlanas: Int = 6
    ): CalculoCobro {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val entrada: Date? = try { sdf.parse("${rec.fecha} ${rec.hora}") } catch (_: Exception) { null }

        val minutos = if (entrada != null) ((now.time - entrada.time) / 60000L).coerceAtLeast(0L) else 0L
        val base = tarifaBase(rec.tipoVehiculo, rec.jornada)
        val plana = esTarifaPlana(rec.jornada)

        val horasRedondeadas = max(1, ceil(minutos / 60.0).toInt())
        val horasCobradas = if (plana) 1 else horasRedondeadas.coerceAtMost(maxHorasNoPlanas)

        val total = if (plana) base else base * horasCobradas
        val horaSalida = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        return CalculoCobro(minutos, horasCobradas, base, total, horaSalida, plana)
    }
}
