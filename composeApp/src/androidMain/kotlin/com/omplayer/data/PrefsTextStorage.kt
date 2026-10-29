package com.omplayer.data

import android.content.Context

class PrefsTextStorage(context: Context, name: String = "om_user_library") : TextStorage {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("text", null)
    override fun write(text: String) { prefs.edit().putString("text", text).apply() }
}
