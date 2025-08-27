// file: data/model/IngresoStore.kt
package com.example.parkingadultosmayores.data.model

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

private val Context.ingresosDataStore by preferencesDataStore(name = "ingresos_store")

object IngresoStore {
    private val gson = Gson()

    // Cambiamos “día” por “mes”
    private val LAST_MONTH = stringPreferencesKey("last_month")
    private fun keyForMonth(month: String) = stringPreferencesKey("ingresos_$month")

    /** Agrega un ingreso y limpia el mes anterior si cambió el mes */
    suspend fun add(context: Context, record: IngresoRecord) {
        // Derivamos el mes a partir de record.fecha ("yyyy-MM-dd"); si falla, usamos el mes actual del dispositivo
        val month = monthFromDate(record.fecha) ?: currentMonthStr()

        context.ingresosDataStore.edit { prefs ->
            val last = prefs[LAST_MONTH]
            if (last != null && last != month) {
                // Borra solo el mes inmediatamente anterior guardado
                prefs.remove(keyForMonth(last))
            }

            val list = getListFromPrefs(prefs, month).toMutableList()
            list.add(record)
            prefs[keyForMonth(month)] = gson.toJson(list)
            prefs[LAST_MONTH] = month
        }
    }

    /** Devuelve los ingresos del mes actual (según fecha del dispositivo) */
    suspend fun getThisMonth(context: Context): List<IngresoRecord> {
        val month = currentMonthStr()
        val prefs = context.ingresosDataStore.data.first()
        return getListFromPrefs(prefs, month)
    }

    private fun getListFromPrefs(prefs: Preferences, month: String): List<IngresoRecord> {
        val json = prefs[keyForMonth(month)] ?: return emptyList()
        val type = object : TypeToken<List<IngresoRecord>>() {}.type
        return gson.fromJson<List<IngresoRecord>>(json, type) ?: emptyList()
    }

    private fun currentMonthStr(): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    private fun monthFromDate(dateStr: String): String? {
        // Esperamos "yyyy-MM-dd" en record.fecha
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(dateStr)
            SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(date!!)
        } catch (_: ParseException) {
            null
        }
    }

    /** Guarda la lista completa para el mes dado (normalmente el actual) */
    private suspend fun saveListForMonth(ctx: Context, month: String, list: List<IngresoRecord>) {
        ctx.ingresosDataStore.edit { prefs ->
            prefs[keyForMonth(month)] = gson.toJson(list)
            prefs[LAST_MONTH] = month
        }
    }

    suspend fun getById(ctx: Context, id: String): IngresoRecord? {
        return getThisMonth(ctx).firstOrNull { it.id == id }
    }

    suspend fun getLatestByPlaca(ctx: Context, placa: String): IngresoRecord? {
        val p = placa.uppercase(Locale.getDefault())
        return getThisMonth(ctx)
            .filter { it.placa.equals(p, ignoreCase = true) }
            .maxByOrNull { it.hora }
    }

    suspend fun removeById(ctx: Context, id: String) {
        val month = currentMonthStr()
        val updated = getThisMonth(ctx).filterNot { it.id == id }
        saveListForMonth(ctx, month, updated)
    }
}
