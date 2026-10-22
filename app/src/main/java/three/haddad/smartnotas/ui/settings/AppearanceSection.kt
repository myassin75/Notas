package three.haddad.smartnotas.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.AccentTheme
import three.haddad.smartnotas.data.ThemeMode
import three.haddad.smartnotas.data.ThemePrefs
import three.haddad.smartnotas.data.ThemeState
import three.haddad.smartnotas.data.effectiveDarkTheme
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.components.RememberButton
import three.haddad.smartnotas.ui.theme.LocalIsDark
import three.haddad.smartnotas.ui.theme.ThemeColors

/**
 * Production-ready Appearance Settings Section for NUVORA.
 *
 * Provides:
 * 1. Appearance Mode: System Default, Light, Dark with accessible controls and live updates.
 * 2. Accent Color Themes: 7 selectable accent themes with color previews and selected indicators.
 * 3. Live Theme Preview: A compact card demonstrating current mode and accent colors.
 * 4. OLED Pure Black toggle when dark mode is active.
 */
@Composable
fun AppearanceSection(
    prefs: ThemePrefs,
    state: ThemeState,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = state.themeMode.effectiveDarkTheme(isSystemDark)

    SettingsSectionCard {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // -------------------------------------------------------------------------
            // Section 1: Appearance Mode (System, Light, Dark)
            // -------------------------------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.appearance_mode_title),
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppearanceModeOption(
                        title = stringResource(R.string.appearance_theme_system),
                        iconName = "devices",
                        selected = state.themeMode == ThemeMode.SYSTEM,
                        onClick = { scope.launch { prefs.setThemeMode(ThemeMode.SYSTEM) } },
                        modifier = Modifier.weight(1f),
                    )
                    AppearanceModeOption(
                        title = stringResource(R.string.appearance_theme_light),
                        iconName = "light_mode",
                        selected = state.themeMode == ThemeMode.LIGHT,
                        onClick = { scope.launch { prefs.setThemeMode(ThemeMode.LIGHT) } },
                        modifier = Modifier.weight(1f),
                    )
                    AppearanceModeOption(
                        title = stringResource(R.string.appearance_theme_dark),
                        iconName = "bedtime",
                        selected = state.themeMode == ThemeMode.DARK,
                        onClick = { scope.launch { prefs.setThemeMode(ThemeMode.DARK) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )

            // -------------------------------------------------------------------------
            // Section 2: Accent Color & Live Theme Preview (Integrated)
            // -------------------------------------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.appearance_accent_color_title),
                        style =
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                    Text(
                        text = stringResource(state.accentTheme.titleRes),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }

                LiveThemePreviewCard(
                    accentTheme = state.accentTheme,
                    isDark = isDark,
                    onSelectTheme = { theme ->
                        scope.launch { prefs.setAccentTheme(theme) }
                    },
                )
            }

            // -------------------------------------------------------------------------
            // Section 4: Pure Black (OLED) Dark Mode Toggle
            // -------------------------------------------------------------------------
            AnimatedVisibility(visible = isDark || state.themeMode == ThemeMode.DARK) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { prefs.setUseBlackTheme(!state.useBlackTheme) } }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.appearance_use_black_theme),
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                            )
                            Text(
                                text = "Save battery on OLED displays with true black surfaces",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    ),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
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
    }
}

