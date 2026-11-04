package com.apex.martyr

import android.content.Context

object Prefs {
    private const val PREF_NAME = "martyr_prefs"
    private const val KEY_LAST_VERSION = "last_opt_version"

    fun isOptimized(context: Context, currentVersion: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_VERSION, "") == currentVersion
    }

    fun saveOptimizedVersion(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_VERSION, version).apply()
    }
}
