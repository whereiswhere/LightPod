package com.lightpod.data

import android.content.Context

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("lightpod_prefs", Context.MODE_PRIVATE)

    var hasSeenOnboarding: Boolean
        get()      = prefs.getBoolean("has_seen_onboarding", false)
        set(value) { prefs.edit().putBoolean("has_seen_onboarding", value).apply() }
}
