package com.looker.droidify.compose.repoList

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.looker.droidify.R
import com.looker.droidify.compose.components.BackButton
import com.looker.droidify.compose.externalApps.ExternalAppIcon
import com.looker.droidify.compose.externalApps.ExternalAppsViewModel
import com.looker.droidify.data.model.Repo
import com.looker.droidify.external.ExternalApp
import com.looker.droidify.utility.text.toAnnotatedString
import com.looker.droidify.compose.theme.AccentBarHeight
import com.looker.droidify.compose.theme.accentTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RepoListScreen(
    viewModel: RepoListViewModel,
    onRepoClick: (Int) -> Unit,
    onBackClick: () -> Unit,
    onAddRepo: () -> Unit,
) {
    val repos by viewModel.stream.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    // The External sources live alongside the F-Droid repos here: this screen is source management
    // (enable / disable / add / remove). The apps themselves (install, launch…) are on the External
    // tab. Both are backed by the same singleton repositories, so the lists stay in sync everywhere.
    val externalViewModel: ExternalAppsViewModel = hiltViewModel()
    val externalApps by externalViewModel.apps.collectAsStateWithLifecycle()
    val externalInstalledKeys by externalViewModel.installedKeys.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        externalViewModel.refreshInstalled()
        externalViewModel.reconcileInstalledLabels()
    }

    var showAddExternal by rememberSaveable { mutableStateOf(false) }
    var editingExternal by remember { mutableStateOf<ExternalApp?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(externalViewModel.snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    colors = accentTopAppBarColors(),
                    expandedHeight = AccentBarHeight,
                    title = { Text(text = stringResource(R.string.repositories)) },
                    navigationIcon = { BackButton(onBackClick) },
                    actions = {
                        IconButton(onClick = { showAddExternal = true }) {
                            Icon(
                                painterResource(R.drawable.ic_tabler_package),
                                contentDescription = stringResource(R.string.external_add_source),
                            )
                        }
                        IconButton(onClick = onAddRepo) {
                            Icon(
                                painterResource(R.drawable.ic_tabler_plus),
                                contentDescription = stringResource(R.string.add_repository),
                            )
                        }
                    },
                )
                if (isSyncing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(contentPadding = contentPadding) {
            item(key = "external-header") {
                SectionHeader(title = stringResource(R.string.tab_external))
            }
            if (externalApps.isEmpty()) {
                item(key = "external-empty") {
                    Text(
                        text = stringResource(R.string.external_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(externalApps, key = { "ext-${it.key}" }) { app ->
                ExternalSourceItem(
                    app = app,
                    isInstalled = app.key in externalInstalledKeys,
                    onToggle = { externalViewModel.setSourceEnabled(app, !app.enabled) },
                    onEdit = { editingExternal = app },
                    onRemove = { externalViewModel.remove(app.key) },
                )
            }

            item(key = "repos-header") {
                SectionHeader(title = stringResource(R.string.repo_section_fdroid))
            }
            items(repos, key = { "repo-${it.id}" }) { repo ->
                RepoItem(
                    onClick = { onRepoClick(repo.id) },
                    onToggle = { viewModel.toggleRepo(repo) },
                    repo = repo,
                )
            }
        }
    }

    if (showAddExternal) {
        AddExternalSourceDialog(
            onDismiss = { showAddExternal = false },
            onAdd = { url, includePrereleases, customName, muteUpdates ->
                externalViewModel.addSource(url, includePrereleases, customName, muteUpdates)
                showAddExternal = false
            },
        )
    }
    editingExternal?.let { app ->
        EditExternalSourceDialog(
            app = app,
            onDismiss = { editingExternal = null },
            onSave = { customName, includePrereleases, muteUpdates ->
                externalViewModel.updateSource(app, customName, includePrereleases, muteUpdates)
                editingExternal = null
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun RepoItem(
    onClick: () -> Unit,
    onToggle: () -> Unit,
    repo: Repo,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(modifier),
    ) {
        AsyncImage(
            model = repo.icon?.path,
            contentDescription = null,
            colorFilter = if (repo.enabled) null else GrayScaleColorFilter,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.large),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1F)) {
            Text(
                text = repo.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (repo.description.isNotEmpty()) {
                Text(
                    text = repo.description.toAnnotatedString { },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 4,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        FilledIconToggleButton(
            checked = repo.enabled,
            onCheckedChange = { onToggle() },
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
        }
    }
}

/** A tracked external source as a management row: logo, name, owner/repo, an enable/disable toggle
 *  (like a repo) and a remove button. App actions (install, launch…) intentionally live elsewhere,
 *  on the External tab — this row only manages the source. */
@Composable
private fun ExternalSourceItem(
    app: ExternalApp,
    isInstalled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val contentAlpha = if (app.enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        ExternalAppIcon(
            app = app,
            isInstalled = isInstalled,
            size = 48.dp,
            modifier = Modifier.alpha(contentAlpha),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(
            modifier = Modifier
                .weight(1F)
                .alpha(contentAlpha),
        ) {
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${app.provider.label} · ${app.path}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        FilledIconToggleButton(
            checked = app.enabled,
            onCheckedChange = { onToggle() },
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.external_edit_source),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_tabler_trash),
                contentDescription = stringResource(R.string.external_remove),
            )
        }
    }
}

@Composable
private fun AddExternalSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, includePrereleases: Boolean, customName: String, muteUpdates: Boolean) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var includePrereleases by rememberSaveable { mutableStateOf(false) }
    var muteUpdates by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_add_source)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.external_source_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                SourceOptionFields(
                    name = name,
                    onNameChange = { name = it },
                    nameHint = null,
                    includePrereleases = includePrereleases,
                    onPrereleasesChange = { includePrereleases = it },
                    muteUpdates = muteUpdates,
                    onMuteChange = { muteUpdates = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url, includePrereleases, name, muteUpdates) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.external_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Edits an existing external source's settings (the URL is fixed, so it isn't shown). */
@Composable
private fun EditExternalSourceDialog(
    app: ExternalApp,
    onDismiss: () -> Unit,
    onSave: (customName: String, includePrereleases: Boolean, muteUpdates: Boolean) -> Unit,
) {
    var name by rememberSaveable(app.key) {
        mutableStateOf(if (app.nameOverridden) app.label else "")
    }
    var includePrereleases by rememberSaveable(app.key) { mutableStateOf(app.includePrereleases) }
    var muteUpdates by rememberSaveable(app.key) { mutableStateOf(app.muteUpdates) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_edit_source)) },
        text = {
            Column {
                Text(
                    text = "${app.provider.label} · ${app.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                SourceOptionFields(
                    name = name,
                    onNameChange = { name = it },
                    nameHint = app.label,
                    includePrereleases = includePrereleases,
                    onPrereleasesChange = { includePrereleases = it },
                    muteUpdates = muteUpdates,
                    onMuteChange = { muteUpdates = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, includePrereleases, muteUpdates) }) {
                Text(stringResource(R.string.external_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Per-source option fields shared by the add and edit dialogs. */
@Composable
private fun SourceOptionFields(
    name: String,
    onNameChange: (String) -> Unit,
    nameHint: String?,
    includePrereleases: Boolean,
    onPrereleasesChange: (Boolean) -> Unit,
    muteUpdates: Boolean,
    onMuteChange: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.external_custom_name)) },
        placeholder = if (nameHint != null) {
            { Text(nameHint) }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    CheckboxRow(
        checked = includePrereleases,
        onCheckedChange = onPrereleasesChange,
        label = stringResource(R.string.external_include_prereleases),
    )
    CheckboxRow(
        checked = muteUpdates,
        onCheckedChange = onMuteChange,
        label = stringResource(R.string.external_mute_updates),
    )
}

@Composable
private fun CheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

val GrayScaleColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
