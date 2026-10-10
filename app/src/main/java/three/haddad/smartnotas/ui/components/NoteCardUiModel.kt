package three.haddad.smartnotas.ui.components

import androidx.compose.runtime.Immutable
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.data.getActiveReminders
import three.haddad.smartnotas.data.pinned
import three.haddad.smartnotas.ui.edit.NoteIcon
import three.haddad.smartnotas.ui.edit.resolveNoteIcon
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

@Immutable
data class NoteCardUiModel(
    val id: Long,
    val kind: NoteKind,
    val title: String,
    val body: String,
    /**
     * Pinned to the top of Home. Separate from [starred] on purpose: starred is a favorite
     * (filter chip + its own widget), pinned is placement only. The two cues must stay
     * visually distinct - starred owns the card border + watermark, pinned gets its own
     * leading glyph in the metadata row.
     */
    val pinned: Boolean,
    val completed: Boolean,
    val icon: NoteIcon,
    val pictureUri: String?,
    val pictureHeroFraming: String?,
    val pictureCacheRevision: Long,
    val reminderAt: Long?,
    val remindersCount: Int,
    val recurring: Boolean,
    val hasAttachment: Boolean,
    val checklistPreviewItems: PersistentList<NoteCardChecklistItemUiModel>,
    val checklistHiddenItemCount: Int,
)

@Immutable
data class NoteCardChecklistItemUiModel(
    val text: String,
    val details: String,
    val checked: Boolean,
    val depth: Int,
)

fun NoteWithItems.toNoteCardUiModel(
    checklistPreviewLimit: Int = 2,
): NoteCardUiModel {
    val orderedChecklistItems =
        items.filter { !it.checked }.sortedBy { it.sortOrder } +
            items.filter { it.checked }.sortedBy { it.sortOrder }
    return NoteCardUiModel(
        id = note.id,
        kind = note.kind,
        title = note.title,
        body = note.body,
        pinned = note.pinned,
        completed = note.completedAt != null,
        icon = resolveNoteIcon(note.iconKey, note.kind),
        pictureUri = note.pictureUri,
        pictureHeroFraming = note.pictureHeroFraming,
        pictureCacheRevision = note.updatedAt,
        reminderAt = note.reminderAt,
        remindersCount = note.getActiveReminders().size,
        recurring = note.recurrence != null,
        hasAttachment = attachments.isNotEmpty(),
        checklistPreviewItems =
            orderedChecklistItems
                .take(checklistPreviewLimit)
                .map { item ->
                    NoteCardChecklistItemUiModel(
                        text = item.text,
                        details = item.details,
                        checked = item.checked,
                        depth = item.depth,
                    )
                }.toPersistentList(),
        checklistHiddenItemCount = (orderedChecklistItems.size - checklistPreviewLimit).coerceAtLeast(0),
    )
}
