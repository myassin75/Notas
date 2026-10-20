package three.haddad.smartnotas.ui.splash

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R

/**
 * Visual design tokens sampled from the approved reference design.
 */
internal object SplashDesignTokens {
    val Background = Color(0xFFF6F3F8)
    val AppNameColor = Color(0xFF393743)
    val AttributionColor = Color(0xFF918D98)
}

/**
 * Production-ready Splash Screen for Notas, matching the approved reference design.
 *
 * Displays the dark burgundy squircle app icon, "Notas" title, and "from 3EE"
 * attribution on a background that adapts to Light, Dark, and OLED themes.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
) {
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "splashContentAlpha",
    )

    val background = MaterialTheme.colorScheme.background
    val appNameColor = MaterialTheme.colorScheme.onBackground
    val attributionColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(background),
    ) {
        // Centered Group: App Icon + App Name (optically balanced slightly above center)
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-22).dp)
                    .graphicsLayer { alpha = contentAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_splash_logo),
                contentDescription = stringResource(R.string.splash_app_name),
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(26.dp)),
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.splash_app_name),
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium,
                        color = appNameColor,
                        letterSpacing = (-0.5).sp,
                    ),
                textAlign = TextAlign.Center,
            )
        }

        // Bottom Brand Attribution: "from 3EE"
        Text(
            text = stringResource(R.string.splash_brand_attribution),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = attributionColor,
                ),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
                    .graphicsLayer { alpha = contentAlpha },
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenPreview() {
    SplashScreen()
}
