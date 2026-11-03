package com.phonapps.qrscanner.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonapps.qrscanner.data.db.ScanEntity
import com.phonapps.qrscanner.data.model.ContentKind
import com.phonapps.qrscanner.data.repo.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistorySort { NEWEST, OLDEST }

data class HistoryUiState(
    val query: String = "",
    val kindFilter: ContentKind? = null,
    val favoritesOnly: Boolean = false,
    val sort: HistorySort = HistorySort.NEWEST,
    val scans: List<ScanEntity> = emptyList(),
    val totalCount: Int = 0
)

class HistoryViewModel(private val scans: ScanRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val kindFilter = MutableStateFlow<ContentKind?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val sort = MutableStateFlow(HistorySort.NEWEST)

    private data class FilterParams(
        val query: String,
        val kind: ContentKind?,
        val favoritesOnly: Boolean,
        val sort: HistorySort
    )

    val uiState: StateFlow<HistoryUiState> = combine(query, kindFilter, favoritesOnly, sort) { q, kind, fav, s ->
        FilterParams(q, kind, fav, s)
    }.flatMapLatest { params ->
        combine(
            scans.observeAll(params.sort == HistorySort.NEWEST),
            scans.observeCount()
        ) { all, total ->
            val filtered = all.filter { entity ->
                val matchesKind = params.kind == null || entity.contentType == params.kind.name
                val matchesFav = !params.favoritesOnly || entity.favorite
                val matchesQuery = params.query.isBlank() ||
                    entity.rawValue.contains(params.query, ignoreCase = true) ||
                    entity.title.contains(params.query, ignoreCase = true)
                matchesKind && matchesFav && matchesQuery
            }
            HistoryUiState(params.query, params.kind, params.favoritesOnly, params.sort, filtered, total)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setQuery(value: String) { query.value = value }
    fun setKindFilter(kind: ContentKind?) { kindFilter.value = kind }
    fun setFavoritesOnly(value: Boolean) { favoritesOnly.value = value }
    fun setSort(value: HistorySort) { sort.value = value }

    fun toggleFavorite(entity: ScanEntity) {
        viewModelScope.launch { scans.setFavorite(entity.id, !entity.favorite) }
    }

    fun delete(entity: ScanEntity) {
        viewModelScope.launch { scans.delete(entity.id) }
    }

    fun clearAll() {
        viewModelScope.launch { scans.clearAll() }
    }

    /** Snapshot for export (respects no filter: exports everything, newest first). */
    suspend fun exportSnapshot(): List<ScanEntity> =
        scans.observeAll(newestFirst = true).first()
}
