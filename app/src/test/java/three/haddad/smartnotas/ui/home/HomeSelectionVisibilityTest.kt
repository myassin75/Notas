package three.haddad.smartnotas.ui.home

import three.haddad.smartnotas.data.NoteEntity
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.ui.components.toNoteCardUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSelectionVisibilityTest {
    @Test
    fun collapsed_section_rows_are_excluded_from_selectable_ids() {
        val activeNote = note(id = 1L, title = "Active")
        val completedNote = note(id = 2L, title = "Done")
        val displayedItems =
            listOf(
                HomeListItem.NoteRow(
                    note = activeNote,
                    card = activeNote.toNoteCardUiModel(),
                    groupKey = "ACTIVE",
                ),
                HomeListItem.NoteRow(
                    note = completedNote,
                    card = completedNote.toNoteCardUiModel(),
                    groupKey = "DONE",
                ),
            )

        val selectableIds =
            selectableVisibleNoteIds(
                displayedItems = displayedItems,
                collapsedSectionKeys = setOf("DONE"),
            )

        assertEquals(setOf(1L), selectableIds)
    }

    @Suppress("ktlint:standard:function-expression-body")
    private fun note(
        id: Long,
        title: String,
    ): NoteWithItems {
        return NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = title,
                    body = "",
                    colorIndex = 0,
                    starred = false,
                    trashed = false,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            items = emptyList(),
        )
    }
}
