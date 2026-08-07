package com.example.smsforwarder.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ExecutionMode {
    FOREGROUND,
    BACKGROUND
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val EXECUTION_MODE_KEY = stringPreferencesKey("execution_mode")

    val executionMode: Flow<ExecutionMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[EXECUTION_MODE_KEY] ?: ExecutionMode.FOREGROUND.name
        runCatching { ExecutionMode.valueOf(modeStr) }.getOrDefault(ExecutionMode.FOREGROUND)
    }

    suspend fun setExecutionMode(mode: ExecutionMode) {
        context.dataStore.edit { prefs ->
            prefs[EXECUTION_MODE_KEY] = mode.name
        }
    }
}
