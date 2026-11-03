package com.phonapps.qrscanner.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonapps.qrscanner.data.db.ScanEntity
import com.phonapps.qrscanner.data.model.ContentKind
import com.phonapps.qrscanner.ui.common.ConfirmDialog
import com.phonapps.qrscanner.ui.common.EmptyState
import com.phonapps.qrscanner.ui.common.KindBadge
import com.phonapps.qrscanner.ui.common.appViewModel
import com.phonapps.qrscanner.ui.common.rememberAppDeps
import com.phonapps.qrscanner.util.ExportUtils
import com.phonapps.qrscanner.util.IntentActions
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenResult: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val deps = rememberAppDeps()
    val vm: HistoryViewModel = appViewModel(deps) { HistoryViewModel(it.scans) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            if (exportFormat == "json") "application/json" else "text/csv"
        )
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val snapshot = vm.exportSnapshot()
                val payload = if (exportFormat == "json") ExportUtils.toJson(snapshot)
                else ExportUtils.toCsv(snapshot)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(payload.toByteArray())
                }
                snackbar.showSnackbar("Exported ${snapshot.size} scans.")
            } catch (_: Exception) {
                snackbar.showSnackbar("Export failed.")
            } finally {
                exportFormat = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("History")
                        Text(
                            "${state.totalCount} scans",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export as CSV") },
                                onClick = {
                                    menuOpen = false
                                    exportFormat = "csv"
                                    exportLauncher.launch(ExportUtils.fileName("csv"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as JSON") },
                                onClick = {
                                    menuOpen = false
                                    exportFormat = "json"
                                    exportLauncher.launch(ExportUtils.fileName("json"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear all history") },
                                onClick = { menuOpen = false; confirmClear = true }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                query = state.query,
                onQueryChange = vm::setQuery,
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search scans") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            // Type filter + favorites + sort in one scrollable row.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = state.sort == HistorySort.NEWEST,
                        onClick = {
                            vm.setSort(
                                if (state.sort == HistorySort.NEWEST) HistorySort.OLDEST
                                else HistorySort.NEWEST
                            )
                        },
                        label = {
                            Text(if (state.sort == HistorySort.NEWEST) "Newest" else "Oldest")
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = state.favoritesOnly,
                        onClick = { vm.setFavoritesOnly(!state.favoritesOnly) },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                if (state.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = state.kindFilter == null,
                        onClick = { vm.setKindFilter(null) },
                        label = { Text("All") }
                    )
                }
                items(ContentKind.entries, key = { it.name }) { kind ->
                    FilterChip(
                        selected = state.kindFilter == kind,
                        onClick = {
                            vm.setKindFilter(if (state.kindFilter == kind) null else kind)
                        },
                        label = { Text(kind.label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.scans.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = if (state.totalCount == 0) "No scans yet"
                    else "Nothing matches",
                    body = if (state.totalCount == 0)
                        "Scanned codes will appear here. Everything stays on this device."
                    else "Try a different search term or clear the filters.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.scans, key = { it.id }) { entity ->
                        HistoryRow(
                            entity = entity,
                            onOpen = { onOpenResult(entity.id) },
                            onFavorite = { vm.toggleFavorite(entity) },
                            onDelete = { vm.delete(entity) },
                            onShare = { IntentActions.shareText(context, entity.rawValue) }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all history?",
            body = "All ${state.totalCount} scans will be permanently deleted from this device.",
            confirmLabel = "Clear all",
            destructive = true,
            onConfirm = { confirmClear = false; vm.clearAll() },
            onDismiss = { confirmClear = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entity: ScanEntity,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val kind = runCatching { ContentKind.valueOf(entity.contentType) }.getOrDefault(ContentKind.TEXT)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onShare)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KindBadge(kind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entity.title.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${kind.label} · " + DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT
                    ).format(Date(entity.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (entity.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (entity.favorite) "Remove favorite" else "Mark favorite",
                    tint = if (entity.favorite) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete scan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
