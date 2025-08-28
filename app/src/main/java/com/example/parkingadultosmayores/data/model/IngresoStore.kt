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

    // Mantiene el último día en el que se guardó algo (yyyy-MM-dd)
    private val LAST_DAY = stringPreferencesKey("last_day")
    private fun keyForDay(day: String) = stringPreferencesKey("ingresos_$day")

    // --------- API pública ---------

    /** Housekeeping diario: si cambió el día, borra el del día anterior. Llamar al iniciar la app. */
    suspend fun dailyHousekeeping(context: Context) {
        context.ingresosDataStore.edit { prefs ->
            val today = currentDayStr()
            val last = prefs[LAST_DAY]
            if (last != null && last != today) {
                // Borra el bucket del día anterior (el último guardado)
                prefs.remove(keyForDay(last))
            }
            // Actualiza el día visto (no crea bucket aún)
            prefs[LAST_DAY] = today
        }
    }

    /** Agrega un ingreso y, si cambió el día, borra automáticamente el del día anterior. */
    suspend fun add(context: Context, record: IngresoRecord) {
        val day = dayFromDate(record.fecha) ?: currentDayStr()

        context.ingresosDataStore.edit { prefs ->
            val last = prefs[LAST_DAY]
            if (last != null && last != day) {
                // Borra el día anterior guardado
                prefs.remove(keyForDay(last))
            }

            val list = getListFromPrefs(prefs, day).toMutableList()
            list.add(record)
            prefs[keyForDay(day)] = gson.toJson(list)
            prefs[LAST_DAY] = day
        }
    }

    /** Devuelve los ingresos del día actual (según fecha del dispositivo). */
    suspend fun getToday(context: Context): List<IngresoRecord> {
        val day = currentDayStr()
        val prefs = context.ingresosDataStore.data.first()
        return getListFromPrefs(prefs, day)
    }

    suspend fun getById(ctx: Context, id: String): IngresoRecord? {
        return getToday(ctx).firstOrNull { it.id == id }
    }

    suspend fun getLatestByPlaca(ctx: Context, placa: String): IngresoRecord? {
        val p = placa.uppercase(Locale.getDefault())
        return getToday(ctx)
            .filter { it.placa.equals(p, ignoreCase = true) }
            .maxByOrNull { it.hora }
    }

    suspend fun removeById(ctx: Context, id: String) {
        val day = currentDayStr()
        val updated = getToday(ctx).filterNot { it.id == id }
        saveListForDay(ctx, day, updated)
    }

    // --------- Helpers internos ---------

    private fun getListFromPrefs(prefs: Preferences, day: String): List<IngresoRecord> {
        val json = prefs[keyForDay(day)] ?: return emptyList()
        val type = object : TypeToken<List<IngresoRecord>>() {}.type
        return gson.fromJson<List<IngresoRecord>>(json, type) ?: emptyList()
    }

    private fun currentDayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun dayFromDate(dateStr: String): String? {
        // Esperamos "yyyy-MM-dd" en record.fecha
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.isLenient = false
            val date = sdf.parse(dateStr)
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date!!)
        } catch (_: ParseException) {
            null
        }
    }

    /** Guarda la lista completa para el día dado (normalmente el actual). */
    private suspend fun saveListForDay(ctx: Context, day: String, list: List<IngresoRecord>) {
        ctx.ingresosDataStore.edit { prefs ->
            prefs[keyForDay(day)] = gson.toJson(list)
            prefs[LAST_DAY] = day
        }
    }
}
