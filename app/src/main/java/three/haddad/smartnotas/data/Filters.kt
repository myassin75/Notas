package three.haddad.smartnotas.data

import androidx.compose.runtime.Immutable

enum class FilterType { ALL, NOTE, LIST }

@Immutable
data class NotesFilter(
    val text: String = "",
    val type: FilterType = FilterType.ALL,
    val shoulderId: Long? = null,
    val hasReminder: Boolean? = null,
    val hasPicture: Boolean? = null,
    val hasAttachment: Boolean? = null,
    val pinned: Boolean? = null,
) {
    val facetActive: Boolean
        get() =
            type != FilterType.ALL ||
                shoulderId != null ||
                hasReminder != null ||
                hasPicture != null ||
                hasAttachment != null ||
                pinned != null

    val active: Boolean
        get() = text.isNotBlank() || facetActive
}

fun NotesFilter.matches(n: NoteWithItems): Boolean {
    val note = n.note
    val typeOk =
        when (type) {
            FilterType.ALL -> true
            FilterType.NOTE -> note.kind == NoteKind.NOTE
            FilterType.LIST -> note.kind == NoteKind.LIST
        }
    if (!typeOk) return false
    
    if (shoulderId != null && note.shoulderId != shoulderId) return false
    
    hasReminder?.let { if ((note.reminderAt != null) != it) return false }
    hasPicture?.let { if ((!note.pictureUri.isNullOrBlank()) != it) return false }
    hasAttachment?.let { if (n.attachments.isNotEmpty() != it) return false }
    pinned?.let { if (note.pinned != it) return false }
    if (text.isBlank()) return true
    val needle = text.trim().lowercase()
    if (note.title.lowercase().contains(needle)) return true
    if (note.body.lowercase().contains(needle)) return true
    if (n.items.any { it.text.lowercase().contains(needle) }) return true
    if (n.attachments.any { it.displayName.lowercase().contains(needle) }) return true
    return n.note.actions.any { action ->
        action.title.lowercase().contains(needle) ||
            action.details.lowercase().contains(needle)
    }
}
