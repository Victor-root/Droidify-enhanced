package com.looker.droidify.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.looker.droidify.compose.appDetail.navigation.appDetail
import com.looker.droidify.compose.appDetail.navigation.navigateToAppDetail
import com.looker.droidify.compose.appList.navigation.AppList
import com.looker.droidify.compose.appList.navigation.appList
import com.looker.droidify.compose.appList.navigation.navigateToAppList
import com.looker.droidify.compose.home.navigation.home
import com.looker.droidify.compose.repoDetail.navigation.navigateToRepoDetail
import com.looker.droidify.compose.repoDetail.navigation.repoDetail
import com.looker.droidify.compose.repoEdit.navigation.navigateToRepoEdit
import com.looker.droidify.compose.repoEdit.navigation.repoEdit
import com.looker.droidify.compose.repoList.navigation.navigateToRepoList
import com.looker.droidify.compose.repoList.navigation.repoList
import com.looker.droidify.compose.settings.navigation.navigateToSettings
import com.looker.droidify.compose.settings.navigation.settings
import com.looker.droidify.compose.theme.DroidifyTheme
import com.looker.droidify.data.RepoRepository
import com.looker.droidify.datastore.SettingsRepository
import com.looker.droidify.datastore.extension.getThemeRes
import com.looker.droidify.datastore.get
import com.looker.droidify.datastore.model.Theme
import com.looker.droidify.model.Repository
import com.looker.droidify.utility.common.SdkCheck
import com.looker.droidify.utility.common.requestNotificationPermission
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainComposeActivity : ComponentActivity() {

    @Inject
    lateinit var repository: RepoRepository

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    private data class ThemeState(
        val theme: Theme,
        val dynamicTheme: Boolean,
        val themeColor: Int,
    )

    /**
     * Reads the current theme/accent settings, applies the matching XML theme, and re-creates the
     * activity whenever they change — mirroring [com.looker.droidify.MainActivity] so both entry
     * points stay visually identical. Uses an entry point (not field injection) because the value is
     * needed before super.onCreate().
     */
    private fun collectThemeChanges(): ThemeState {
        val entryPoint = EntryPointAccessors.fromApplication(this, SettingsEntryPoint::class.java)
        val themeFlow = entryPoint.settingsRepository()
            .get { ThemeState(theme, dynamicTheme, themeColor) }
        val initial = runBlocking { themeFlow.first() }
        setTheme(
            resources.configuration.getThemeRes(
                theme = initial.theme,
                dynamicTheme = initial.dynamicTheme,
            ),
        )
        lifecycleScope.launch {
            themeFlow.drop(1).collect { recreate() }
        }
        return initial
    }

    /**
     * Generates an MD3 palette from the chosen accent color and applies it to this activity, so the
     * Compose screens follow the user's color. Skipped when Material You is on (S+).
     */
    private fun applyAccentColor(state: ThemeState) {
        if (state.dynamicTheme && SdkCheck.isSnowCake) return
        val options = DynamicColorsOptions.Builder()
            .setContentBasedSource(state.themeColor)
            .build()
        DynamicColors.applyToActivityIfAvailable(this, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeState = collectThemeChanges()
        super.onCreate(savedInstanceState)
        applyAccentColor(themeState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            if (repository.repos.first().isEmpty()) {
                Repository.defaultRepositories.forEach {
                    repository.insertRepo(it.address, it.fingerprint, null, null, it.name, it.description)
                }
            }
            // Enable (and therefore sync) the repos that are enabled by default but aren't yet, so a
            // fresh install fills its catalog automatically instead of showing an empty list.
            val enabledByDefault = Repository.defaultRepositories
                .filter { it.enabled }
                .map { it.address }
                .toSet()
            repository.repos.first()
                .filter { it.address in enabledByDefault && !it.enabled }
                .forEach { repository.enableRepository(it, enable = true) }
        }
        requestNotificationPermission(request = notificationPermission::launch)
        setContent {
            val darkTheme = when (themeState.theme) {
                Theme.DARK, Theme.AMOLED -> true
                Theme.LIGHT -> false
                Theme.SYSTEM, Theme.SYSTEM_BLACK -> isSystemInDarkTheme()
            }
            DroidifyTheme(
                darkTheme = darkTheme,
                dynamicColor = themeState.dynamicTheme,
            ) {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        modifier = Modifier.padding(innerPadding),
                        navController = navController,
                        startDestination = AppList,
                    ) {
                        home(
                            onNavigateToApps = { navController.navigateToAppList() },
                            onNavigateToRepos = { navController.navigateToRepoList() },
                            onNavigateToSettings = { navController.navigateToSettings() },
                        )
                        appList(
                            onAppClick = { packageName ->
                                navController.navigateToAppDetail(packageName)
                            },
                            onNavigateToRepos = { navController.navigateToRepoList() },
                            onNavigateToSettings = { navController.navigateToSettings() },
                        )

                        repoList(
                            onRepoClick = { repoId -> navController.navigateToRepoDetail(repoId) },
                            onBackClick = { navController.popBackStack() },
                        )

                        appDetail(
                            onBackClick = { navController.popBackStack() },
                        )

                        repoDetail(
                            onBackClick = { navController.popBackStack() },
                            onEditClick = { repoId ->
                                navController.navigateToRepoEdit(repoId)
                            },
                        )

                        repoEdit(onBackClick = { navController.popBackStack() })

                        settings(onBackClick = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
