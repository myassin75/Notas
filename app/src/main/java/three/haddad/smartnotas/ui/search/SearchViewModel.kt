package three.haddad.smartnotas.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NoteWithItems
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val searchResults: List<NoteWithItems> = emptyList(),
    val allResultsCount: Int = 0,
    val notesResultsCount: Int = 0,
    val tasksResultsCount: Int = 0,
    val eventsResultsCount: Int = 0
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (newQuery.isBlank()) {
                _uiState.update { it.copy(
                    searchResults = emptyList(),
                    allResultsCount = 0,
                    notesResultsCount = 0,
                    tasksResultsCount = 0,
                    eventsResultsCount = 0
                ) }
            } else {
                noteRepository.searchNotes(newQuery).collectLatest { results ->
                    val notesCount = results.count { it.note.kind == NoteKind.NOTE }
                    val tasksCount = results.count { it.note.kind == NoteKind.LIST }
                    _uiState.update { it.copy(
                        searchResults = results,
                        allResultsCount = results.size,
                        notesResultsCount = notesCount,
                        tasksResultsCount = tasksCount,
                        eventsResultsCount = 0
                    ) }
                }
            }
        }
    }
}
