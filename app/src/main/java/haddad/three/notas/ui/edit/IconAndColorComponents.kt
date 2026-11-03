package haddad.three.notas.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.ui.calendar.CalendarDesignTokens
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.theme.LocalIsDark
import haddad.three.notas.ui.theme.NoteColors

@Composable
fun NoteIconAndColorCard(
    colorIndex: Int,
    iconKey: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val isDark = LocalIsDark.current
    val cardBg = CalendarDesignTokens.resolveCardBg(isDark)
    val cardBorder = CalendarDesignTokens.resolveCardBorder(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val accent = NoteColors.at(colorIndex)
            val resolvedColor =
                if (NoteColors.isNone(colorIndex)) {
                    MaterialTheme.colorScheme.primary
                } else if (isDark) {
                    accent.dark
                } else {
                    accent.light
                }

            val boxBg =
                if (NoteColors.isNone(colorIndex)) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    resolvedColor.copy(alpha = if (isDark) 0.25f else 0.15f)
                }

            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(boxBg),
                contentAlignment = Alignment.Center,
            ) {
                EventSmallIconRenderer(
                    iconKey = iconKey,
                    tint = resolvedColor,
                    size = 20.dp,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.event_icon_and_color_title),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary,
                        ),
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(resolvedColor),
                    )

                    Box(
                        modifier =
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(resolvedColor.copy(alpha = if (isDark) 0.25f else 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EventSmallIconRenderer(
                            iconKey = iconKey,
                            tint = resolvedColor,
                        )
                    }

                    val colorDisplayName =
                        when {
                            NoteColors.isNone(colorIndex) -> stringResource(R.string.color_none)
                            accent.name.equals("Orchid", ignoreCase = true) -> stringResource(R.string.color_purple)
                            else -> accent.name
                        }
                    val iconDisplayName = getEventIconDisplayName(iconKey)

                    Text(
                        text = "$colorDisplayName · $iconDisplayName",
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = textSecondary,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = 20.dp,
                tint = textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconAndColorSheet(
    currentColorIndex: Int,
    currentIconKey: String?,
    isDark: Boolean = LocalIsDark.current,
    onColorSelected: (Int) -> Unit,
    onIconSelected: (String?) -> Unit,
    onRequestMoreIcons: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val innerCardBg = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceVariant

    val currentAccent = NoteColors.at(currentColorIndex)
    val currentColor =
        if (NoteColors.isNone(currentColorIndex)) {
            MaterialTheme.colorScheme.primary
        } else if (isDark) {
            currentAccent.dark
        } else {
            currentAccent.light
        }

    val presetIcons =
        remember {
            listOf(
                "calendar_month",
                "event",
                "celebration",
                "cake",
                "star",
                "sports_soccer",
                "flight",
                "fitness_center",
                "restaurant",
                "school",
                "computer",
                "medical_services",
                "shopping_cart",
                "pets",
            )
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.event_icon_and_color_title),
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                        ),
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.calendar_event_save),
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
            }

            // Live preview card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = innerCardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(currentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        EventMediumIconRenderer(
                            iconKey = currentIconKey,
                            tint = Color.White,
                        )
                    }

                    Column(verticalArrangement = Arrangement.Center) {
                        val colorDisplayName =
                            when {
                                NoteColors.isNone(currentColorIndex) -> stringResource(R.string.color_none)
                                currentAccent.name.equals("Orchid", ignoreCase = true) -> stringResource(R.string.color_purple)
                                else -> currentAccent.name
                            }
                        val iconDisplayName = getEventIconDisplayName(currentIconKey)

                        Text(
                            text = "$colorDisplayName · $iconDisplayName",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                ),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.event_icon_and_color_title),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                ),
                        )
                    }
                }
            }

            // Color Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.event_icon_and_color_color_label),
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                        ),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(NoteColors.palette) { index, accent ->
                        val isSelected = index == currentColorIndex
                        val dotColor =
                            if (index == 0) {
                                if (isDark) Color(0xFF4A4A58) else Color(0xFFD4D4DC)
                            } else if (isDark) {
                                accent.dark
                            } else {
                                accent.light
                            }

                        Box(
                            modifier =
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                BorderStroke(3.dp, if (isDark) Color.White else Color(0xFF202028)),
                                                CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ).clickable { onColorSelected(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 18.dp,
                                    tint = if (index == 0 || accent.name == "Sun" || accent.name == "Lime") Color.Black else Color.White,
                                )
                            } else if (index == 0) {
                                RememberMaterialRoundedSymbol(
                                    name = "close",
                                    size = 16.dp,
                                    tint = if (isDark) Color.White else Color(0xFF6B6B76),
                                )
                            }
                        }
                    }
                }
            }

            // Icon Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.event_icon_and_color_icon_label),
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                        ),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(presetIcons) { iconName ->
                        val currentSymbol = extractSymbolName(currentIconKey)
                        val isSelected = currentSymbol.equals(iconName, ignoreCase = true)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) currentColor.copy(alpha = 0.18f) else innerCardBg,
                            border =
                                BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) currentColor else cardBorder,
                                ),
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clickable { onIconSelected("${ICON_SYMBOL_PREFIX}$iconName") },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = iconName,
                                    size = 22.dp,
                                    tint = if (isSelected) currentColor else textSecondary,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // "More icons…" button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = innerCardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRequestMoreIcons() },
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "category",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.event_icon_and_color_more_icons),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                ),
                            modifier = Modifier.weight(1f),
                        )
                        RememberMaterialRoundedSymbol(
                            name = "chevron_right",
                            size = 20.dp,
                            tint = textSecondary,
                        )
                    }
                }
            }
        }
    }
}

