package com.looker.droidify.compose.githubApps

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looker.droidify.data.model.PackageName
import com.looker.droidify.github.GithubApi
import com.looker.droidify.github.GithubApp
import com.looker.droidify.github.GithubAppRepository
import com.looker.droidify.github.parseGithubRepo
import com.looker.droidify.github.selectApkAsset
import com.looker.droidify.installer.InstallManager
import com.looker.droidify.installer.model.InstallItem
import com.looker.droidify.network.Downloader
import com.looker.droidify.network.NetworkResponse
import com.looker.droidify.utility.common.cache.Cache
import com.looker.droidify.utility.common.extension.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GithubAppsViewModel @Inject constructor(
    private val githubApi: GithubApi,
    private val repository: GithubAppRepository,
    private val downloader: Downloader,
    private val installManager: InstallManager,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val apps: StateFlow<List<GithubApp>> = repository.apps.asStateFlow(emptyList())

    /** Keys of apps with a network/install action in flight (drives per-row spinners). */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy

    val snackbarHostState = SnackbarHostState()

    /** Adds a GitHub project (any URL form) as a tracked source after confirming it has a release. */
    fun addSource(url: String, includePrereleases: Boolean) {
        val parsed = parseGithubRepo(url)
        if (parsed == null) {
            snack("Not a valid GitHub project URL")
            return
        }
        val (owner, repo) = parsed
        val key = "$owner/$repo"
        if (apps.value.any { it.key == key }) {
            snack("$repo is already in your list")
            return
        }
        viewModelScope.launch {
            withBusy(key) {
                val release = githubApi.latestRelease(owner, repo, includePrereleases)
                if (release == null) {
                    snack("No release found for $key (or rate-limited)")
                    return@withBusy
                }
                repository.addApp(
                    GithubApp(
                        owner = owner,
                        repo = repo,
                        latestTag = release.tagName,
                        includePrereleases = includePrereleases,
                    ),
                )
                snack("Added $repo")
            }
        }
    }

    /** Downloads the latest release's APK and installs it, recording the installed tag + package. */
    fun installOrUpdate(app: GithubApp) {
        viewModelScope.launch {
            withBusy(app.key) {
                val release = githubApi.latestRelease(app.owner, app.repo, app.includePrereleases)
                if (release == null) {
                    snack("Couldn't reach GitHub for ${app.repo}")
                    return@withBusy
                }
                val asset = selectApkAsset(release.assets)
                if (asset == null) {
                    snack("The latest ${app.repo} release has no APK")
                    return@withBusy
                }
                val cacheFileName = "${app.owner}_${app.repo}_${release.tagName}.apk"
                    .replace(UNSAFE_FILE_CHARS, "_")
                val releaseFile = Cache.getReleaseFile(context, cacheFileName)
                val response = withContext(Dispatchers.IO) {
                    // Download to a partial file and promote it on success. The Downloader resumes by
                    // Range against the target's current size, so a previously-completed file would
                    // make it request past EOF -> HTTP 416 -> failure. Start each download fresh
                    // (GitHub asset URLs are one-shot CDN links anyway, so resuming wouldn't help).
                    val partial = Cache.getPartialReleaseFile(context, cacheFileName)
                    partial.delete()
                    val result = downloader.downloadToFile(
                        url = asset.browserDownloadUrl,
                        target = partial,
                    )
                    if (result is NetworkResponse.Success) {
                        partial.copyTo(releaseFile, overwrite = true)
                        partial.delete()
                    }
                    result
                }
                if (response !is NetworkResponse.Success) {
                    snack("Download failed for ${app.repo}")
                    return@withBusy
                }
                // GitHub APKs aren't pre-registered like F-Droid ones, so read the package name from
                // the downloaded file to drive the installer and detect future updates.
                val packageName = context.packageManager
                    .getPackageArchiveInfo(releaseFile.absolutePath, 0)
                    ?.packageName
                if (packageName == null) {
                    snack("Downloaded file isn't a valid APK")
                    return@withBusy
                }
                installManager.install(InstallItem(PackageName(packageName), cacheFileName))
                repository.upsertApp(
                    app.copy(
                        packageName = packageName,
                        installedTag = release.tagName,
                        latestTag = release.tagName,
                    ),
                )
            }
        }
    }

    /** Re-checks every tracked app for a newer release tag (e.g. on opening the screen). */
    fun refresh() {
        viewModelScope.launch {
            apps.value.forEach { app ->
                val release = githubApi.latestRelease(app.owner, app.repo, app.includePrereleases)
                if (release != null && release.tagName != app.latestTag) {
                    repository.upsertApp(app.copy(latestTag = release.tagName))
                }
            }
        }
    }

    fun remove(key: String) {
        viewModelScope.launch { repository.removeApp(key) }
    }

    private fun setBusy(key: String, busy: Boolean) {
        _busy.value = if (busy) _busy.value + key else _busy.value - key
    }

    private suspend inline fun withBusy(key: String, block: () -> Unit) {
        setBusy(key, true)
        try {
            block()
        } finally {
            setBusy(key, false)
        }
    }

    private fun snack(message: String) {
        viewModelScope.launch { snackbarHostState.showSnackbar(message) }
    }
}

private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
