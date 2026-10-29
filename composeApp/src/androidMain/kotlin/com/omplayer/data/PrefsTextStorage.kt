package com.omplayer.data

import android.content.Context

class PrefsTextStorage(context: Context) : TextStorage {
    private val prefs = context.applicationContext.getSharedPreferences("om_user_library", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("text", null)
    override fun write(text: String) { prefs.edit().putString("text", text).apply() }
}
