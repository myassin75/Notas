package three.haddad.smartnotas.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import three.haddad.smartnotas.R
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.common.isSmallLandscape
import three.haddad.smartnotas.ui.components.AppIconImage
import three.haddad.smartnotas.ui.components.RememberButton
import three.haddad.smartnotas.ui.theme.reducedMotionAwareSpec
import three.haddad.smartnotas.ui.theme.reducedMotionEnterTransition

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingTitleScreen(
    onLetsBegin: () -> Unit,
) {
    var iconVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var bylineVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        iconVisible = true
        delay(140)
        titleVisible = true
        delay(120)
        bylineVisible = true
        delay(100)
        buttonVisible = true
    }

    val motionScheme = MaterialTheme.motionScheme
    val enterSpatialSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec<IntOffset>())
    val enterFadeSpec = reducedMotionAwareSpec(motionScheme.defaultEffectsSpec<Float>())

    val iconEnter =
        remember(enterSpatialSpec, enterFadeSpec) {
            fadeIn(animationSpec = enterFadeSpec) +
                slideInVertically(animationSpec = enterSpatialSpec) { fullHeight ->
                    fullHeight / 3
                }
        }
    val blockEnter =
        remember(enterSpatialSpec, enterFadeSpec) {
            fadeIn(animationSpec = enterFadeSpec) +
                slideInVertically(animationSpec = enterSpatialSpec) { fullHeight ->
                    fullHeight / 2
                }
        }

    val useTwoColumns = isSmallLandscape()

    if (useTwoColumns) {
        LandscapeLayout(
            iconVisible = iconVisible,
            titleVisible = titleVisible,
            bylineVisible = bylineVisible,
            buttonVisible = buttonVisible,
            iconEnter = reducedMotionEnterTransition(iconEnter),
            blockEnter = reducedMotionEnterTransition(blockEnter),
            onLetsBegin = onLetsBegin,
        )
    } else {
        PortraitLayout(
            iconVisible = iconVisible,
            titleVisible = titleVisible,
            bylineVisible = bylineVisible,
            buttonVisible = buttonVisible,
            iconEnter = reducedMotionEnterTransition(iconEnter),
            blockEnter = reducedMotionEnterTransition(blockEnter),
            onLetsBegin = onLetsBegin,
        )
    }
}

@Composable
private fun PortraitLayout(
    iconVisible: Boolean,
    titleVisible: Boolean,
    bylineVisible: Boolean,
    buttonVisible: Boolean,
    iconEnter: EnterTransition,
    blockEnter: EnterTransition,
    onLetsBegin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.background),
    ) {
        val screenW = maxWidth
        val horizontalMargin = screenW * 0.05f

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top spacing: ~10.5% of total height
            Spacer(modifier = Modifier.weight(0.105f))

            // Main Card: ~60% of total height
            Surface(
                modifier =
                    Modifier
                        .weight(0.60f)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalMargin),
                shape = RoundedCornerShape(screenW * 0.06f),
                color = scheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Logo top padding: ~19.6% of card height
                    Spacer(modifier = Modifier.weight(0.196f))

                    // Logo Composition Box (Always reserves space)
                    val logoSize = screenW * 0.4f
                    Box(
                        modifier = Modifier.size(logoSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.animation.AnimatedVisibility(visible = iconVisible, enter = iconEnter) {
                            LogoComposition(logoSize = logoSize)
                        }
                    }

                    // Spacer between Logo and Title
                    Spacer(modifier = Modifier.weight(0.063f))

                    // Title and Tagline Box
                    Box(contentAlignment = Alignment.TopCenter) {
                        androidx.compose.animation.AnimatedVisibility(visible = titleVisible, enter = blockEnter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(screenW * 0.0375f))
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    fontSize = 16.sp,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Empty space inside card
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Spacer between Card and Author
            Spacer(modifier = Modifier.weight(0.067f))

            Box(contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(visible = bylineVisible, enter = blockEnter) {
                    Text(
                        text = stringResource(R.string.onboarding_byline),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            // Spacer between Author and Button
            Spacer(modifier = Modifier.weight(0.02f))

            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalMargin),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = buttonVisible, enter = blockEnter, modifier = Modifier.fillMaxWidth()) {
                    RememberButton(
                        onClick = onLetsBegin,
                        shape = RoundedCornerShape(screenW * 0.045f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = scheme.primary,
                                contentColor = scheme.onPrimary,
                            ),
                        contentPadding = PaddingValues(horizontal = horizontalMargin * 1.5f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(screenW * 0.141f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_lets_begin),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimary,
                            )
                            RememberMaterialRoundedSymbol(
                                name = "arrow_forward",
                                autoMirror = true,
                                tint = scheme.onPrimary,
                            )
                        }
                    }
                }
            }

            // Bottom safe area spacing
            Spacer(modifier = Modifier.weight(0.12f))
        }
    }
}

@Composable
private fun LogoComposition(logoSize: androidx.compose.ui.unit.Dp) {
    val bubbleColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    Box(
        modifier = Modifier.size(logoSize),
        contentAlignment = Alignment.Center,
    ) {
        val dot1Size = logoSize * 0.1f
        val dot2Size = logoSize * 0.15f
        val logoIconSize = logoSize * 0.65f

        // Large pale circular background
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(bubbleColor),
        )

        // Upper-right circle
        Box(
            modifier =
                Modifier
                    .size(dot1Size)
                    .align(Alignment.Center)
                    .offset(x = logoSize * 0.42f, y = -(logoSize * 0.35f))
                    .clip(CircleShape)
                    .background(bubbleColor),
        )

        // Lower-left circle
        Box(
            modifier =
                Modifier
                    .size(dot2Size)
                    .align(Alignment.Center)
                    .offset(x = -(logoSize * 0.4f), y = logoSize * 0.35f)
                    .clip(CircleShape)
                    .background(bubbleColor),
        )

        // Grounding shadow
        Box(
            modifier =
                Modifier
                    .size(logoSize * 0.75f, logoSize * 0.125f)
                    .align(Alignment.BottomCenter)
                    .offset(y = logoSize * 0.05f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.04f), Color.Transparent),
                        ),
                    ),
        )

        // The SmartNotas Logo
        AppIconImage(
            modifier =
                Modifier
                    .size(logoIconSize)
                    .clip(RoundedCornerShape(logoIconSize * 0.23f)),
        )
    }
}

@Composable
private fun LandscapeLayout(
    iconVisible: Boolean,
    titleVisible: Boolean,
    bylineVisible: Boolean,
    buttonVisible: Boolean,
    iconEnter: EnterTransition,
    blockEnter: EnterTransition,
    onLetsBegin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.background),
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                shape = RoundedCornerShape(screenH * 0.06f),
                color = scheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val logoSize = screenH * 0.4f
                    Box(modifier = Modifier.size(logoSize), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(visible = iconVisible, enter = iconEnter) {
                            LogoComposition(logoSize = logoSize)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    androidx.compose.animation.AnimatedVisibility(visible = titleVisible, enter = blockEnter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.app_tagline),
                                fontSize = 14.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = bylineVisible, enter = blockEnter) {
                    Text(
                        text = stringResource(R.string.onboarding_byline),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                androidx.compose.animation.AnimatedVisibility(visible = buttonVisible, enter = blockEnter, modifier = Modifier.fillMaxWidth()) {
                    RememberButton(
                        onClick = onLetsBegin,
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = scheme.primary,
                                contentColor = scheme.onPrimary,
                            ),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_lets_begin),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimary,
                            )
                            RememberMaterialRoundedSymbol(
                                name = "arrow_forward",
                                autoMirror = true,
                                tint = scheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
