package haddad.three.notas.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.common.isSmallLandscape
import haddad.three.notas.ui.components.RememberButton
import haddad.three.notas.ui.theme.reducedMotionAwareSpec
import haddad.three.notas.ui.theme.reducedMotionEnterTransition
import kotlinx.coroutines.delay

private val BrandMaroon = Color(0xFF800000)
private val ScreenBg = Color(0xFFF7F7F9)
private val TitleDark = Color(0xFF1B1B20)
private val SubtitleGray = Color(0xFF8A879B)
private val BylineGray = Color(0xFF84828F)
private val PaleDecorShape = Color(0xFFFDF6F7)

@Composable
fun OnboardingTitleScreen(
    onLetsBegin: () -> Unit,
) {
    var iconVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var bylineVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(60)
        iconVisible = true
        delay(120)
        titleVisible = true
        delay(100)
        bylineVisible = true
        delay(80)
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
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(top = topInset, bottom = bottomInset)
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val horizontalMargin = screenW * 0.055f

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top spacing above card
            Spacer(modifier = Modifier.height(screenH * 0.025f))

            // Main White Card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalMargin),
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val logoSize = (screenW * 0.44f).coerceAtMost(200.dp)
                    androidx.compose.animation.AnimatedVisibility(visible = iconVisible, enter = iconEnter) {
                        LogoComposition(logoSize = logoSize)
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    AnimatedVisibility(visible = titleVisible, enter = blockEnter) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Notas",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = TitleDark,
                                letterSpacing = (-0.5).sp,
                            )
                            Text(
                                text = stringResource(R.string.app_tagline),
                                fontSize = 15.sp,
                                color = SubtitleGray,
                                textAlign = TextAlign.Center,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(screenH * 0.045f))

            // Author Byline
            AnimatedVisibility(visible = bylineVisible, enter = blockEnter) {
                Text(
                    text = stringResource(R.string.onboarding_byline),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BylineGray,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Let's begin" Action Button
            AnimatedVisibility(
                visible = buttonVisible,
                enter = blockEnter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalMargin),
            ) {
                RememberButton(
                    onClick = onLetsBegin,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandMaroon,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
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
                            color = Color.White,
                        )
                        RememberMaterialRoundedSymbol(
                            name = "arrow_forward",
                            autoMirror = true,
                            tint = Color.White,
                            size = 24.dp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(screenH * 0.035f))
        }
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        val screenH = maxHeight

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val logoSize = (screenH * 0.38f).coerceAtMost(160.dp)
                    androidx.compose.animation.AnimatedVisibility(visible = iconVisible, enter = iconEnter) {
                        LogoComposition(logoSize = logoSize)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = titleVisible, enter = blockEnter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Notas",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = TitleDark,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.app_tagline),
                                fontSize = 14.sp,
                                color = SubtitleGray,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AnimatedVisibility(visible = bylineVisible, enter = blockEnter) {
                    Text(
                        text = stringResource(R.string.onboarding_byline),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BylineGray,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedVisibility(
                    visible = buttonVisible,
                    enter = blockEnter,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RememberButton(
                        onClick = onLetsBegin,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandMaroon,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_lets_begin),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            RememberMaterialRoundedSymbol(
                                name = "arrow_forward",
                                autoMirror = true,
                                tint = Color.White,
                                size = 22.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoComposition(logoSize: Dp) {
    Box(
        modifier = Modifier.size(logoSize),
        contentAlignment = Alignment.Center,
    ) {
        val dot1Size = logoSize * 0.12f
        val dot2Size = logoSize * 0.16f
        val logoIconSize = logoSize * 0.68f

        // Pale circular background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(PaleDecorShape),
        )

        // Upper-right circle accent
        Box(
            modifier = Modifier
                .size(dot1Size)
                .align(Alignment.Center)
                .offset(x = logoSize * 0.44f, y = -(logoSize * 0.36f))
                .clip(CircleShape)
                .background(PaleDecorShape),
        )

        // Lower-left circle accent
        Box(
            modifier = Modifier
                .size(dot2Size)
                .align(Alignment.Center)
                .offset(x = -(logoSize * 0.42f), y = logoSize * 0.36f)
                .clip(CircleShape)
                .background(PaleDecorShape),
        )

        // Grounding radial shadow
        Box(
            modifier = Modifier
                .size(logoSize * 0.75f, logoSize * 0.12f)
                .align(Alignment.BottomCenter)
                .offset(y = logoSize * 0.04f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.04f), Color.Transparent),
                    ),
                ),
        )

        // The SmartNotas Logo
        val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
        Image(
            painter = painterResource(id = if (isDark) R.drawable.ic_logo_white else R.drawable.darck_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(logoIconSize)
                .clip(RoundedCornerShape(logoIconSize * 0.22f)),
        )
    }
}
