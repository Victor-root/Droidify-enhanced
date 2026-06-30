package com.looker.droidify.compose.repoList

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.looker.droidify.R
import com.looker.droidify.compose.components.BackButton
import com.looker.droidify.compose.components.tvDpadDownTo
import com.looker.droidify.compose.externalApps.AddSourceState
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

    var showAddChooser by rememberSaveable { mutableStateOf(false) }
    var showAddExternal by rememberSaveable { mutableStateOf(false) }
    var editingExternal by remember { mutableStateOf<ExternalApp?>(null) }

    // Group sources whose names start the same way (e.g. "Brave Release" / "Brave Beta" / "Brave
    // Nightly" -> one "Brave (3)" group) so several sources for the same thing read as one entry. A
    // source with no sibling stays a plain row. Recomputed only when the underlying lists change.
    val externalGroups = remember(externalApps) { groupByName(externalApps) { it.label } }
    val repoGroups = remember(repos) { groupByName(repos) { it.name } }
    // Groups are shown expanded by default (nothing is hidden); this tracks the ones the user collapsed
    // to tidy the list. Keyed per section so an "ext" group and an "repo" group can't clash.
    var collapsedGroups by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val toggleGroup: (String) -> Unit = { key ->
        collapsedGroups = if (key in collapsedGroups) collapsedGroups - key else collapsedGroups + key
    }

    // TV / D-pad: the top bar doesn't release focus downward on its own; this lets "down" drop from the
    // header into the list. No effect on touch.
    val contentFocusRequester = remember { FocusRequester() }

    Scaffold(
        snackbarHost = { SnackbarHost(externalViewModel.snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    colors = accentTopAppBarColors(),
                    expandedHeight = AccentBarHeight,
                    modifier = Modifier.tvDpadDownTo(contentFocusRequester),
                    title = { Text(text = stringResource(R.string.repositories)) },
                    navigationIcon = { BackButton(onBackClick) },
                    actions = {
                        IconButton(onClick = { showAddChooser = true }) {
                            Icon(
                                painterResource(R.drawable.ic_tabler_plus),
                                contentDescription = stringResource(R.string.add_source_title),
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
        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier
                .focusRequester(contentFocusRequester)
                .focusGroup(),
        ) {
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
            groupedItems(
                sectionId = "ext",
                groups = externalGroups,
                collapsedGroups = collapsedGroups,
                onToggleGroup = toggleGroup,
                keyOf = { "ext-${it.key}" },
            ) { app ->
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
            groupedItems(
                sectionId = "repo",
                groups = repoGroups,
                collapsedGroups = collapsedGroups,
                onToggleGroup = toggleGroup,
                keyOf = { "repo-${it.id}" },
            ) { repo ->
                RepoItem(
                    onClick = { onRepoClick(repo.id) },
                    onToggle = { viewModel.toggleRepo(repo) },
                    repo = repo,
                )
            }
        }
    }

    if (showAddChooser) {
        AddSourceChooserDialog(
            onDismiss = { showAddChooser = false },
            onChooseFdroid = {
                showAddChooser = false
                onAddRepo()
            },
            onChooseExternal = {
                showAddChooser = false
                showAddExternal = true
            },
        )
    }
    if (showAddExternal) {
        val addState by externalViewModel.addState.collectAsStateWithLifecycle()
        // Keep the dialog up (with a spinner) until the add actually finishes, then close on success —
        // so a slow GitHub response can't make it look like nothing happened.
        LaunchedEffect(addState) {
            if (addState == AddSourceState.SUCCESS) {
                showAddExternal = false
                externalViewModel.consumeAddState()
            }
        }
        AddExternalSourceDialog(
            isLoading = addState == AddSourceState.LOADING,
            onDismiss = {
                showAddExternal = false
                externalViewModel.consumeAddState()
            },
            onAdd = { url, includePrereleases, customName, muteUpdates, apkFilter ->
                externalViewModel.addSource(
                    url = url,
                    includePrereleases = includePrereleases,
                    customName = customName,
                    muteUpdates = muteUpdates,
                    apkFilter = apkFilter,
                )
            },
        )
    }
    editingExternal?.let { app ->
        // Launcher icons found in the repo, for the picker. null = still loading, empty = none found.
        val iconCandidates by produceState<List<String>?>(initialValue = null, app.key) {
            value = externalViewModel.loadIconCandidates(app)
        }
        EditExternalSourceDialog(
            app = app,
            iconCandidates = iconCandidates,
            onDismiss = { editingExternal = null },
            onSave = { customName, includePrereleases, muteUpdates, apkFilter, iconUrl ->
                externalViewModel.updateSource(
                    app = app,
                    customName = customName,
                    includePrereleases = includePrereleases,
                    muteUpdates = muteUpdates,
                    apkFilter = apkFilter,
                    iconUrl = iconUrl,
                )
                editingExternal = null
            },
        )
    }
}

/** A set of sources whose names share a leading word, shown together under one collapsible header. */
private data class NameGroup<T>(val key: String, val title: String, val members: List<T>)

private val WHITESPACE = Regex("\\s+")

/** Buckets [items] by the first word of their name (case-insensitive), preserving first-seen order.
 *  A bucket with several members becomes a group titled by the words its names share (e.g. "Brave" or
 *  "Brave Browser"); a lone member keeps its full name. */
private fun <T> groupByName(items: List<T>, name: (T) -> String): List<NameGroup<T>> {
    val buckets = LinkedHashMap<String, MutableList<T>>()
    for (item in items) {
        val firstWord = name(item).trim().split(WHITESPACE).firstOrNull { it.isNotEmpty() }.orEmpty()
        buckets.getOrPut(firstWord.lowercase()) { mutableListOf() }.add(item)
    }
    return buckets.map { (key, members) ->
        val title = if (members.size >= 2) commonNamePrefix(members.map(name)) else name(members.first())
        NameGroup(key, title, members)
    }
}

/** The leading words shared by every name (e.g. ["Brave Release", "Brave Beta"] -> "Brave"), falling
 *  back to the first name if there's no shared word. */
private fun commonNamePrefix(names: List<String>): String {
    val tokens = names.map { it.trim().split(WHITESPACE).filter(String::isNotEmpty) }
    val minLen = tokens.minOf { it.size }
    val prefix = mutableListOf<String>()
    for (i in 0 until minLen) {
        val word = tokens.first()[i]
        if (tokens.all { it[i].equals(word, ignoreCase = true) }) prefix += word else break
    }
    return prefix.joinToString(" ").ifEmpty { names.first() }
}

/** Renders one section's [groups]: a lone source as a plain flat [row]; a multi-source group as a
 *  single boxed card (tinted, rounded) with a bold "Name (count)" header and its [row]s stacked inside,
 *  separated by dividers. The card vs flat-row contrast is what makes a group read as its own category.
 *  The whole group is one lazy item (repo lists are short, so nesting the rows is fine). Expanded unless
 *  the user collapsed it (tracked in [collapsedGroups] by a per-section key). */
private fun <T> LazyListScope.groupedItems(
    sectionId: String,
    groups: List<NameGroup<T>>,
    collapsedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    keyOf: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    for (group in groups) {
        if (group.members.size < 2) {
            val item = group.members.first()
            item(key = keyOf(item)) { row(item) }
        } else {
            val stateKey = "$sectionId:${group.key}"
            val expanded = stateKey !in collapsedGroups
            item(key = "$sectionId-group-${group.key}") {
                RepoGroupCard(
                    title = group.title,
                    count = group.members.size,
                    expanded = expanded,
                    onClick = { onToggleGroup(stateKey) },
                ) {
                    group.members.forEachIndexed { index, member ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        row(member)
                    }
                }
            }
        }
    }
}

/** A group rendered as a contained card: a tinted, rounded box whose bold header ("Name (count)" +
 *  open/closed chevron) toggles its [members]. The box makes the group visibly its own block, distinct
 *  from the flat rows of ungrouped sources around it. */
@Composable
private fun RepoGroupCard(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    members: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Text(
                    text = "$title ($count)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) members()
        }
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
                text = "${app.sourceLabel} · ${app.path}",
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

/** Asks whether the new source is an F-Droid repository or an external (releases) source, explaining
 *  each so the choice is clear, then routes to the matching add flow. */
@Composable
private fun AddSourceChooserDialog(
    onDismiss: () -> Unit,
    onChooseFdroid: () -> Unit,
    onChooseExternal: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_source_title)) },
        text = {
            Column {
                AddSourceOption(
                    title = stringResource(R.string.add_source_fdroid),
                    description = stringResource(R.string.add_source_fdroid_desc),
                    onClick = onChooseFdroid,
                )
                Spacer(Modifier.height(8.dp))
                AddSourceOption(
                    title = stringResource(R.string.add_source_external),
                    description = stringResource(R.string.add_source_external_desc),
                    onClick = onChooseExternal,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** One tappable option (bold title + explanation) in the add-source chooser. */
@Composable
private fun AddSourceOption(title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddExternalSourceDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAdd: (
        url: String,
        includePrereleases: Boolean,
        customName: String,
        muteUpdates: Boolean,
        apkFilter: String,
    ) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var includePrereleases by rememberSaveable { mutableStateOf(false) }
    var muteUpdates by rememberSaveable { mutableStateOf(false) }
    var apkFilter by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        // Stay cancellable even while loading, so a slow/stuck request can't trap the user.
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_add_source)) },
        text = {
            if (isLoading) {
                // While the add runs (release lookup + repo metadata), show a clear "working" state so
                // a slow network never looks like a no-op.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularWavyProgressIndicator()
                    Text(
                        text = stringResource(R.string.external_adding),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                        apkFilter = apkFilter,
                        onApkFilterChange = { apkFilter = it },
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                TextButton(
                    onClick = { onAdd(url, includePrereleases, name, muteUpdates, apkFilter) },
                    enabled = url.isNotBlank(),
                ) {
                    Text(stringResource(R.string.external_add))
                }
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
    iconCandidates: List<String>?,
    onDismiss: () -> Unit,
    onSave: (
        customName: String,
        includePrereleases: Boolean,
        muteUpdates: Boolean,
        apkFilter: String,
        iconUrl: String?,
    ) -> Unit,
) {
    var name by rememberSaveable(app.key) {
        mutableStateOf(if (app.nameOverridden) app.label else "")
    }
    var includePrereleases by rememberSaveable(app.key) { mutableStateOf(app.includePrereleases) }
    var muteUpdates by rememberSaveable(app.key) { mutableStateOf(app.muteUpdates) }
    var apkFilter by rememberSaveable(app.key) { mutableStateOf(app.apkFilter ?: "") }
    // The chosen icon URL; null means "use the account avatar / automatic".
    var selectedIcon by rememberSaveable(app.key) { mutableStateOf(app.repoIconUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_edit_source)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${app.sourceLabel} · ${app.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                IconPickerSection(
                    candidates = iconCandidates,
                    avatarUrl = app.iconUrl,
                    selected = selectedIcon,
                    onSelect = { selectedIcon = it },
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
                    apkFilter = apkFilter,
                    onApkFilterChange = { apkFilter = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, includePrereleases, muteUpdates, apkFilter, selectedIcon) },
            ) {
                Text(stringResource(R.string.external_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * The icon picker shown in the edit dialog: the launcher icons found in the source repo, plus the
 * account avatar, as selectable thumbnails. Selecting the avatar means "automatic". While the repo is
 * being scanned a small spinner shows; when nothing is found the section is hidden and the card simply
 * falls back to the avatar.
 */
@Composable
private fun IconPickerSection(
    candidates: List<String>?,
    avatarUrl: String?,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    when {
        candidates == null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.external_icon_searching),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        candidates.isEmpty() -> Unit

        else -> {
            Text(
                text = stringResource(R.string.external_icon_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidates.forEach { url ->
                    IconChoice(url = url, selected = selected == url, onClick = { onSelect(url) })
                }
                if (avatarUrl != null) {
                    IconChoice(
                        url = avatarUrl,
                        selected = selected == null,
                        onClick = { onSelect(null) },
                    )
                }
            }
        }
    }
}

/** A single selectable icon thumbnail, ringed in the accent colour when chosen. */
@Composable
private fun IconChoice(url: String, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .clickable(onClick = onClick),
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
    apkFilter: String,
    onApkFilterChange: (String) -> Unit,
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
    Spacer(Modifier.size(8.dp))
    OutlinedTextField(
        value = apkFilter,
        onValueChange = onApkFilterChange,
        label = { Text(stringResource(R.string.external_apk_filter)) },
        supportingText = { Text(stringResource(R.string.external_apk_filter_hint)) },
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
