package haddad.three.notas.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.theme.LocalIsDark

/**
 * Top bar for the Calendar screen matching the reference design:
 * - Squircle menu button with calendar view dropdown
 * - Title and day subtitle
 */
@Composable
fun CalendarTopBar(
    title: String,
    subtitle: String,
    currentViewMode: CalendarViewMode,
    onViewModeSelected: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(currentViewMode) {
        menuExpanded = false
    }

    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = CalendarDesignTokens.ScreenHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Squircle Menu Button with Menu Icon and View Selector Dropdown
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = { menuExpanded = true },
                shape = CalendarDesignTokens.MenuButtonShape,
                color = CalendarDesignTokens.resolveCardBg(isDark),
                border =
                    BorderStroke(
                        width = 1.dp,
                        color = CalendarDesignTokens.resolveCardBorder(isDark),
                    ),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = stringResource(R.string.calendar_menu),
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            CalendarViewDropdown(
                expanded = menuExpanded,
                currentViewMode = currentViewMode,
                onDismiss = { menuExpanded = false },
                onSelectViewMode = { mode ->
                    menuExpanded = false
                    onViewModeSelected(mode)
                },
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Center / Left: Title & Day Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary,
                    ),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = textSecondary,
                        ),
                )
            }
        }
    }
}

/**
 * Dropdown popup allowing the user to switch between Day, Week, Month, and Year views.
 */
@Composable
fun CalendarViewDropdown(
    expanded: Boolean,
    currentViewMode: CalendarViewMode,
    onDismiss: () -> Unit,
    onSelectViewMode: (CalendarViewMode) -> Unit,
) {
    val isDark = LocalIsDark.current
    val menuBg = CalendarDesignTokens.resolveCardBg(isDark)
    val textPrimary = CalendarDesignTokens.resolveTextPrimary(isDark)
    val textSecondary = CalendarDesignTokens.resolveTextSecondary(isDark)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = menuBg,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, CalendarDesignTokens.resolveCardBorder(isDark)),
        modifier = Modifier.width(200.dp),
    ) {
        // Dropdown Header Label
        Text(
            text = stringResource(R.string.calendar_view_selector_title),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    color = textSecondary,
                    fontWeight = FontWeight.Medium,
                ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // View Mode Options
        CalendarViewMode.entries.forEach { mode ->
            val isSelected = mode == currentViewMode
            val itemBg =
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else textPrimary

            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(mode.labelRes),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor,
                                fontSize = 14.sp,
                            ),
                    )
                },
                leadingIcon = {
                    RememberMaterialRoundedSymbol(
                        name = mode.symbolName,
                        size = 18.dp,
                        tint = contentColor,
                    )
                },
                trailingIcon =
                    if (isSelected) {
                        {
                            RememberMaterialRoundedSymbol(
                                name = "check",
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                onClick = {
                    onDismiss()
                    onSelectViewMode(mode)
                },
                colors =
                    MenuDefaults.itemColors(
                        textColor = contentColor,
                        leadingIconColor = contentColor,
                        trailingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .background(itemBg, RoundedCornerShape(12.dp)),
            )
        }
    }
}
