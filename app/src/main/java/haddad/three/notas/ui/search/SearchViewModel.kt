package haddad.three.notas.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.SearchHistoryEntry
import haddad.three.notas.data.SearchPreferences
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ShoulderRepository
import java.util.Calendar
import javax.inject.Inject

enum class SearchSortMode {
    RELEVANCE,
    NEWEST,
    OLDEST,
}

data class SearchFilters(
    val typeIndex: Int = 0,
    val dateRangeIndex: Int = 0,
    val customStartDate: Long? = null,
    val customEndDate: Long? = null,
    val taskStatusIndex: Int = 0,
    val shoulderId: Long? = null,
    val includeArchived: Boolean = false,
) {
    val activeCount: Int
        get() {
            var count = 0
            if (typeIndex != 0) count++
            if (dateRangeIndex != 0) count++
            if (taskStatusIndex != 0) count++
            if (shoulderId != null) count++
            if (includeArchived) count++
            return count
        }
}

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val sortMode: SearchSortMode = SearchSortMode.RELEVANCE,
    val searchResults: List<NoteWithItems> = emptyList(),
    val allResultsCount: Int = 0,
    val notesResultsCount: Int = 0,
    val tasksResultsCount: Int = 0,
    val eventsResultsCount: Int = 0,
    val isLoading: Boolean = false,
) {
    val selectedCategoryIndex: Int
        get() = filters.typeIndex
}

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val noteRepository: NoteRepository,
        private val shoulderRepository: ShoulderRepository,
        private val searchPreferences: SearchPreferences,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        val recentSearches: StateFlow<List<SearchHistoryEntry>> =
            searchPreferences.recentSearches.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        val activeShoulders: StateFlow<List<ShoulderEntity>> =
            shoulderRepository.getAllShoulders().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        private var searchJob: Job? = null
        private var lastRawResults: List<NoteWithItems> = emptyList()

        fun updateCategoryIndex(index: Int) {
            val newFilters = _uiState.value.filters.copy(typeIndex = index)
            _uiState.update { it.copy(filters = newFilters) }
            reapplyFiltersAndSort()
        }

        fun setSortMode(sortMode: SearchSortMode) {
            if (_uiState.value.sortMode == sortMode) return
            _uiState.update { it.copy(sortMode = sortMode) }
            reapplyFiltersAndSort()
        }

        fun applyFilters(filters: SearchFilters) {
            val currentFilters = _uiState.value.filters
            _uiState.update { it.copy(filters = filters) }
            if (currentFilters.includeArchived != filters.includeArchived) {
                performSearch(_uiState.value.query, debounceMs = 0L)
            } else {
                reapplyFiltersAndSort()
            }
        }

        fun updateQuery(newQuery: String) {
            _uiState.update { it.copy(query = newQuery) }
            performSearch(newQuery, debounceMs = 300L)
        }

        fun submitSearch(query: String) {
            val trimmed = query.trim()
            if (trimmed.isNotBlank()) {
                _uiState.update { it.copy(query = trimmed) }
                viewModelScope.launch {
                    searchPreferences.addSearchQuery(trimmed)
                }
                performSearch(trimmed, debounceMs = 0L)
            }
        }

        fun removeRecentSearch(query: String) {
            viewModelScope.launch {
                searchPreferences.removeSearchQuery(query)
            }
        }

        fun clearRecentSearches() {
            viewModelScope.launch {
                searchPreferences.clearRecentSearches()
            }
        }

        private fun performSearch(
            newQuery: String,
            debounceMs: Long,
        ) {
            searchJob?.cancel()
            if (newQuery.isBlank()) {
                lastRawResults = emptyList()
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        allResultsCount = 0,
                        notesResultsCount = 0,
                        tasksResultsCount = 0,
                        eventsResultsCount = 0,
                        isLoading = false,
                    )
                }
                return
            }

            searchJob =
                viewModelScope.launch {
                    if (debounceMs > 0L) {
                        delay(debounceMs)
                    }
                    _uiState.update { it.copy(isLoading = true) }

                    val includeArchived = _uiState.value.filters.includeArchived
                    val flow =
                        if (includeArchived) {
                            combine(
                                noteRepository.searchNotes(newQuery),
                                noteRepository.searchArchivedNotes(newQuery),
                            ) { active, archived -> active + archived }
                        } else {
                            noteRepository.searchNotes(newQuery)
                        }

                    flow.collectLatest { rawResults ->
                        lastRawResults = rawResults
                        reapplyFiltersAndSort()
                    }
                }
        }

        private fun reapplyFiltersAndSort() {
            val currentState = _uiState.value
            val query = currentState.query
            if (query.isBlank()) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        allResultsCount = 0,
                        notesResultsCount = 0,
                        tasksResultsCount = 0,
                        eventsResultsCount = 0,
                        isLoading = false,
                    )
                }
                return
            }

            val filters = currentState.filters
            val sortMode = currentState.sortMode

            val filteredCandidates =
                lastRawResults.filter { item ->
                    val note = item.note

                    if (filters.dateRangeIndex != 0) {
                        val timestamp = getEntityTimestamp(item)
                        if (timestamp == null) {
                            return@filter false
                        }
                        if (filters.dateRangeIndex == 1) {
                            val cal = Calendar.getInstance()
                            val currentMonth = cal.get(Calendar.MONTH)
                            val currentYear = cal.get(Calendar.YEAR)
                            cal.timeInMillis = timestamp
                            if (cal.get(Calendar.MONTH) != currentMonth || cal.get(Calendar.YEAR) != currentYear) {
                                return@filter false
                            }
                        } else if (filters.dateRangeIndex == 2) {
                            val start = startOfDayMillis(filters.customStartDate) ?: 0L
                            val end = endOfDayMillis(filters.customEndDate) ?: Long.MAX_VALUE
                            if (timestamp !in start..end) {
                                return@filter false
                            }
                        }
                    }

                    if (filters.shoulderId != null && note.shoulderId != filters.shoulderId) {
                        return@filter false
                    }

                    if (filters.taskStatusIndex != 0 && isTask(item)) {
                        val isCompleted = note.completedAt != null || (item.items.isNotEmpty() && item.items.all { it.checked })
                        if (filters.taskStatusIndex == 1 && isCompleted) return@filter false
                        if (filters.taskStatusIndex == 2 && !isCompleted) return@filter false
                    }

                    true
                }

            val notesCount = filteredCandidates.count { isNote(it) }
            val tasksCount = filteredCandidates.count { isTask(it) }
            val eventsCount = filteredCandidates.count { isEvent(it) }
            val allCount = notesCount + tasksCount + eventsCount

            val displayedCandidates =
                when (filters.typeIndex) {
                    1 -> filteredCandidates.filter { isNote(it) }
                    2 -> filteredCandidates.filter { isTask(it) }
                    3 -> filteredCandidates.filter { isEvent(it) }
                    else -> filteredCandidates
                }

            val sortedResults =
                when (sortMode) {
                    SearchSortMode.RELEVANCE -> {
                        displayedCandidates.sortedWith(
                            compareByDescending<NoteWithItems> { calculateRelevanceScore(it, query) }
                                .thenByDescending { getEntityTimestamp(it) ?: 0L },
                        )
                    }
                    SearchSortMode.NEWEST -> {
                        displayedCandidates.sortedByDescending { getEntityTimestamp(it) ?: 0L }
                    }
                    SearchSortMode.OLDEST -> {
                        displayedCandidates.sortedBy { getEntityTimestamp(it) ?: Long.MAX_VALUE }
                    }
                }

            _uiState.update {
                it.copy(
                    searchResults = sortedResults,
                    allResultsCount = allCount,
                    notesResultsCount = notesCount,
                    tasksResultsCount = tasksCount,
                    eventsResultsCount = eventsCount,
                    isLoading = false,
                )
            }
        }

        companion object {
            fun isTask(item: NoteWithItems): Boolean = item.note.kind == NoteKind.LIST

            fun isEvent(item: NoteWithItems): Boolean = item.note.kind == NoteKind.NOTE && (item.note.reminderAt != null || item.note.reminders.isNotEmpty())

            fun isNote(item: NoteWithItems): Boolean = item.note.kind == NoteKind.NOTE && item.note.reminderAt == null && item.note.reminders.isEmpty()

            fun getEntityTimestamp(item: NoteWithItems): Long? =
                if (isEvent(item)) {
                    item.note.reminderAt ?: item.note.reminders.minOfOrNull { it.reminderAt } ?: item.note.updatedAt
                } else if (isTask(item)) {
                    item.note.reminderAt ?: item.note.updatedAt
                } else {
                    item.note.updatedAt
                }

            fun calculateRelevanceScore(
                item: NoteWithItems,
                query: String,
            ): Long {
                val q = query.trim().lowercase()
                if (q.isBlank()) return 0L
                val title =
                    item.note.title
                        .trim()
                        .lowercase()
                var score = 0L

                if (title == q) {
                    score += 100_000L
                } else if (title.startsWith(q)) {
                    score += 50_000L
                } else if (title.contains(q)) {
                    score += 20_000L
                }

                val body = item.note.body.lowercase()
                if (body.contains(q)) {
                    score += 5_000L
                }
                if (item.items.any { it.text.lowercase().contains(q) }) {
                    score += 5_000L
                }
                return score
            }

            fun startOfDayMillis(epochMillis: Long?): Long? {
                if (epochMillis == null) return null
                val cal = Calendar.getInstance()
                cal.timeInMillis = epochMillis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }

            fun endOfDayMillis(epochMillis: Long?): Long? {
                if (epochMillis == null) return null
                val cal = Calendar.getInstance()
                cal.timeInMillis = epochMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                return cal.timeInMillis
            }
        }
    }
