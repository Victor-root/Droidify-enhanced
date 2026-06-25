package com.looker.droidify.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's tracked GitHub app sources as a JSON file in the app's storage. Mirrors
 * [com.looker.droidify.datastore.CustomButtonRepository] so we avoid a Room schema migration for
 * what is a short, user-managed list.
 */
@Singleton
class GithubAppRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var isLoaded = false
    private val _apps = MutableStateFlow<List<GithubApp>>(emptyList())

    val apps: Flow<List<GithubApp>> = flow {
        ensureLoaded()
        emitAll(_apps)
    }

    suspend fun getApps(): List<GithubApp> {
        ensureLoaded()
        return _apps.value
    }

    /** Adds [app] unless an entry with the same owner/repo is already tracked. */
    suspend fun addApp(app: GithubApp) {
        mutex.withLock {
            ensureLoadedInternal()
            if (_apps.value.any { it.key == app.key }) return@withLock
            val updated = _apps.value + app
            saveToFile(updated)
            _apps.value = updated
        }
    }

    /** Replaces the tracked app sharing [GithubApp.key], or adds it when absent. */
    suspend fun upsertApp(app: GithubApp) {
        mutex.withLock {
            ensureLoadedInternal()
            val updated = if (_apps.value.any { it.key == app.key }) {
                _apps.value.map { if (it.key == app.key) app else it }
            } else {
                _apps.value + app
            }
            saveToFile(updated)
            _apps.value = updated
        }
    }

    suspend fun removeApp(key: String) {
        mutex.withLock {
            ensureLoadedInternal()
            val updated = _apps.value.filter { it.key != key }
            saveToFile(updated)
            _apps.value = updated
        }
    }

    private suspend fun ensureLoaded() {
        if (!isLoaded) {
            mutex.withLock { ensureLoadedInternal() }
        }
    }

    private suspend fun ensureLoadedInternal() {
        if (!isLoaded) {
            _apps.value = loadFromFile()
            isLoaded = true
        }
    }

    private suspend fun loadFromFile(): List<GithubApp> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString(ListSerializer(GithubApp.serializer()), file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun saveToFile(apps: List<GithubApp>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(json.encodeToString(ListSerializer(GithubApp.serializer()), apps))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val FILE_NAME = "github_apps.json"
    }
}
