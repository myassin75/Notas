package haddad.three.notas.ui.home

import androidx.compose.ui.graphics.Color
import haddad.three.notas.data.GroupBy
import haddad.three.notas.data.NoteEntity
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.NotesFilter
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.data.ViewOptions
import haddad.three.notas.data.matches
import haddad.three.notas.ui.components.readableBadgeColors
import haddad.three.notas.ui.components.toNoteCardUiModel
import haddad.three.notas.ui.theme.NoteColors
import haddad.three.notas.ui.theme.resolveSemanticPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates NUVORA Home Screen & Note Cards refinement acceptance criteria:
 * - Empty Home state
 * - Undated note isolation
 * - Multiple notes with different Shoulder colors
 * - Notes without Shoulders (no false Shoulder)
 * - Custom note colors preserved independently
 * - Readable badge colors for shoulders in dark & light modes
 * - Filter selection by shoulder
 * - Light Mode preservation
 */
class HomeCardAndThemeRefinementTest {
    @Test
    fun empty_home_state_has_empty_items_and_zero_active() {
        val emptyList = emptyList<NoteWithItems>()
        val items = arrangeItems(emptyList, ViewOptions())
        assertTrue("Empty notes produce empty list items", items.isEmpty())
    }

    @Test
    fun undated_note_strictly_remains_in_no_date_group() {
        val now = System.currentTimeMillis()
        val undatedNote =
            NoteWithItems(
                note =
                    NoteEntity(
                        id = 101L,
                        kind = NoteKind.NOTE,
                        title = "Unscheduled Thought",
                        body = "This has no reminder or date.",
                        colorIndex = 0,
                        trashed = false,
                        createdAt = now,
                        updatedAt = now,
                        reminderAt = null,
                    ),
                items = emptyList(),
            )

        val items = arrangeItems(listOf(undatedNote), ViewOptions(groupBy = GroupBy.DATE))
        val headers = items.filterIsInstance<HomeListItem.Header>()

        assertEquals(1, headers.size)
        assertEquals("NO_DATE", headers.first().stableKey)
        val row = items.filterIsInstance<HomeListItem.NoteRow>().first()
        assertEquals(101L, row.card.id)
        assertNull(row.card.reminderAt)
    }

    @Test
    fun multiple_notes_with_different_shoulder_colors_preserve_identity() {
        val workShoulder = makeShoulder(id = 1L, name = "Work", colorHex = "#04147D")
        val personalShoulder = makeShoulder(id = 2L, name = "Personal", colorHex = "#047D06")
        val creativeShoulder = makeShoulder(id = 3L, name = "Creative", colorHex = "#98549C")

        val note1 = createNoteWithShoulder(id = 1L, title = "Sprint Plan", shoulder = workShoulder)
        val note2 = createNoteWithShoulder(id = 2L, title = "Grocery List", shoulder = personalShoulder)
        val note3 = createNoteWithShoulder(id = 3L, title = "Song Lyrics", shoulder = creativeShoulder)

        val card1 = note1.toNoteCardUiModel()
        val card2 = note2.toNoteCardUiModel()
        val card3 = note3.toNoteCardUiModel()

        assertEquals("Work", card1.shoulderName)
        assertEquals("#04147D", card1.shoulderColorHex)

        assertEquals("Personal", card2.shoulderName)
        assertEquals("#047D06", card2.shoulderColorHex)

        assertEquals("Creative", card3.shoulderName)
        assertEquals("#98549C", card3.shoulderColorHex)
    }

    @Test
    fun notes_without_shoulders_do_not_display_false_shoulder() {
        val noteNoShoulder =
            NoteWithItems(
                note =
                    NoteEntity(
                        id = 4L,
                        kind = NoteKind.NOTE,
                        title = "Standalone note",
                        body = "No category",
                        colorIndex = 0,
                        trashed = false,
                        createdAt = 1000L,
                        updatedAt = 1000L,
                        shoulderId = null,
                    ),
                items = emptyList(),
                shoulder = null,
            )

        val card = noteNoShoulder.toNoteCardUiModel()

        assertNull("Shoulder name must be null", card.shoulderName)
        assertNull("Shoulder color hex must be null", card.shoulderColorHex)
    }

    @Test
    fun note_specific_color_is_preserved_independently() {
        val customColorNote =
            NoteWithItems(
                note =
                    NoteEntity(
                        id = 5L,
                        kind = NoteKind.NOTE,
                        title = "Peach Note",
                        body = "Has colorIndex = 2",
                        colorIndex = 2,
                        trashed = false,
                        createdAt = 1000L,
                        updatedAt = 1000L,
                    ),
                items = emptyList(),
            )

        val card = customColorNote.toNoteCardUiModel()

        assertEquals(2, card.colorIndex)
        assertFalse(NoteColors.isNone(card.colorIndex))
        assertEquals("Peach", NoteColors.at(card.colorIndex).name)
    }

    @Test
    fun readable_badge_colors_generate_distinct_and_readable_surfaces() {
        val testColor = Color(0xFF04147D) // Deep Royal Blue

        val (darkBg, darkFg) = readableBadgeColors(testColor, isDark = true)
        val (lightBg, lightFg) = readableBadgeColors(testColor, isDark = false)

        assertNotNull(darkBg)
        assertNotNull(darkFg)
        assertNotNull(lightBg)
        assertNotNull(lightFg)

        // Dark foreground should be light/readable
        assertTrue("Dark mode badge foreground alpha should be 1.0", darkFg.alpha > 0.99f)
        // Dark background should be subtle container
        assertTrue("Dark mode badge background container alpha should be restrained", darkBg.alpha < 0.5f)
    }

    @Test
    fun shoulder_filter_matches_only_notes_with_that_shoulder() {
        val workShoulder = makeShoulder(id = 10L, name = "Work", colorHex = "#04147D")
        val personalShoulder = makeShoulder(id = 20L, name = "Personal", colorHex = "#047D06")

        val noteWork = createNoteWithShoulder(id = 1L, title = "Task 1", shoulder = workShoulder)
        val notePersonal = createNoteWithShoulder(id = 2L, title = "Task 2", shoulder = personalShoulder)

        val filterWork = NotesFilter(shoulderId = 10L)

        assertTrue("Work note matches work filter", filterWork.matches(noteWork))
        assertFalse("Personal note does not match work filter", filterWork.matches(notePersonal))
    }

    @Test
    fun light_mode_semantic_palettes_remain_consistent() {
        val themes = haddad.three.notas.data.AccentTheme.entries
        themes.forEach { theme ->
            val palette = resolveSemanticPalette(accentTheme = theme, darkTheme = false, isOledBlack = false)
            assertNotNull(palette.primaryAction)
            assertNotNull(palette.primaryActionForeground)
            assertEquals(Color.Transparent, palette.ambientTint)
        }
    }

    private fun makeShoulder(
        id: Long,
        name: String,
        colorHex: String,
    ): ShoulderEntity =
        ShoulderEntity(
            id = id,
            name = name,
            normalizedName = name.lowercase(),
            colorHex = colorHex,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

    private fun createNoteWithShoulder(
        id: Long,
        title: String,
        shoulder: ShoulderEntity,
    ): NoteWithItems =
        NoteWithItems(
            note =
                NoteEntity(
                    id = id,
                    kind = NoteKind.NOTE,
                    title = title,
                    body = "Content for $title",
                    colorIndex = 0,
                    trashed = false,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                    shoulderId = shoulder.id,
                ),
            items = emptyList(),
            shoulder = shoulder,
        )
}
