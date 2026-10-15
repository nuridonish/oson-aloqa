package com.osonaloqa.app

import android.content.Context

object Pref {
    private const val NAME = "osonaloqa"

    fun save(context: Context, name: String, e164: String) {
        sp(context).edit().putString("name", name).putString("e164", e164).apply()
    }

    fun name(context: Context): String = sp(context).getString("name", "") ?: ""

    fun e164(context: Context): String = sp(context).getString("e164", "") ?: ""

    fun registered(context: Context): Boolean = e164(context).isNotEmpty()

    fun logout(context: Context) {
        sp(context).edit().clear().apply()
    }

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
