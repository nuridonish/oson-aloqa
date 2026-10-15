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

    fun netCall(context: Context): Boolean = sp(context).getBoolean("netcall", false)

    fun setNetCall(context: Context, on: Boolean) {
        sp(context).edit().putBoolean("netcall", on).apply()
    }

    fun sipHost(context: Context): String = sp(context).getString("sip_host", "") ?: ""
    fun sipUser(context: Context): String = sp(context).getString("sip_user", "") ?: ""
    fun sipPass(context: Context): String = sp(context).getString("sip_pass", "") ?: ""

    fun setSip(context: Context, host: String, user: String, pass: String) {
        sp(context).edit().putString("sip_host", host.trim()).putString("sip_user", user.trim()).putString("sip_pass", pass).apply()
    }

    fun sipConfigured(context: Context): Boolean = sipHost(context).isNotEmpty() && sipUser(context).isNotEmpty()

    fun pinned(context: Context): MutableSet<String> =
        (sp(context).getStringSet("pinned", setOf()) ?: setOf()).toMutableSet()

    fun setPinned(context: Context, set: Set<String>) {
        sp(context).edit().putStringSet("pinned", set).apply()
    }

    fun muted(context: Context): MutableSet<String> =
        (sp(context).getStringSet("muted", setOf()) ?: setOf()).toMutableSet()

    fun setMuted(context: Context, set: Set<String>) {
        sp(context).edit().putStringSet("muted", set).apply()
    }

    fun archived(context: Context): MutableSet<String> =
        (sp(context).getStringSet("archived", setOf()) ?: setOf()).toMutableSet()

    fun setArchived(context: Context, set: Set<String>) {
        sp(context).edit().putStringSet("archived", set).apply()
    }

    fun logout(context: Context) {
        sp(context).edit().clear().apply()
    }

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}




