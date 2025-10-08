// file: data/model/RecaudacionStore.kt
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

private val Context.recaudsDataStore by preferencesDataStore(name = "recauds_store")

object RecaudacionStore {
    private val gson = Gson()

    private val LAST_DAY = stringPreferencesKey("last_day_recauds")
    private fun keyForDay(day: String) = stringPreferencesKey("recauds_$day")

    /** Llamar al iniciar la app: si cambió el día, borra el bucket anterior. */
    suspend fun dailyHousekeeping(context: Context) {
        context.recaudsDataStore.edit { prefs ->
            val today = currentDayStr()
            val last = prefs[LAST_DAY]
            if (last != null && last != today) {
                prefs.remove(keyForDay(last))
            }
            prefs[LAST_DAY] = today
        }
    }

    /** Agrega una recaudación. Si el día cambió, se limpia el bucket anterior. */
    suspend fun add(context: Context, record: RecaudacionRecord) {
        val day = dayFromDate(record.fecha) ?: currentDayStr()
        context.recaudsDataStore.edit { prefs ->
            val last = prefs[LAST_DAY]
            if (last != null && last != day) {
                prefs.remove(keyForDay(last))
            }
            val list = getListFromPrefs(prefs, day).toMutableList()
            list.add(record)
            prefs[keyForDay(day)] = gson.toJson(list)
            prefs[LAST_DAY] = day
        }
    }

    /** Lista completa de recaudaciones de HOY. */
    suspend fun getToday(context: Context): List<RecaudacionRecord> {
        val day = currentDayStr()
        val prefs = context.recaudsDataStore.data.first()
        return getListFromPrefs(prefs, day)
    }

    /** Total en $ de HOY. */
    suspend fun totalToday(context: Context): Double {
        return getToday(context).sumOf { it.monto }
    }

    /** Limpia explícitamente el bucket de HOY (opcional, para botón "Limpiar"). */
    suspend fun clearToday(context: Context) {
        val day = currentDayStr()
        context.recaudsDataStore.edit { prefs ->
            prefs.remove(keyForDay(day))
            prefs[LAST_DAY] = day
        }
    }

    // ---------- Helpers ----------
    private fun getListFromPrefs(prefs: Preferences, day: String): List<RecaudacionRecord> {
        val json = prefs[keyForDay(day)] ?: return emptyList()
        val type = object : TypeToken<List<RecaudacionRecord>>() {}.type
        return gson.fromJson<List<RecaudacionRecord>>(json, type) ?: emptyList()
    }

    private fun currentDayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun dayFromDate(dateStr: String): String? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(dateStr)
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date!!)
        } catch (_: ParseException) {
            null
        }
    }
}
