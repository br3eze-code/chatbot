package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.apiKeyDataStore: DataStore<Preferences> by preferencesDataStore(name = "atelier_api_keys")

class ApiKeyStore(private val context: Context) {

    private fun keyForProvider(providerWireId: String): Preferences.Key<String> =
        stringPreferencesKey("api_key_$providerWireId")

    private fun keyForPlugin(pluginId: String): Preferences.Key<String> =
        stringPreferencesKey("plugin_key_$pluginId")

    val allProviderKeysFlow: Flow<Map<String, String>> = context.apiKeyDataStore.data.map { prefs ->
        buildMap {
            AiProvider.entries.forEach { provider ->
                val value = prefs[keyForProvider(provider.wireId)].orEmpty()
                if (value.isNotBlank()) {
                    put(provider.wireId, value)
                }
            }
        }
    }

    val allPluginKeysFlow: Flow<Map<String, String>> = context.apiKeyDataStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { it.key.name.startsWith("plugin_key_") }
            .associate { it.key.name.removePrefix("plugin_key_") to (it.value as? String).orEmpty() }
            .filterValues { it.isNotBlank() }
    }

    suspend fun getProviderKey(providerWireId: String): String {
        val prefs = context.apiKeyDataStore.data.first()
        return prefs[keyForProvider(providerWireId)].orEmpty()
    }

    suspend fun saveProviderKey(providerWireId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        context.apiKeyDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(keyForProvider(providerWireId))
            } else {
                prefs[keyForProvider(providerWireId)] = trimmed
            }
        }
    }

    suspend fun getPluginKey(pluginId: String): String {
        val prefs = context.apiKeyDataStore.data.first()
        return prefs[keyForPlugin(pluginId)].orEmpty()
    }

    suspend fun savePluginKey(pluginId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        context.apiKeyDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(keyForPlugin(pluginId))
            } else {
                prefs[keyForPlugin(pluginId)] = trimmed
            }
        }
    }

    companion object {
        fun maskKey(raw: String): String {
            if (raw.isBlank()) return "Not configured"
            if (raw.length <= 8) return "••••••••"
            return "${raw.take(4)}••••${raw.takeLast(4)}"
        }
    }
}
