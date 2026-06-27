package com.looker.droidify.external

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny REST client for the external-source feature, covering GitHub, GitLab and Codeberg (Gitea).
 * Reuses the app's shared Ktor [HttpClient]. Unauthenticated, so it's subject to each provider's
 * anonymous rate limit — plenty for occasionally adding a source and checking a handful of apps.
 */
@Singleton
class ExternalApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latestReleaseFor(app: ExternalApp): Release? =
        latestRelease(app.provider, app.owner, app.repo, app.includePrereleases)

    /**
     * Best-effort application id of the app a source builds, read from its `build.gradle`'s
     * `applicationId` (falling back to `namespace`) — the same trick Obtainium uses. Knowing the
     * package id lets an already-installed app be matched, so its real on-device name and icon show
     * before the user ever installs through us. GitHub and Codeberg/Gitea expose file contents the same
     * way; GitLab isn't covered (falls back to the repo name + avatar). Returns null when no build file
     * or id can be found. Never throws.
     */
    suspend fun fetchPackageId(app: ExternalApp): String? = withContext(Dispatchers.IO) {
        val base = when (app.provider) {
            SourceProvider.GITHUB ->
                "https://api.github.com/repos/${app.owner}/${app.repo}/contents"

            SourceProvider.CODEBERG ->
                "https://codeberg.org/api/v1/repos/${app.owner}/${app.repo}/contents"

            SourceProvider.GITLAB -> return@withContext null
        }
        val github = app.provider == SourceProvider.GITHUB
        for (path in BUILD_GRADLE_PATHS) {
            val text = runCatching { getText("$base/$path", github = github) }.getOrNull() ?: continue
            val source = decodeContentsBase64(text) ?: continue
            for (regex in PACKAGE_ID_REGEXES) {
                regex.find(source)?.let { return@withContext it.groupValues[1] }
            }
        }
        null
    }

    /**
     * The project README as HTML, for display on the detail screen. GitHub renders it for us
     * (Accept: application/vnd.github.html); the other providers have no equally simple endpoint, so
     * they return null for now. Returns null on any failure or when there is no README.
     */
    suspend fun readmeHtml(app: ExternalApp): String? = withContext(Dispatchers.IO) {
        runCatching {
            when (app.provider) {
                SourceProvider.GITHUB -> {
                    val response = httpClient.get(
                        "https://api.github.com/repos/${app.owner}/${app.repo}/readme",
                    ) {
                        header("Accept", "application/vnd.github.html")
                        header("X-GitHub-Api-Version", "2022-11-28")
                    }
                    if (response.status.isSuccess()) response.bodyAsText() else null
                }

                SourceProvider.GITLAB, SourceProvider.CODEBERG -> null
            }
        }.getOrNull()
    }

    /**
     * Fetches the release Droidify should offer for the project: the newest non-draft release that
     * actually ships an APK (optionally including pre-releases). Releases with no APK — e.g. a
     * server-only version bump — are skipped, since there's nothing to install from them and their
     * tag would otherwise be mistaken for a new app version. Returns null on network/HTTP/parse
     * failure or when no release in the recent window ships an APK.
     */
    suspend fun latestRelease(
        provider: SourceProvider,
        owner: String,
        repo: String,
        includePrereleases: Boolean = false,
    ): Release? = withContext(Dispatchers.IO) {
        runCatching {
            when (provider) {
                SourceProvider.GITHUB -> {
                    val text = getText(
                        url = "https://api.github.com/repos/$owner/$repo/releases?per_page=10",
                        github = true,
                    ) ?: return@runCatching null
                    decodeRest(text).firstMatching(includePrereleases)?.toRelease()
                }

                SourceProvider.CODEBERG -> {
                    val text = getText(
                        url = "https://codeberg.org/api/v1/repos/$owner/$repo/releases?limit=10",
                    ) ?: return@runCatching null
                    decodeRest(text).firstMatching(includePrereleases)?.toRelease()
                }

                SourceProvider.GITLAB -> {
                    val path = URLEncoder.encode("$owner/$repo", "UTF-8")
                    val text = getText(
                        url = "https://gitlab.com/api/v4/projects/$path/releases?per_page=10",
                    ) ?: return@runCatching null
                    decodeGitlab(text)
                        .firstOrNull {
                            (includePrereleases || !it.upcomingRelease) &&
                                it.assets.links.any { link ->
                                    link.name.endsWith(".apk", ignoreCase = true)
                                }
                        }
                        ?.toRelease()
                }
            }
        }.getOrNull()
    }

    private fun decodeRest(text: String): List<RestReleaseDto> =
        json.decodeFromString(ListSerializer(RestReleaseDto.serializer()), text)

    private fun decodeGitlab(text: String): List<GitlabReleaseDto> =
        json.decodeFromString(ListSerializer(GitlabReleaseDto.serializer()), text)

    private fun List<RestReleaseDto>.firstMatching(includePrereleases: Boolean): RestReleaseDto? =
        firstOrNull {
            !it.draft &&
                (includePrereleases || !it.prerelease) &&
                it.assets.any { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
        }

    private suspend fun getText(url: String, github: Boolean = false): String? {
        val response = httpClient.get(url) {
            if (github) {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        return if (response.status.isSuccess()) response.bodyAsText() else null
    }

    /** Decodes the base64 `content` field of a GitHub/Gitea "contents" API response into text. */
    private fun decodeContentsBase64(text: String): String? {
        val dto = runCatching {
            json.decodeFromString(ContentsDto.serializer(), text)
        }.getOrNull() ?: return null
        if (dto.encoding != "base64" || dto.content == null) return null
        return runCatching {
            String(Base64.decode(dto.content.replace("\n", ""), Base64.DEFAULT))
        }.getOrNull()
    }

    @Serializable
    private data class ContentsDto(val content: String? = null, val encoding: String? = null)

    private companion object {
        /** Where an Android app's `applicationId` usually lives, most likely first. */
        val BUILD_GRADLE_PATHS = listOf(
            "app/build.gradle.kts",
            "app/build.gradle",
            "android/app/build.gradle.kts",
            "android/app/build.gradle",
            "src/app/build.gradle",
        )

        /** `applicationId`, else `namespace`, in either Groovy or Kotlin-DSL form. */
        val PACKAGE_ID_REGEXES = listOf(
            Regex("""applicationId\s*[=(]?\s*["']([\w.]+)["']"""),
            Regex("""namespace\s*[=(]?\s*["']([\w.]+)["']"""),
        )
    }
}
