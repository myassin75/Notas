package three.haddad.smartnotas.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.data.SearchHistoryEntry
import three.haddad.smartnotas.data.SearchPreferences
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.data.ShoulderRepository
import java.util.Calendar
import javax.inject.Inject

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
    val searchResults: List<NoteWithItems> = emptyList(),
    val allResultsCount: Int = 0,
    val notesResultsCount: Int = 0,
    val tasksResultsCount: Int = 0,
    val eventsResultsCount: Int = 0,
) {
    val selectedCategoryIndex: Int
        get() = filters.typeIndex
}

@HiltViewModel
class SearchViewModel @Inject constructor(
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

    fun updateCategoryIndex(index: Int) {
        val newFilters = _uiState.value.filters.copy(typeIndex = index)
        _uiState.update { it.copy(filters = newFilters) }
    }

    fun applyFilters(filters: SearchFilters) {
        _uiState.update { it.copy(filters = filters) }
        performSearch(_uiState.value.query)
    }

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        performSearch(newQuery)
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            _uiState.update { it.copy(query = trimmed) }
            viewModelScope.launch {
                searchPreferences.addSearchQuery(trimmed)
            }
            performSearch(trimmed)
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

    private fun performSearch(newQuery: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (newQuery.isBlank()) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        allResultsCount = 0,
                        notesResultsCount = 0,
                        tasksResultsCount = 0,
                        eventsResultsCount = 0,
                    )
                }
            } else {
                val filters = _uiState.value.filters
                val flow = if (filters.includeArchived) {
                    combine(
                        noteRepository.searchNotes(newQuery),
                        noteRepository.searchArchivedNotes(newQuery),
                    ) { active, archived -> active + archived }
                } else {
                    noteRepository.searchNotes(newQuery)
                }

                flow.collectLatest { rawResults ->
                    val results = rawResults.filter { item ->
                        val note = item.note

                        var dateMatches = true
                        if (filters.dateRangeIndex != 0) {
                            val timestamp = if (note.kind == NoteKind.NOTE) note.updatedAt else note.reminderAt
                            if (timestamp == null) {
                                dateMatches = false
                            } else {
                                if (filters.dateRangeIndex == 1) {
                                    val cal = Calendar.getInstance()
                                    val currentMonth = cal.get(Calendar.MONTH)
                                    val currentYear = cal.get(Calendar.YEAR)
                                    cal.timeInMillis = timestamp
                                    dateMatches = cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
                                } else if (filters.dateRangeIndex == 2) {
                                    val start = filters.customStartDate ?: 0L
                                    val end = filters.customEndDate ?: Long.MAX_VALUE
                                    dateMatches = timestamp in start..end
                                }
                            }
                        }
                        if (!dateMatches) return@filter false

                        if (filters.taskStatusIndex != 0 && note.kind == NoteKind.LIST) {
                            val isCompleted = note.completedAt != null
                            if (filters.taskStatusIndex == 1 && isCompleted) return@filter false
                            if (filters.taskStatusIndex == 2 && !isCompleted) return@filter false
                        }

                        if (filters.shoulderId != null && note.shoulderId != filters.shoulderId) return@filter false

                        true
                    }

                    val notesCount = results.count { it.note.kind == NoteKind.NOTE }
                    val tasksCount = results.count { it.note.kind == NoteKind.LIST }
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            allResultsCount = results.size,
                            notesResultsCount = notesCount,
                            tasksResultsCount = tasksCount,
                            eventsResultsCount = 0,
                        )
                    }
                }
            }
        }
    }
}
