package haddad.three.notas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeRevealDefaultsTest {
    private val defaultSlots =
        (DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS + DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS)
            .filterNotNull()

    @Test
    fun default_layout_has_pin_and_duplicate() {
        assertEquals(
            listOf(NoteSwipeAction.TOGGLE_PIN, NoteSwipeAction.DUPLICATE),
            DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS.filterNotNull(),
        )
        assertEquals(
            listOf(NoteSwipeAction.MARK_DONE, NoteSwipeAction.ARCHIVE, NoteSwipeAction.TRASH),
            DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS.filterNotNull(),
        )
    }

    @Test
    fun pin_is_reachable_on_swipe_out_of_the_box() {
        assertTrue(NoteSwipeAction.TOGGLE_PIN in defaultSlots)
    }

    @Test
    fun direct_mode_right_swipe_default_matches_the_first_reveal_slot() {
        assertEquals(
            DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS.first(),
            DEFAULT_SWIPE_START_TO_END_ACTION,
        )
        assertEquals(NoteSwipeAction.TOGGLE_PIN, DEFAULT_SWIPE_START_TO_END_ACTION)
    }

    @Test
    fun direct_mode_left_swipe_default_stays_trash_and_does_not_follow_the_reveal_slots() {
        assertEquals(NoteSwipeAction.TRASH, DEFAULT_SWIPE_END_TO_START_ACTION)
        assertEquals(NoteSwipeAction.MARK_DONE, DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS.first())
    }

    @Test
    fun default_interaction_state_uses_the_documented_defaults() {
        val state = InteractionState()
        assertEquals(NoteSwipeAction.TOGGLE_PIN, state.swipeStartToEnd)
        assertEquals(NoteSwipeAction.TRASH, state.swipeEndToStart)
    }
}
