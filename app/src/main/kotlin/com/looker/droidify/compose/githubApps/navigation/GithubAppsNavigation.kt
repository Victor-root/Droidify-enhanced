package com.looker.droidify.compose.githubApps.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.looker.droidify.compose.githubApps.GithubAppsScreen
import kotlinx.serialization.Serializable

@Serializable
object GithubApps

fun NavController.navigateToGithubApps() {
    this.navigate(
        GithubApps,
        navOptions {
            launchSingleTop = true
            restoreState = true
        },
    )
}

fun NavGraphBuilder.githubApps(onBackClick: () -> Unit) {
    composable<GithubApps> {
        GithubAppsScreen(viewModel = hiltViewModel(), onBackClick = onBackClick)
    }
}
