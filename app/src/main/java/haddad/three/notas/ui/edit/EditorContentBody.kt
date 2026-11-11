package haddad.three.notas.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import haddad.three.notas.ui.common.HERO_MASK_ASPECT_RATIO
import haddad.three.notas.ui.common.HeroFramedImage
import haddad.three.notas.ui.common.HeroFraming
import haddad.three.notas.ui.components.EditorShelfNotice
import haddad.three.notas.ui.components.EditorShelfNoticeState
import haddad.three.notas.ui.components.NoteShelfState
import haddad.three.notas.ui.feedback.appClickable
import haddad.three.notas.ui.modifiers.rememberExpressiveOverscrollEffect
import haddad.three.notas.ui.theme.reducedMotionAwareSpec

internal object EditorContentBodyDefaults {
    val HorizontalPadding = 24.dp
    val HeroTopSpacing = 16.dp
    val ShelfNoticeTopSpacing = 16.dp
    val BodyTopSpacing = 16.dp
    val BottomSpacing = 40.dp
    val BottomFloorExtra = 24.dp
}

/**
 * Scaffold bottom padding for the editor content, floored at the nav-bar inset plus
 * [EditorContentBodyDefaults.BottomFloorExtra]. The editors' bottom-bar slot renders a
 * zero-height Box when no bar is shown, which collapses the Scaffold padding to 0 — the
 * options panel must keep a real margin from the screen bottom even then.
 */
@Composable
internal fun editorBottomPaddingWithFloor(padding: PaddingValues): Dp {
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(
        padding.calculateBottomPadding(),
        navBarInset + EditorContentBodyDefaults.BottomFloorExtra,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorContentBodyColumn(
    modifier: Modifier,
    horizontalPadding: Dp,
    padding: PaddingValues,
    scrollState: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
    shelfState: NoteShelfState,
    bottomPadding: Dp,
    heroContent: (@Composable ColumnScope.() -> Unit)?,
    bodyContent: @Composable ColumnScope.() -> Unit,
    optionsContent: @Composable ColumnScope.() -> Unit,
) {
    val overscrollEffect = rememberExpressiveOverscrollEffect()
    val scrollModifier =
        Modifier.verticalScroll(
            state = scrollState,
            enabled = scrollEnabled,
            overscrollEffect = overscrollEffect,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .then(modifier)
                .clipToBounds()
                .overscroll(overscrollEffect)
                .then(scrollModifier)
                .padding(horizontal = horizontalPadding),
    ) {
        Spacer(Modifier.height(padding.calculateTopPadding()))
        heroContent?.invoke(this)
        EditorShelfNoticeBlock(shelfState = shelfState)
        Spacer(Modifier.height(EditorContentBodyDefaults.BodyTopSpacing))
        val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) Color(0xFF262626) else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                bodyContent()
            }
        }
        optionsContent()
        Spacer(Modifier.height(EditorContentBodyDefaults.BottomSpacing + bottomPadding))
    }
}

internal fun LazyListScope.editorContentHeaderItems(
    padding: PaddingValues,
    shelfState: NoteShelfState,
    heroContent: (@Composable () -> Unit)?,
    bodyTopSpacing: Dp = EditorContentBodyDefaults.BodyTopSpacing,
) {
    item(key = "top_padding") {
        Spacer(Modifier.height(padding.calculateTopPadding()))
    }
    if (heroContent != null) {
        item(key = "picture_hero") {
            Spacer(Modifier.height(EditorContentBodyDefaults.HeroTopSpacing))
            heroContent()
        }
    }
    if (shelfState != NoteShelfState.ACTIVE) {
        item(key = "editor_shelf_notice") {
            EditorShelfNoticeBlock(shelfState = shelfState)
        }
    }
    item(key = "body_top_spacing") {
        Spacer(Modifier.height(bodyTopSpacing))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.editorContentOptionsItem(
    padding: PaddingValues,
    bottomExtra: Dp = 0.dp,
    optionsContent: @Composable () -> Unit,
) {
    item(key = "options_panel") {
        Column(
            modifier =
                Modifier.animateItem(
                    placementSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
                ),
        ) {
            optionsContent()
            Spacer(
                Modifier.height(
                    EditorContentBodyDefaults.BottomSpacing +
                        editorBottomPaddingWithFloor(padding) +
                        bottomExtra,
                ),
            )
        }
    }
}

@Composable
internal fun EditorShelfNoticeBlock(shelfState: NoteShelfState) {
    when (shelfState) {
        NoteShelfState.ARCHIVED -> {
            Spacer(Modifier.height(EditorContentBodyDefaults.ShelfNoticeTopSpacing))
            EditorShelfNotice(
                state = EditorShelfNoticeState.ARCHIVED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NoteShelfState.TRASHED -> {
            Spacer(Modifier.height(EditorContentBodyDefaults.ShelfNoticeTopSpacing))
            EditorShelfNotice(
                state = EditorShelfNoticeState.TRASHED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NoteShelfState.ACTIVE -> {}
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditorContentPictureHero(
    uri: String,
    pictureRevision: Long,
    pictureHeroFraming: String?,
    viewerOpen: Boolean,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val framing = remember(pictureHeroFraming) { HeroFraming.fromJsonString(pictureHeroFraming) }
    val sharedScope = haddad.three.notas.ui.nav.LocalSharedTransitionScope.current

    Box(modifier = modifier) {
        val heroFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
        val heroFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
        AnimatedVisibility(
            visible = !viewerOpen,
            enter = fadeIn(animationSpec = heroFadeInSpec),
            exit = fadeOut(animationSpec = heroFadeOutSpec),
        ) {
            // Only lift the photo out of the editor when the full-screen viewer has a match.
            // During note navigation it must inherit the editor container's scale and fade.
            val viewerSharedModifier =
                if (sharedScope != null) {
                    with(sharedScope) {
                        val viewerSharedState = rememberSharedContentState(key = "hero-image-$uri")
                        Modifier.sharedBounds(
                            sharedContentState = viewerSharedState,
                            animatedVisibilityScope = this@AnimatedVisibility,
                            renderInOverlayDuringTransition = viewerSharedState.isMatchFound,
                        )
                    }
                } else {
                    Modifier
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(viewerSharedModifier)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .appClickable(onClick = onOpenFull),
            ) {
                HeroFramedImage(
                    imageUri = uri,
                    framing = framing,
                    cacheRevision = pictureRevision,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
