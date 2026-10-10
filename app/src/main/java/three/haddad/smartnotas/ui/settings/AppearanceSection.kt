package three.haddad.smartnotas.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.ThemeMode
import three.haddad.smartnotas.data.ThemePrefs
import three.haddad.smartnotas.data.ThemeState

@Composable
fun AppearanceSection(
    prefs: ThemePrefs,
    state: ThemeState,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()

    SettingsSectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(Color(0xFFF5F4F9))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SegmentOption(
                    title = stringResource(R.string.appearance_theme_system),
                    selected = state.themeMode == ThemeMode.SYSTEM,
                    onClick = { scope.launch { prefs.setThemeMode(ThemeMode.SYSTEM) } },
                    modifier = Modifier.weight(1f),
                )
                SegmentOption(
                    title = stringResource(R.string.appearance_theme_light),
                    selected = state.themeMode == ThemeMode.LIGHT,
                    onClick = { scope.launch { prefs.setThemeMode(ThemeMode.LIGHT) } },
                    modifier = Modifier.weight(1f),
                )
                SegmentOption(
                    title = stringResource(R.string.appearance_theme_dark),
                    selected = state.themeMode == ThemeMode.DARK,
                    onClick = { scope.launch { prefs.setThemeMode(ThemeMode.DARK) } },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF5F4F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { prefs.setUseBlackTheme(!state.useBlackTheme) } }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.appearance_use_black_theme),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = SettingsDesignTokens.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                SettingsCustomSwitch(
                    checked = state.useBlackTheme,
                    onCheckedChange = { checked ->
                        scope.launch { prefs.setUseBlackTheme(checked) }
                    },
                )
            }
        }
    }
}

@Composable
private fun SegmentOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) SettingsDesignTokens.SoftLavender else Color.Transparent
    val textColor = if (selected) SettingsDesignTokens.PrimaryPurple else SettingsDesignTokens.TextSecondary
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                color = textColor,
                fontWeight = fontWeight,
                fontSize = 14.sp,
            ),
        )
    }
}
