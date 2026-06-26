package com.looker.droidify.compose.appList

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults.IconButtonWidthOption.Companion.Narrow
import androidx.compose.material3.IconButtonDefaults.smallContainerSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.looker.droidify.R
import com.looker.droidify.compose.components.CatalogCard
import com.looker.droidify.compose.externalApps.ExternalGridCard
import com.looker.droidify.compose.externalApps.ExternalAppsViewModel
import com.looker.droidify.data.model.AppMinimal
import com.looker.droidify.datastore.extension.sortOrderName
import com.looker.droidify.datastore.model.SortOrder
import com.looker.droidify.datastore.model.supportedSortOrders

@Composable
fun AppListScreen(
    viewModel: AppListViewModel,
    onAppClick: (String) -> Unit,
    onExternalAppClick: (String) -> Unit,
    onNavigateToRepos: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val newApps by viewModel.newApps.collectAsStateWithLifecycle()
    val recentlyUpdatedApps by viewModel.recentlyUpdatedApps.collectAsStateWithLifecycle()
    val mostDownloadedApps by viewModel.mostDownloadedApps.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val expandedSections by viewModel.expandedSections.collectAsStateWithLifecycle()
    val expandedSectionApps by viewModel.expandedSectionApps.collectAsStateWithLifecycle()
    val openedSection by viewModel.openedSection.collectAsStateWithLifecycle()
    val openedSectionApps by viewModel.openedSectionApps.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrderFlow.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val updatesCount by viewModel.updatesCount.collectAsStateWithLifecycle()
    val installedVersionNames by viewModel.installedVersionNames.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // The Explore tab shows the Discover home (3 curated carousels + the categories list) by default;
    // once the user is searching or has opened a category, it shows a flat list of apps instead.
    val isSearching = viewModel.searchQuery.text.isNotEmpty()
    // A curated carousel's "see all" opens it as its own page (a flat list of the whole section);
    // null means we're on the Discover home. Categories, by contrast, expand inline in the accordion.
    val sectionView = openedSection != null
    // Collapse the in-header search on system back (folds it back into the magnifier).
    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
        viewModel.searchQuery.clearText()
    }
    // System back leaves a carousel "see all" page and returns to the Discover home.
    BackHandler(enabled = sectionView && !searchExpanded) {
        viewModel.closeSection()
    }
    // Entering or leaving a section page swaps the whole list, so start it at the top.
    LaunchedEffect(openedSection) { gridState.scrollToItem(0) }

    // Cold/warm start: the Discover carousels are fed by independent flows that emit in a race, and
    // LazyGrid anchors on its first visible item — so a carousel that finishes loading *above* the
    // current anchor (e.g. "New apps" arriving after "Most downloaded") shoves the top off-screen and
    // the Explore tab opens already scrolled down. Pin it to the top while the sections stream in,
    // and stop the moment the user actually scrolls (a real drag/fling sets isScrollInProgress; the
    // programmatic scrollToItem below does not, so this never fights the user).
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) userScrolled = true }
    }
    LaunchedEffect(
        newApps.size,
        recentlyUpdatedApps.size,
        mostDownloadedApps.size,
        categories.size,
    ) {
        if (selectedTab == AppTab.AVAILABLE && !isSearching && !sectionView && !userScrolled) {
            gridState.scrollToItem(0)
        }
    }

    // The External tab is backed by its own ViewModel (Obtainium-style sources: GitHub/GitLab/
    // Codeberg). It shows the same 2-column card grid as the F-Droid tabs; tapping a card opens the
    // external detail screen, where the install lifecycle lives — exactly like the other tabs.
    val externalViewModel: ExternalAppsViewModel = hiltViewModel()
    val externalApps by externalViewModel.apps.collectAsStateWithLifecycle()
    val externalInstalledKeys by externalViewModel.installedKeys.collectAsStateWithLifecycle()
    // External-repo updates surface in the Updates tab too (no difference from F-Droid repos), so we
    // refresh release tags on screen entry — not only when the External tab is open.
    LaunchedEffect(Unit) {
        externalViewModel.refresh()
        externalViewModel.refreshInstalled()
    }
    // Replace stored repo names with the real installed app names (e.g. "GlassKeep"). Keyed on the
    // count so it runs once apps load and converges (label-only changes don't change the count).
    LaunchedEffect(externalApps.size) {
        externalViewModel.reconcileInstalledLabels()
    }
    // Disabled sources are hidden from the catalogue and updates, exactly like a disabled F-Droid repo.
    val enabledExternalApps = remember(externalApps) { externalApps.filter { it.enabled } }
    val externalUpdates = remember(enabledExternalApps) { enabledExternalApps.filter { it.hasUpdate } }

    // First launch: the catalogue is still empty and a sync is running. Show a full-screen fetching
    // state (like F-Droid) instead of an empty grid + thin banner. `newApps` is empty exactly when the
    // catalogue has no apps, so it doubles as the "nothing loaded yet" signal. The External tab has
    // its own content, so it's excluded.
    val catalogLoading = isSyncing && newApps.isEmpty() && selectedTab != AppTab.EXTERNAL

    Scaffold(
        snackbarHost = { SnackbarHost(externalViewModel.snackbarHostState) },
        topBar = {
            Column {
                // A carousel "see all" page takes over the whole header: a back arrow + the section
                // title, with no tabs, so it reads as its own screen.
                if (sectionView) {
                    SectionTopBar(
                        title = sectionTitle(openedSection),
                        onBack = { viewModel.closeSection() },
                    )
                } else {
                    AppListTopBar(
                        onSync = viewModel::sync,
                        searchExpanded = searchExpanded,
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) viewModel.searchQuery.clearText()
                        },
                        searchState = viewModel.searchQuery,
                        onNavigateToRepos = onNavigateToRepos,
                        onNavigateToSettings = onNavigateToSettings,
                        currentSort = sortOrder,
                        onSortSelected = viewModel::setSortOrder,
                        title = {
                            Text("Droid-ify")
                        },
                    )
                    AppTabRow(
                        selectedTab = selectedTab,
                        updatesCount = updatesCount + externalUpdates.size,
                        onSelectTab = viewModel::selectTab,
                    )
                    // While the full-screen fetching state is up, the thin banner is redundant.
                    if (isSyncing && !catalogLoading) {
                        SyncBanner()
                    }
                }
            }
        },
    ) { contentPadding ->
        if (catalogLoading) {
            RepoFetchingState(modifier = Modifier.padding(contentPadding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = contentPadding,
        ) {
            if (selectedTab == AppTab.EXTERNAL) {
                if (enabledExternalApps.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "external-empty") {
                        ExternalTabEmpty()
                    }
                }
                // Same 2-column grid as the catalogue tabs; install happens on the detail screen.
                items(items = enabledExternalApps, key = { it.key }) { app ->
                    ExternalGridCard(
                        app = app,
                        isInstalled = app.key in externalInstalledKeys,
                        onClick = { onExternalAppClick(app.key) },
                    )
                }
                return@LazyVerticalGrid
            }
            // Discover home (Explore tab, not searching, not on a "see all" page): the 3 curated
            // carousels then the categories accordion. A carousel arrow opens that section as its own
            // page; a category chevron expands its apps inline. When searching or on a section page,
            // this is skipped and the apps render as a flat list below.
            if (selectedTab == AppTab.AVAILABLE && !isSearching && !sectionView) {
                val installedPackages = installedVersionNames.keys
                if (newApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "carousel-new") {
                        DiscoverCarousel(
                            title = stringResource(R.string.discover_new_apps),
                            apps = newApps,
                            installedPackages = installedPackages,
                            onAppClick = onAppClick,
                            onSeeAll = { viewModel.openSection(SECTION_WHATS_NEW) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                if (recentlyUpdatedApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "carousel-updated") {
                        DiscoverCarousel(
                            title = stringResource(R.string.discover_recently_updated),
                            apps = recentlyUpdatedApps,
                            installedPackages = installedPackages,
                            onAppClick = onAppClick,
                            onSeeAll = { viewModel.openSection(SECTION_RECENTLY_UPDATED) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                // "Most downloaded" — F-Droid v2's third curated carousel. Hidden until the download-
                // stats worker has fetched data, so it simply appears once stats land.
                if (mostDownloadedApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "carousel-downloaded") {
                        DiscoverCarousel(
                            title = stringResource(R.string.discover_most_downloaded),
                            apps = mostDownloadedApps,
                            installedPackages = installedPackages,
                            onAppClick = onAppClick,
                            onSeeAll = { viewModel.openSection(SECTION_MOST_DOWNLOADED) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                // The categories accordion. The chevron expands a category's apps inline; tapping
                // again collapses it.
                if (categories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "categories-title") {
                        CategoriesTitle()
                    }
                    categories.forEach { category ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "category-$category") {
                            CategoryRow(
                                category = category,
                                expanded = category in expandedSections,
                                onClick = { viewModel.toggleSection(category) },
                            )
                        }
                        expandedAppItems(category, expandedSections, expandedSectionApps, onAppClick)
                    }
                }
            }
            val showEmpty = when (selectedTab) {
                AppTab.INSTALLED -> apps.isEmpty()
                AppTab.UPDATES -> apps.isEmpty() && externalUpdates.isEmpty()
                else -> false
            }
            if (showEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty-tab") {
                    EmptyTabMessage(tab = selectedTab)
                }
            }
            // The Explore tab ends at the categories accordion — no flat grid under the Discover home.
            // A flat list of full-width F-Droid-style rows appears when searching (search results) or
            // on a carousel "see all" page (the whole section). The Installed/Updates tabs keep cards.
            if (selectedTab == AppTab.AVAILABLE) {
                val flatList = when {
                    sectionView -> openedSectionApps
                    isSearching -> apps
                    else -> emptyList()
                }
                items(
                    items = flatList,
                    key = { it.appId },
                    span = { GridItemSpan(maxLineSpan) },
                ) { app ->
                    AppListRow(
                        app = app,
                        versionLabel = appVersionLabel(app, selectedTab, installedVersionNames),
                        onClick = { onAppClick(app.packageName.name) },
                    )
                }
            } else {
                items(
                    items = apps,
                    key = { it.appId },
                ) { app ->
                    AppCard(
                        app = app,
                        versionLabel = appVersionLabel(app, selectedTab, installedVersionNames),
                        onClick = { onAppClick(app.packageName.name) },
                    )
                }
            }
            // External-repo updates, shown alongside the F-Droid ones on the Updates tab.
            if (selectedTab == AppTab.UPDATES) {
                items(
                    items = externalUpdates,
                    key = { "ext-${it.key}" },
                ) { app ->
                    ExternalGridCard(
                        app = app,
                        isInstalled = app.key in externalInstalledKeys,
                        version = "${app.installedTag} → ${app.latestTag}",
                        onClick = { onExternalAppClick(app.key) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTabRow(
    selectedTab: AppTab,
    updatesCount: Int,
    onSelectTab: (AppTab) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        AppTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                text = {
                    val label = when (tab) {
                        AppTab.AVAILABLE -> stringResource(R.string.available)
                        AppTab.INSTALLED -> stringResource(R.string.installed)
                        AppTab.UPDATES -> if (updatesCount > 0) {
                            "${stringResource(R.string.updates)} ($updatesCount)"
                        } else {
                            stringResource(R.string.updates)
                        }
                        AppTab.EXTERNAL -> stringResource(R.string.tab_external)
                    }
                    Text(label)
                },
            )
        }
    }
}

/**
 * Full-screen state shown on first launch while the catalogue is being fetched — mirrors F-Droid: a
 * centred label above the Material 3 expressive wavy loading indicator (themed with the app's accent),
 * instead of an empty grid.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepoFetchingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.fetching_repositories),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        CircularWavyProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/**
 * Status strip shown under the tabs while a sync runs. A filled container (not a bare line that
 * blended into the tab indicator) with a spinner + label, so it reads as "syncing", not decoration.
 */
@Composable
private fun SyncBanner() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.syncing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyTabMessage(tab: AppTab) {
    val message = when (tab) {
        AppTab.INSTALLED -> stringResource(R.string.no_installed_apps)
        AppTab.UPDATES -> stringResource(R.string.everything_up_to_date)
        AppTab.AVAILABLE -> ""
        AppTab.EXTERNAL -> ""
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SearchBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedTransition = updateTransition(isPressed)
    val pressedScale by pressedTransition.animateFloat(label = "pressedScale") { isPressed ->
        if (isPressed) 0.97F else 1F
    }
    val pressedShape by pressedTransition.animateDp(label = "pressedShape") { isPressed ->
        if (isPressed) 20.dp else 32.dp
    }
    BasicTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = LocalTextStyle.current,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                clip = true
                shape = RoundedCornerShape(pressedShape)
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(modifier),
        decorator = {
            Box(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                val isFocused by interactionSource.collectIsFocusedAsState()
                if (state.text.isEmpty()) {
                    val colors = TextFieldDefaults.colors()
                    val color by animateColorAsState(
                        if (!isFocused) {
                            colors.focusedPlaceholderColor
                        } else {
                            colors.unfocusedPlaceholderColor
                        },
                    )
                    Text(
                        text = stringResource(R.string.search),
                        color = color,
                    )
                }
                it()
            }
        },
    )
}

/**
 * The header turned into a search field: a back arrow (folds it away) + a full-width input that
 * auto-focuses so the keyboard opens immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    state: TextFieldState,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        },
        title = {
            BasicTextField(
                state = state,
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorator = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppListTopBar(
    onSync: () -> Unit,
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    searchState: TextFieldState,
    onNavigateToRepos: () -> Unit,
    onNavigateToSettings: () -> Unit,
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    title: @Composable () -> Unit,
) {
    // Tapping the magnifier unfolds the search field into the whole header (back arrow + input);
    // closing it folds back to the title + actions.
    if (searchExpanded) {
        SearchTopBar(state = searchState, onClose = onToggleSearch)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    TopAppBar(
        title = title,
        actions = {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(smallContainerSize(Narrow)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_tabler_search),
                    contentDescription = stringResource(R.string.search),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onSync,
                modifier = Modifier.size(smallContainerSize(Narrow)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_tabler_refresh),
                    contentDescription = stringResource(R.string.sync),
                )
            }
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(smallContainerSize(Narrow)),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_tabler_sort),
                        contentDescription = stringResource(R.string.sort),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    supportedSortOrders().forEach { order ->
                        DropdownMenuItem(
                            text = { Text(context.sortOrderName(order)) },
                            onClick = {
                                onSortSelected(order)
                                expanded = false
                            },
                            trailingIcon = if (order == currentSort) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onNavigateToRepos,
                modifier = Modifier.size(smallContainerSize(Narrow)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_tabler_box),
                    contentDescription = stringResource(R.string.repositories),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.size(smallContainerSize(Narrow)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_tabler_settings),
                    contentDescription = stringResource(R.string.settings),
                )
            }
            Spacer(Modifier.width(4.dp))
        },
    )
}

/**
 * The version string to show on a card for the given [tab]: the real installed version on the
 * Installed tab, "installed → available" on the Updates tab, and the available (catalogue) version
 * elsewhere. The installed version comes from the package manager, so a fork installed over an
 * upstream package shows its actual version (e.g. "6.5.5-c") rather than the catalogue's.
 */
private fun appVersionLabel(
    app: AppMinimal,
    tab: AppTab,
    installedVersionNames: Map<String, String>,
): String {
    val installed = installedVersionNames[app.packageName.name]
    return when (tab) {
        AppTab.INSTALLED -> installed ?: app.suggestedVersion
        AppTab.UPDATES ->
            if (installed != null && installed != app.suggestedVersion) {
                "$installed → ${app.suggestedVersion}"
            } else {
                app.suggestedVersion
            }
        else -> app.suggestedVersion
    }
}

/** A catalogue app as a grid card (shared [CatalogCard] chrome): large icon, name, summary and
 *  version. Tapping opens the detail screen (install happens there). */
@Composable
private fun AppCard(
    app: AppMinimal,
    versionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CatalogCard(
        name = app.name,
        summary = app.summary,
        version = versionLabel,
        onClick = onClick,
        modifier = modifier,
    ) {
        var icon by remember(app.appId) { mutableStateOf(app.icon?.path) }
        if (icon != null) {
            AsyncImage(
                model = icon,
                onError = { icon = app.fallbackIcon?.path },
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                    ),
            ) {
                Image(
                    painter = painterResource(android.R.mipmap.sym_def_app_icon),
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** A catalogue app as a full-width list row (F-Droid-style category / search results): icon, name,
 *  summary and version. Tapping opens the detail screen. */
@Composable
private fun AppListRow(
    app: AppMinimal,
    versionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        var icon by remember(app.appId) { mutableStateOf(app.icon?.path) }
        if (icon != null) {
            AsyncImage(
                model = icon,
                onError = { icon = app.fallbackIcon?.path },
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                    ),
            ) {
                Image(
                    painter = painterResource(android.R.mipmap.sym_def_app_icon),
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = app.summary
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (versionLabel.isNotBlank()) {
            Text(
                text = versionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExternalTabEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.external_empty_tab),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Emits the inline-expanded app rows for a Discover section (a carousel or a category), lazily, when
 *  that section is expanded — full-width F-Droid-style rows below the carousel/category header. */
private fun LazyGridScope.expandedAppItems(
    key: String,
    expandedSections: Set<String>,
    expandedSectionApps: Map<String, List<AppMinimal>>,
    onAppClick: (String) -> Unit,
) {
    if (key !in expandedSections) return
    items(
        items = expandedSectionApps[key].orEmpty(),
        key = { "exp-$key-${it.appId}" },
        span = { GridItemSpan(maxLineSpan) },
    ) { app ->
        AppListRow(
            app = app,
            versionLabel = app.suggestedVersion,
            onClick = { onAppClick(app.packageName.name) },
        )
    }
}

/** "Categories" heading above the categories list. */
@Composable
private fun CategoriesTitle() {
    Text(
        text = stringResource(R.string.categories),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The localized title for a curated-carousel "see all" page. */
@Composable
private fun sectionTitle(key: String?): String = when (key) {
    SECTION_WHATS_NEW -> stringResource(R.string.discover_new_apps)
    SECTION_RECENTLY_UPDATED -> stringResource(R.string.discover_recently_updated)
    SECTION_MOST_DOWNLOADED -> stringResource(R.string.discover_most_downloaded)
    else -> ""
}

/** Header for a carousel "see all" page: a back arrow that returns to the Discover home + the title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        },
        title = { Text(title) },
    )
}

