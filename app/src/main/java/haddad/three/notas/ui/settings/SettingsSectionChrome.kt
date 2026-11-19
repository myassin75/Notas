package haddad.three.notas.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.RememberSwitch
import haddad.three.notas.ui.feedback.appClickable
import haddad.three.notas.ui.theme.reducedMotionAwareSpec

object SettingsDesignTokens {
    val ScreenBg: Color @Composable get() = MaterialTheme.colorScheme.background
    val CardBg: Color @Composable get() =
        if (haddad.three.notas.ui.theme.LocalIsDark.current) {
            haddad.three.notas.ui.theme
                .glassContainerColor(haddad.three.notas.ui.theme.GlassVariant.StandardCard, isDark = true)
        } else {
            MaterialTheme.colorScheme.surface
        }
    val PrimaryPurple: Color @Composable get() = MaterialTheme.colorScheme.primary
    val SoftLavender: Color
        @Composable get() {
            val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
            return if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer
        }
    val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val DividerColor: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val DisabledBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val DisabledText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val DisabledSwitchTrack: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val StaticPrimary = Color(0xFF800000)
    val StaticSoftLavender = Color(0xFFF4EAF6)

    val CardShape = RoundedCornerShape(22.dp)
    val IconBoxShape = RoundedCornerShape(10.dp)
    val PillShape = CircleShape
}

@Composable
fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
    val containerColor =
        if (isDark) {
            haddad.three.notas.ui.theme
                .glassContainerColor(haddad.three.notas.ui.theme.GlassVariant.StandardCard, isDark = true)
        } else {
            MaterialTheme.colorScheme.surface
        }
    val borderStroke =
        if (isDark) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SettingsDesignTokens.CardShape,
        color = containerColor,
        border = borderStroke,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SettingsSectionTitle(
    materialSymbolName: String,
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Normal,
            filled = false,
        )
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            modifier = Modifier.weight(1f),
        )
        trailingContent?.invoke()
    }
}

@Composable
fun SettingsRowItem(
    materialSymbolName: String? = null,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    titleColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val clickableModifier =
        if (enabled && onClick != null) {
            Modifier.appClickable { onClick() }
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(clickableModifier)
                .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (materialSymbolName != null) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = materialSymbolName,
                    size = 24.dp,
                    tint = if (enabled) (titleColor ?: MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    weight = FontWeight.Normal,
                    filled = false,
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = titleColor ?: (if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)),
                    ),
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(12.dp))
            trailingContent()
        }
    }
}

@Composable
fun SettingsValuePill(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    val pillBg =
        if (isDark) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        }
    val pillTextColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier =
            Modifier
                .clip(SettingsDesignTokens.PillShape)
                .then(clickableModifier),
        shape = SettingsDesignTokens.PillShape,
        color = pillBg,
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = pillTextColor,
                ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun SettingsCustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
    val uncheckedTrack = if (isDark) Color(0xFF2C2C32) else MaterialTheme.colorScheme.surfaceContainerHighest
    val uncheckedThumb = if (isDark) Color(0xFF9E9EA8) else MaterialTheme.colorScheme.outline
    val disabledTrack = if (isDark) Color(0xFF242428) else MaterialTheme.colorScheme.surfaceContainerHigh
    val disabledThumb = if (isDark) Color(0xFF9E9EA8).copy(alpha = 0.38f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)

    RememberSwitch(
        checked = checked,
        onCheckedChange = if (enabled) onCheckedChange else null,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = uncheckedThumb,
                uncheckedTrackColor = uncheckedTrack,
                uncheckedBorderColor = Color.Transparent,
                disabledUncheckedTrackColor = disabledTrack,
                disabledUncheckedThumbColor = disabledThumb,
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f),
            ),
        thumbContent =
            if (checked) {
                {
                    RememberMaterialRoundedSymbol(
                        name = "check",
                        size = SwitchDefaults.IconSize,
                        tint = MaterialTheme.colorScheme.primary,
                        weight = FontWeight.Bold,
                    )
                }
            } else {
                null
            },
    )
}

@Composable
fun SettingsDivider(hasIcon: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (hasIcon) 66.dp else 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsExpandableSection(
    sectionKey: String,
    materialSymbolName: String,
    title: String,
    collapsedSectionKeys: Set<String>,
    onCollapsedSectionKeysChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    highlightedSectionKey: String? = null,
    content: @Composable () -> Unit,
) {
    val collapsed = sectionKey in collapsedSectionKeys
    val highlighted = sectionKey == highlightedSectionKey
    val highlightAlpha = rememberSectionHighlightPulseAlpha(highlighted)
    val spatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .pulsingSectionHighlightOutline(
                    active = highlighted,
                    outlineColor = MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha),
                ),
    ) {
        if (showHeader) {
            SettingsSectionTitle(
                materialSymbolName = materialSymbolName,
                title = title,
            )
            Spacer(Modifier.height(6.dp))
        }
        AnimatedVisibility(
            visible = !showHeader || !collapsed,
            enter =
                expandVertically(
                    animationSpec = spatialSpec,
                    expandFrom = Alignment.Top,
                ) + fadeIn(fadeInSpec),
            exit =
                shrinkVertically(
                    animationSpec = spatialSpec,
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(fadeOutSpec),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
internal fun SettingsStaticSectionHeader(
    materialSymbolName: String,
    title: String,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SettingsSectionTitle(
        materialSymbolName = materialSymbolName,
        title = title,
        trailingContent = trailingContent,
    )
}

@Composable
internal fun SettingsToggleRow(
    materialSymbolName: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRowItem(
        materialSymbolName = materialSymbolName,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            SettingsCustomSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

internal fun isPermissionLinked(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        Build.MANUFACTURER.lowercase() in setOf("google", "samsung", "nothing", "motorola")

@Composable
internal fun SettingsToggleSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDisabledInteraction: (() -> Unit)? = null,
) {
    Box {
        SettingsCustomSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        if (!enabled && onDisabledInteraction != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.extraExtraLarge)
                        .appClickable { onDisabledInteraction() },
            )
        }
    }
}

@Composable
internal fun ToggleRow(
    materialSymbolName: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingsRowItem(
        materialSymbolName = materialSymbolName,
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick =
            if (enabled) {
                { onChange(!checked) }
            } else {
                null
            },
        trailingContent = {
            SettingsCustomSwitch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
            )
        },
    )
}
