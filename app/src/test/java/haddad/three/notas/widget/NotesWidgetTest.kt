package haddad.three.notas.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.ViewOptions
import haddad.three.notas.data.Visibility

class NotesWidgetTest {
    @Test
    fun selected_notes_widget_items_empty_notes_returns_empty_list() {
        val result =
            selectedNotesWidgetItems(
                notes = emptyList(),
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.ALL),
                viewOptions = ViewOptions(),
            )

        assertEquals(emptyList<NoteWithItems>(), result)
    }

    @Test
    fun selected_notes_widget_items_all_excludes_completed_notes() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Active 1"),
                createTestNote(id = 2L, title = "Completed", completedAt = 1000L),
                createTestNote(id = 3L, title = "Active 2"),
            )

        val result =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.ALL),
                viewOptions = ViewOptions(),
            )

        assertEquals(listOf(1L, 3L), result.map { it.note.id })
    }

    @Test
    fun selected_notes_widget_items_shoulder_filters_notes_with_shoulder() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Regular"),
                createTestNote(id = 2L, title = "Shoulder", shoulderId = 10L),
                createTestNote(id = 3L, title = "Shoulder Completed", shoulderId = 10L, completedAt = 500L),
            )

        val result =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.SHOULDER),
                viewOptions = ViewOptions(),
            )

        assertEquals(listOf(2L), result.map { it.note.id })
    }

    @Test
    fun selected_notes_widget_items_pinned_filters_only_pinned_notes() {
        val notes =
            listOf(
                createTestNote(id = 1L, title = "Regular"),
                createTestNote(id = 2L, title = "Pinned", pinned = true),
                createTestNote(id = 3L, title = "Pinned Completed", pinned = true, completedAt = 500L),
            )

        val result =
            selectedNotesWidgetItems(
                notes = notes,
                config = SelectedNotesWidgetConfig(filterType = SelectedNotesFilterType.PINNED),
                viewOptions = ViewOptions(),
            )

        assertEquals(listOf(2L), result.map { it.note.id })
    }

    private fun createTestNote(
        id: Long,
        title: String = "Note $id",
        pinned: Boolean = false,
        shoulderId: Long? = null,
        completedAt: Long? = null,
        visibility: Visibility = Visibility.DEFAULT,
    ): NoteWithItems =
        NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = title,
                    body = "",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = id * 1000L,
                    updatedAt = id * 1000L,
                    visibility = visibility,
                    shoulderId = shoulderId,
                    completedAt = completedAt,
                    pinnedAt = if (pinned) 1000L else null,
                ),
            items = emptyList(),
        )
}
