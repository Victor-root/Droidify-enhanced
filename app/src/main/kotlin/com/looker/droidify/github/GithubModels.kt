package com.looker.droidify.github

import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A GitHub project tracked as an app source (Obtainium-style): Droidify fetches its latest release
 * and installs / updates the APK directly, without a real F-Droid repository.
 *
 * Persisted as JSON (see [GithubAppRepository]); [packageName] and [installedTag] are filled in once
 * the user installs a release, so updates can be detected against the latest release tag.
 */
@Serializable
data class GithubApp(
    val owner: String,
    val repo: String,
    val label: String = repo,
    /** Resolved from the installed APK's manifest; null until first installed. */
    val packageName: String? = null,
    /** Release tag the user last installed (e.g. "v1.2.3"). */
    val installedTag: String? = null,
    /** Most recent release tag seen on GitHub. */
    val latestTag: String? = null,
    /** Whether to consider pre-releases when picking the latest release. */
    val includePrereleases: Boolean = false,
) {
    /** Stable identity for lists / de-duplication. */
    val key: String get() = "$owner/$repo"

    val webUrl: String get() = "https://github.com/$owner/$repo"

    /** A newer release than the one installed is available. */
    val hasUpdate: Boolean
        get() = installedTag != null && latestTag != null && latestTag != installedTag
}

/** Minimal view of a GitHub release (the API returns far more fields, which we ignore). */
@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
data class GithubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
)

/**
 * Parses a GitHub project reference from common forms the user might paste:
 * `owner/repo`, `https://github.com/owner/repo`, `.../owner/repo/releases`, with or without `.git`.
 * Returns null when it can't find an `owner/repo` pair.
 */
fun parseGithubRepo(input: String): Pair<String, String>? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withoutScheme = trimmed
        .substringAfter("://", trimmed)
        .removePrefix("github.com/")
        .removePrefix("www.github.com/")
        .removePrefix("api.github.com/repos/")
    val segments = withoutScheme.split('/').filter { it.isNotBlank() }
    if (segments.size < 2) return null
    val owner = segments[0]
    val repo = segments[1].removeSuffix(".git")
    if (owner.isBlank() || repo.isBlank()) return null
    return owner to repo
}

/**
 * Picks the APK asset best suited to this device from a release's [assets].
 *
 * GitHub assets carry no ABI metadata, only file names, so we match the device's ABIs (most
 * preferred first) against the name, then fall back to a universal build, then the only/first APK.
 * Returns null when the release ships no APK.
 */
fun selectApkAsset(
    assets: List<GithubAssetDto>,
    deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
): GithubAssetDto? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apks.size <= 1) return apks.firstOrNull()

    // Try each ABI the device supports, in preference order, with a few common name aliases.
    for (abi in deviceAbis) {
        val aliases = abiAliases(abi)
        val match = apks.firstOrNull { apk ->
            val name = apk.name.lowercase()
            aliases.any { name.contains(it) }
        }
        if (match != null) return match
    }
    // No ABI-specific build matched: prefer a universal one, else just the first APK.
    return apks.firstOrNull { apk ->
        val name = apk.name.lowercase()
        "universal" in name || "noarch" in name || "all" in name
    } ?: apks.first()
}

private fun abiAliases(abi: String): List<String> = when (abi) {
    "arm64-v8a" -> listOf("arm64-v8a", "arm64", "aarch64")
    "armeabi-v7a" -> listOf("armeabi-v7a", "armeabi", "armv7", "arm32")
    "x86_64" -> listOf("x86_64", "x64")
    "x86" -> listOf("x86")
    else -> listOf(abi.lowercase())
}
