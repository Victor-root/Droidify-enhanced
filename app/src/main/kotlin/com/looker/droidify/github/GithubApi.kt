package com.looker.droidify.github

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny GitHub REST client for the Obtainium-style source feature. Reuses the app's shared Ktor
 * [HttpClient]. Unauthenticated, so it's subject to GitHub's 60 requests/hour rate limit — plenty
 * for occasionally adding a source and checking a handful of apps for updates.
 */
@Singleton
class GithubApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches the release Droidify should offer for `owner/repo`: the newest non-draft release
     * (optionally including pre-releases). Returns null on network/HTTP/parse failure or when the
     * project has no releases.
     */
    suspend fun latestRelease(
        owner: String,
        repo: String,
        includePrereleases: Boolean = false,
    ): GithubReleaseDto? = withContext(Dispatchers.IO) {
        runCatching {
            if (includePrereleases) {
                val text = getText("$API/repos/$owner/$repo/releases?per_page=10")
                    ?: return@runCatching null
                json.decodeFromString(ListSerializer(GithubReleaseDto.serializer()), text)
                    .firstOrNull { !it.draft }
            } else {
                val text = getText("$API/repos/$owner/$repo/releases/latest")
                    ?: return@runCatching null
                json.decodeFromString(GithubReleaseDto.serializer(), text)
            }
        }.getOrNull()
    }

    private suspend fun getText(url: String): String? {
        val response = httpClient.get(url) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        return if (response.status.isSuccess()) response.bodyAsText() else null
    }

    companion object {
        private const val API = "https://api.github.com"
    }
}