fun extractSymbolName(iconKey: String?): String {
    if (iconKey.isNullOrBlank()) return "calendar_month"
    return iconKey
        .removePrefix(ICON_SYMBOL_PREFIX)
        .removePrefix(ICON_SYMBOL_FILLED_PREFIX)
        .removePrefix(ICON_SYMBOL_OUTLINED_PREFIX)
        .removePrefix(ICON_EMOJI_PREFIX)
        .removePrefix(ICON_DRAWABLE_PREFIX)
        .ifBlank { "calendar_month" }
}

fun ensureCatalogIconKey(key: String?): String {
    if (key.isNullOrBlank()) return "${ICON_SYMBOL_PREFIX}calendar_month"
    if (key.startsWith(ICON_SYMBOL_PREFIX) ||
        key.startsWith(ICON_SYMBOL_FILLED_PREFIX) ||
        key.startsWith(ICON_SYMBOL_OUTLINED_PREFIX) ||
        key.startsWith(ICON_EMOJI_PREFIX) ||
        key.startsWith(ICON_DRAWABLE_PREFIX)
    ) {
        return key
    }
    return "${ICON_SYMBOL_PREFIX}$key"
}

fun getEventIconDisplayName(iconKey: String?): String {
    if (iconKey.isNullOrBlank()) return "Calendar"
    if (iconKey.startsWith(ICON_EMOJI_PREFIX)) {
        return iconEmojiPayload(iconKey) ?: "Emoji"
    }
    val symbol = extractSymbolName(iconKey)
    if (symbol.equals("calendar_month", ignoreCase = true) || symbol.equals("event", ignoreCase = true)) {
        return "Calendar"
    }
    val label = iconLabelFor(iconKey) ?: humanizeIconKey(iconKey)
    if (label.contains("calendar", ignoreCase = true)) return "Calendar"
    return label
}

@Composable
fun EventSmallIconRenderer(
    iconKey: String?,
    tint: Color,
    size: Dp = 12.dp,
) {
    if (iconKey?.startsWith(ICON_EMOJI_PREFIX) == true) {
        val emoji = iconEmojiPayload(iconKey) ?: ""
        Text(
            text = emoji,
            fontSize = (size.value * 0.9f).sp,
            lineHeight = (size.value * 0.9f).sp,
        )
    } else if (iconKey?.startsWith(ICON_DRAWABLE_PREFIX) == true) {
        val resId = iconDrawableRes(iconKey)
        if (resId != null) {
            Icon(
                painter = painterResource(resId),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size),
            )
        } else {
            RememberMaterialRoundedSymbol(
                name = "calendar_month",
                size = size,
                tint = tint,
            )
        }
    } else {
        val symbolName = extractSymbolName(iconKey)
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = size,
            tint = tint,
        )
    }
}

@Composable
fun EventMediumIconRenderer(
    iconKey: String?,
    tint: Color,
) {
    if (iconKey?.startsWith(ICON_EMOJI_PREFIX) == true) {
        val emoji = iconEmojiPayload(iconKey) ?: ""
        Text(
            text = emoji,
            fontSize = 26.sp,
        )
    } else if (iconKey?.startsWith(ICON_DRAWABLE_PREFIX) == true) {
        val resId = iconDrawableRes(iconKey)
        if (resId != null) {
            Icon(
                painter = painterResource(resId),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
        } else {
            RememberMaterialRoundedSymbol(
                name = "calendar_month",
                size = 28.dp,
                tint = tint,
            )
        }
    } else {
        val symbolName = extractSymbolName(iconKey)
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 28.dp,
            tint = tint,
        )
    }
}
