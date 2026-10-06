package com.ourmine.caregov.demo

import android.content.Context
import androidx.core.content.edit

enum class DemoRole(val label: String, val accountName: String) {
    PATIENT("환자", "김영희"),
    GUARDIAN("보호자", "이준호"),
    MANAGER("매니저", "박서연"),
    OPERATOR("운영자", "운영팀"),
}

class DemoSession(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("demo_session", Context.MODE_PRIVATE)

    fun loadRole(): DemoRole {
        val stored = preferences.getString("role", null)
        return DemoRole.entries.firstOrNull { it.name == stored } ?: DemoRole.PATIENT
    }

    fun saveRole(role: DemoRole) {
        preferences.edit { putString("role", role.name) }
    }
}
