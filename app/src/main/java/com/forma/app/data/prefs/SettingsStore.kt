package com.forma.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
enum class WeightUnit(val label: String) { LB("Pounds (lb)"), KG("Kilograms (kg)") }
enum class LengthUnit(val label: String) { IN("Inches (in)"), CM("Centimetres (cm)") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val weightUnit: WeightUnit = WeightUnit.LB,
    val lengthUnit: LengthUnit = LengthUnit.IN,
)

private val Context.dataStore by preferencesDataStore(name = "forma_settings")

/** UI preferences live in DataStore; health data lives in Room. */
class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val weightKey = stringPreferencesKey("weight_unit")
    private val lengthKey = stringPreferencesKey("length_unit")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == p[themeKey] } ?: ThemeMode.SYSTEM,
            weightUnit = WeightUnit.entries.firstOrNull { it.name == p[weightKey] } ?: WeightUnit.LB,
            lengthUnit = LengthUnit.entries.firstOrNull { it.name == p[lengthKey] } ?: LengthUnit.IN,
        )
    }

    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[themeKey] = t.name }
    suspend fun setWeightUnit(u: WeightUnit) = context.dataStore.edit { it[weightKey] = u.name }
    suspend fun setLengthUnit(u: LengthUnit) = context.dataStore.edit { it[lengthKey] = u.name }
    suspend fun clear() = context.dataStore.edit { it.clear() }
}
