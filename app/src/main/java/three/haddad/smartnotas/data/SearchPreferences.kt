package three.haddad.smartnotas.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchPreferences @Inject constructor() {
    val recentSearches: Flow<List<String>> = flowOf(emptyList())
}
