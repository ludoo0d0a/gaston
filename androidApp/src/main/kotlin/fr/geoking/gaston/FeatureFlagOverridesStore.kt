package fr.geoking.gaston

import android.content.Context

/** Persists [FeatureFlag] runtime overrides across process restarts (dev only). */
object FeatureFlagOverridesStore {
    private const val PREFS_NAME = "feature_flag_overrides"

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = FeatureFlag.Key.entries.mapNotNull { key ->
            if (!prefs.contains(key.name)) return@mapNotNull null
            key to prefs.getBoolean(key.name, key.default)
        }.toMap()
        FeatureFlag.replaceOverrides(loaded)
    }

    fun persist(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        FeatureFlag.overrides.value.forEach { (key, enabled) ->
            editor.putBoolean(key.name, enabled)
        }
        editor.apply()
    }
}