@Composable
private fun AppearanceModeOption(
    title: String,
    iconName: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val springSpec = spring<Color>(stiffness = Spring.StiffnessMediumLow)

    val targetBg =
        if (isDark) {
            if (selected) {
                Color(0xFF1E1E26).copy(alpha = 0.85f)
            } else {
                Color(0xFF141419).copy(alpha = 0.60f)
            }
        } else {
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        }

    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = springSpec,
        label = "modeBgAnimation",
    )

    val targetBorderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)
        }

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = springSpec,
        label = "modeBorderAnimation",
    )

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (selected) 1.5.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "modeBorderWidthAnimation",
    )

    val targetContentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = springSpec,
        label = "modeContentColor",
    )

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(animatedBorderWidth, animatedBorderColor), RoundedCornerShape(14.dp))
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics {
                    this.role = Role.RadioButton
                    this.selected = selected
                    this.contentDescription = "$title, " + if (selected) "selected" else "not selected"
                },
        shape = RoundedCornerShape(14.dp),
        color = animatedBg,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = iconName,
                    size = 22.dp,
                    tint = contentColor,
                    weight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    filled = selected,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = contentColor,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 5.dp, top = 2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun AccentThemesGrid(
    selectedTheme: AccentTheme,
    onSelect: (AccentTheme) -> Unit,
) {
    val themes = AccentTheme.entries

    // First row: 4 themes, Second row: 3 themes (centered and balanced)
    val topRow = themes.take(4)
    val bottomRow = themes.drop(4)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topRow.forEach { theme ->
                AccentThemeCard(
                    theme = theme,
                    selected = selectedTheme == theme,
                    onClick = { onSelect(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bottomRow.forEach { theme ->
                AccentThemeCard(
                    theme = theme,
                    selected = selectedTheme == theme,
                    onClick = { onSelect(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Spacer to balance the grid
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AccentThemeCard(
    theme: AccentTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val seedColor =
        when (theme) {
            AccentTheme.MAROON -> ThemeColors.MaroonSeed
            AccentTheme.ROYAL_BLUE -> ThemeColors.RoyalBlueSeed
            AccentTheme.FOREST_GREEN -> ThemeColors.ForestGreenSeed
            AccentTheme.DARK_BROWN -> ThemeColors.DarkBrownSeed
            AccentTheme.OCEAN_TEAL -> ThemeColors.OceanTealSeed
            AccentTheme.PURE_BLACK -> ThemeColors.PureBlackSeed
            AccentTheme.AMETHYST -> ThemeColors.AmethystSeed
        }

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (selected) 1.5.dp else 1.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accentBorderWidth",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)
            },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accentBorderColor",
    )
    val cardBackground by animateColorAsState(
        targetValue =
            if (isDark) {
                if (selected) {
                    Color(0xFF262638).copy(alpha = 0.90f)
                } else {
                    Color(0xFF1E1E28).copy(alpha = 0.75f)
                }
            } else {
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accentCardBg",
    )

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(animatedBorderWidth, animatedBorderColor), RoundedCornerShape(12.dp))
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics {
                    this.role = Role.RadioButton
                    this.selected = selected
                    this.contentDescription = "${theme.displayName} theme, " + if (selected) "selected" else "not selected"
                },
        shape = RoundedCornerShape(12.dp),
        color = cardBackground,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Swatch circular preview with checkmark when selected
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(seedColor)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (theme == AccentTheme.PURE_BLACK) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    Color.White.copy(alpha = 0.35f)
                                },
                            ),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    RememberMaterialRoundedSymbol(
                        name = "check",
                        size = 18.dp,
                        tint = if (theme == AccentTheme.OCEAN_TEAL) Color.Black else Color.White,
                        weight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(theme.titleRes),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Compact Live Preview showing how the selected appearance mode and accent look on real UI elements.
 */
@Composable
private fun LiveThemePreviewCard(
    accentTheme: AccentTheme,
    isDark: Boolean,
    onSelectTheme: (AccentTheme) -> Unit,
) {
    val isDarkLocal = LocalIsDark.current
    val previewBg =
        if (isDarkLocal) {
            Color(0xFF141419).copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val previewBorder =
        BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkLocal) 0.35f else 0.55f),
        )

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(previewBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = previewBg,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = stringResource(R.string.appearance_preview_sample_card_title),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                }

                // Sample pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(accentTheme.titleRes),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.appearance_preview_sample_card_body),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RememberButton(
                    onClick = {},
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.appearance_preview_action),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "check",
                            size = 14.dp,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            weight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = if (isDark) "Dark" else "Light",
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkLocal) 0.35f else 0.55f),
                thickness = 1.dp,
            )

            AccentThemesGrid(
                selectedTheme = accentTheme,
                onSelect = onSelectTheme,
            )
        }
    }
}
