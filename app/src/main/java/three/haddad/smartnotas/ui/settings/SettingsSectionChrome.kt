package three.haddad.smartnotas.ui.settings

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
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.components.RememberSwitch
import three.haddad.smartnotas.ui.feedback.appClickable
import three.haddad.smartnotas.ui.theme.reducedMotionAwareSpec

object SettingsDesignTokens {
    val ScreenBg = Color(0xFFFAFAFD)
    val CardBg = Color(0xFFFFFFFF)
    val PrimaryPurple = Color(0xFF98549C)
    val SoftLavender = Color(0xFFF4EAF6)
    val TextPrimary = Color(0xFF41414D)
    val TextSecondary = Color(0xFF9292A1)
    val DividerColor = Color(0xFFF0EFF4)
    val DisabledBg = Color(0xFFEFEFF4)
    val DisabledText = Color(0xFF9292A1)
    val DisabledSwitchTrack = Color(0xFFE2E2E9)

    val CardShape = RoundedCornerShape(22.dp)
    val IconBoxShape = RoundedCornerShape(10.dp)
    val PillShape = CircleShape
}

@Composable
fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SettingsDesignTokens.CardShape,
        color = SettingsDesignTokens.CardBg,
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 20.dp,
            tint = SettingsDesignTokens.PrimaryPurple,
            weight = FontWeight.Normal,
            filled = false,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = SettingsDesignTokens.TextPrimary,
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
    val clickableModifier = if (enabled && onClick != null) {
        Modifier.appClickable { onClick() }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (materialSymbolName != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(SettingsDesignTokens.IconBoxShape)
                    .background(if (enabled) SettingsDesignTokens.SoftLavender else SettingsDesignTokens.DisabledBg),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = materialSymbolName,
                    size = 20.dp,
                    tint = if (enabled) (titleColor ?: SettingsDesignTokens.PrimaryPurple) else SettingsDesignTokens.DisabledText,
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
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = titleColor ?: (if (enabled) SettingsDesignTokens.TextPrimary else SettingsDesignTokens.DisabledText),
                ),
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        color = SettingsDesignTokens.TextSecondary,
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
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Surface(
        modifier = Modifier
            .clip(SettingsDesignTokens.PillShape)
            .then(clickableModifier),
        shape = SettingsDesignTokens.PillShape,
        color = SettingsDesignTokens.SoftLavender,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = SettingsDesignTokens.PrimaryPurple,
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
    RememberSwitch(
        checked = checked,
        onCheckedChange = if (enabled) onCheckedChange else null,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = SettingsDesignTokens.PrimaryPurple,
            checkedIconColor = SettingsDesignTokens.PrimaryPurple,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = SettingsDesignTokens.DisabledSwitchTrack,
            uncheckedBorderColor = Color.Transparent,
            disabledUncheckedTrackColor = SettingsDesignTokens.DisabledSwitchTrack,
            disabledUncheckedThumbColor = Color.White,
        ),
        thumbContent = if (checked) {
            {
                RememberMaterialRoundedSymbol(
                    name = "check",
                    size = SwitchDefaults.IconSize,
                    tint = SettingsDesignTokens.PrimaryPurple,
                    weight = FontWeight.Bold,
                )
            }
        } else null,
    )
}

@Composable
fun SettingsDivider(hasIcon: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (hasIcon) 66.dp else 16.dp),
        color = SettingsDesignTokens.DividerColor,
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
        modifier = modifier
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
            enter = expandVertically(
                animationSpec = spatialSpec,
                expandFrom = Alignment.Top,
            ) + fadeIn(fadeInSpec),
            exit = shrinkVertically(
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
        onClick = if (enabled) { { onChange(!checked) } } else null,
        trailingContent = {
            SettingsCustomSwitch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
            )
        },
    )
}
