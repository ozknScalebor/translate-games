package com.ozkan.altyazi.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.remoteStore: DataStore<Preferences> by preferencesDataStore(name = "remote_play")

/**
 * Paired consoles and the PSN account id used for pairing, in app-private storage (backups are
 * disabled in the manifest, so the registration keys never leave the tablet).
 */
class ConsoleStore(context: Context) {

    private val store = context.applicationContext.remoteStore
    private val consolesKey = stringPreferencesKey("consoles")
    private val accountKey = stringPreferencesKey("psn_account_id")

    private val data = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val consoles: Flow<List<SavedConsole>> = data.map { ConsoleCodec.decode(it[consolesKey]) }.distinctUntilChanged()
    val accountId: Flow<String?> = data.map { it[accountKey] }.distinctUntilChanged()

    /** Adds [console], replacing an earlier pairing of the same console. */
    suspend fun save(console: SavedConsole) = edit { list -> list.filterNot { it.id == console.id } + console }

    suspend fun rename(id: String, name: String) = edit { list -> list.map { if (it.id == id) it.copy(name = name) else it } }

    /** Remembers the console's current address so it can be reached before discovery answers. */
    suspend fun updateHost(id: String, host: String) = edit { list ->
        list.map { if (it.id == id && it.host != host) it.copy(host = host) else it }
    }

    suspend fun remove(id: String) = edit { list -> list.filterNot { it.id == id } }

    suspend fun saveAccountId(accountId: String) {
        store.edit { it[accountKey] = accountId.trim() }
    }

    private suspend fun edit(transform: (List<SavedConsole>) -> List<SavedConsole>) {
        store.edit { prefs -> prefs[consolesKey] = ConsoleCodec.encode(transform(ConsoleCodec.decode(prefs[consolesKey]))) }
    }
}
