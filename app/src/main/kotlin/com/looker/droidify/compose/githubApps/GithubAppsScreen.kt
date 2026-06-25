package com.looker.droidify.compose.githubApps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looker.droidify.compose.components.BackButton
import com.looker.droidify.github.GithubApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GithubAppsScreen(
    viewModel: GithubAppsViewModel,
    onBackClick: () -> Unit,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    // Refresh update status whenever the screen is opened.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub apps") },
                navigationIcon = { BackButton(onBackClick) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check for updates")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(viewModel.snackbarHostState) },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            AddSourceRow(onAdd = viewModel::addSource)
            HorizontalDivider()
            if (apps.isEmpty()) {
                Text(
                    text = "Add a GitHub project (e.g. github.com/owner/repo) to install and update " +
                        "its release APKs directly, like Obtainium.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn {
                items(items = apps, key = { it.key }) { app ->
                    GithubAppRow(
                        app = app,
                        busy = app.key in busy,
                        onAction = { viewModel.installOrUpdate(app) },
                        onRemove = { viewModel.remove(app.key) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AddSourceRow(onAdd: (url: String, includePrereleases: Boolean) -> Unit) {
    var url by remember { mutableStateOf("") }
    var includePrereleases by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("GitHub project URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includePrereleases, onCheckedChange = { includePrereleases = it })
            Text("Include pre-releases", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = {
                    onAdd(url, includePrereleases)
                    url = ""
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("Add")
            }
        }
    }
}

@Composable
private fun GithubAppRow(
    app: GithubApp,
    busy: Boolean,
    onAction: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = app.statusLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            val actionLabel = when {
                app.installedTag == null -> "Install"
                app.hasUpdate -> "Update"
                else -> "Reinstall"
            }
            Button(onClick = onAction) { Text(actionLabel) }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }
}

private fun GithubApp.statusLine(): String = when {
    installedTag == null -> "$key · latest ${latestTag ?: "?"}"
    hasUpdate -> "$key · $installedTag → ${latestTag ?: "?"}"
    else -> "$key · $installedTag (up to date)"
}
