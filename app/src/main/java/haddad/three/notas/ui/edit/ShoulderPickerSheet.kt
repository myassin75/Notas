package haddad.three.notas.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import java.util.Locale
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.ui.common.AppBottomSheet
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.RememberConfirmDialog
import haddad.three.notas.ui.components.formatColorToHex
import haddad.three.notas.ui.components.hsvToRgb
import haddad.three.notas.ui.components.isColorDark
import haddad.three.notas.ui.components.parseColorHexOrNull
import haddad.three.notas.ui.components.parseHsvFromHex
import haddad.three.notas.ui.components.parseNoteColorHex
import haddad.three.notas.ui.components.readableBadgeColors
import haddad.three.notas.ui.components.rgbToHsv
import haddad.three.notas.ui.theme.LocalIsDark

@Composable
fun ShoulderPickerSheet(
    shoulders: List<ShoulderEntity>,
    selectedShoulderId: Long?,
    noteTitle: String?,
    onSelectShoulder: (ShoulderEntity?) -> Unit,
    onCreateShoulder: (name: String, colorHex: String) -> Unit,
    onUpdateShoulder: (ShoulderEntity) -> Unit,
    onDeleteShoulder: (ShoulderEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSelectedId by remember(selectedShoulderId) { mutableStateOf(selectedShoulderId) }
    var editingShoulder by remember { mutableStateOf<ShoulderEntity?>(null) }
    var creatingShoulder by remember { mutableStateOf(false) }

    val activeSelectedShoulder = shoulders.firstOrNull { it.id == currentSelectedId }

    AppBottomSheet(
        title = stringResource(R.string.shoulder_sheet_title),
        onDismiss = onDismiss,
        showTitleBar = false,
        scrollable = false,
    ) {
        if (editingShoulder != null || creatingShoulder) {
            EditShoulderContent(
                shoulder = editingShoulder,
                noteTitle = noteTitle,
                onBack = {
                    editingShoulder = null
                    creatingShoulder = false
                },
                onSave = { name, colorHex ->
                    val target = editingShoulder
                    val cleanName = name.trim()
                    if (target != null) {
                        onUpdateShoulder(
                            target.copy(
                                name = cleanName,
                                normalizedName = cleanName.lowercase(),
                                colorHex = colorHex,
                            ),
                        )
                    } else {
                        onCreateShoulder(cleanName, colorHex)
                    }
                    editingShoulder = null
                    creatingShoulder = false
                },
                onDelete = {
                    val target = editingShoulder
                    if (target != null) {
                        if (currentSelectedId == target.id) {
                            currentSelectedId = null
                        }
                        onDeleteShoulder(target)
                    }
                    editingShoulder = null
                    creatingShoulder = false
                },
                onDismiss = onDismiss,
            )
        } else {
            ShoulderListContent(
                shoulders = shoulders,
                selectedShoulderId = currentSelectedId,
                initialAssignedShoulderId = selectedShoulderId,
                searchQuery = searchQuery,
                noteTitle = noteTitle,
                onSearchQueryChange = { searchQuery = it },
                onSelectId = { id -> currentSelectedId = id },
                onEditShoulder = { shoulder -> editingShoulder = shoulder },
                onCreateNewClick = { creatingShoulder = true },
                onApply = {
                    onSelectShoulder(activeSelectedShoulder)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun ShoulderListContent(
    shoulders: List<ShoulderEntity>,
    selectedShoulderId: Long?,
    initialAssignedShoulderId: Long?,
    searchQuery: String,
    noteTitle: String?,
    onSearchQueryChange: (String) -> Unit,
    onSelectId: (Long?) -> Unit,
    onEditShoulder: (ShoulderEntity) -> Unit,
    onCreateNewClick: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered =
        remember(shoulders, searchQuery) {
            if (searchQuery.isBlank()) shoulders else shoulders.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    val selectedShoulder = shoulders.firstOrNull { it.id == selectedShoulderId }

    val isDark = LocalIsDark.current
    val primaryAccent = MaterialTheme.colorScheme.primary
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF32313F)
    val textSecondary = if (isDark) Color(0xFFB8B6C6) else Color(0xFF6B687E)
    val textPlaceholder = if (isDark) Color(0xFFC8C6D4) else Color(0xFF757288)
    val pillSurface = if (isDark) Color(0xFF262626) else Color(0xFFF5F4F9)
    val cardSurface = if (isDark) Color(0xFF242424) else Color(0xFFF8F8FC)
    val cardBorder = if (isDark) Color(0xFF3A3A3A) else Color(0xFFE9E3EF)
    val noShoulderSelectedBg = if (isDark) primaryAccent.copy(alpha = 0.18f) else primaryAccent.copy(alpha = 0.10f)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shoulder_sheet_title),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = textPrimary,
                        ),
                )
                Text(
                    text = stringResource(R.string.shoulder_sheet_subtitle),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            color = textSecondary,
                        ),
                )
            }
            Surface(
                shape = CircleShape,
                color = pillSurface,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "close",
                        size = 18.dp,
                        tint = textSecondary,
                    )
                }
            }
        }

        // Search box
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = pillSurface,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.shoulder_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium.copy(color = textPlaceholder, fontSize = 14.5.sp),
                    )
                },
                leadingIcon = {
                    RememberMaterialRoundedSymbol(
                        name = "search",
                        size = 20.dp,
                        tint = textPlaceholder,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = primaryAccent,
                        focusedContainerColor = pillSurface,
                        unfocusedContainerColor = pillSurface,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedPlaceholderColor = textPlaceholder,
                        unfocusedPlaceholderColor = textPlaceholder,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Shoulder items list
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (filtered.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No shoulders matching \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium.copy(color = textSecondary),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(filtered, key = { it.id }) { shoulder ->
                val isSelected = selectedShoulderId == shoulder.id
                val shoulderColor = parseNoteColorHex(shoulder.colorHex, primaryAccent)
                val (_, readableFg) = readableBadgeColors(shoulderColor, isDark)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) shoulderColor.copy(alpha = if (isDark) 0.16f else 0.10f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, shoulderColor.copy(alpha = if (isDark) 0.50f else 0.40f)) else null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectId(shoulder.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Authoritative Color dot with high-contrast outline border
                        Box(
                            modifier =
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(shoulderColor)
                                    .border(
                                        1.dp,
                                        if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.18f),
                                        CircleShape,
                                    ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shoulder.name,
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textPrimary,
                                    ),
                            )
                            Text(
                                text =
                                    stringResource(
                                        if (isSelected) R.string.shoulder_assigned_status else R.string.shoulder_assign_status,
                                    ),
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        color = if (isSelected) readableFg else textSecondary,
                                    ),
                            )
                        }

                        // Unambiguous selection checkmark container
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) shoulderColor else Color.Transparent)
                                    .border(
                                        if (isSelected) 1.dp else 1.5.dp,
                                        if (isSelected) {
                                            if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.20f)
                                        } else {
                                            if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFD1D1D6)
                                        },
                                        CircleShape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                val checkTint = if (isColorDark(shoulderColor)) Color.White else Color(0xFF1E1E26)
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 14.dp,
                                    tint = checkTint,
                                    weight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        // Edit pencil button
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onEditShoulder(shoulder) },
                            contentAlignment = Alignment.Center,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "edit",
                                size = 18.dp,
                                tint = primaryAccent,
                            )
                        }
                    }
                }
            }

            // Distinct "No shoulder" option
            item {
                val isSelected = selectedShoulderId == null
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) noShoulderSelectedBg else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, primaryAccent.copy(alpha = if (isDark) 0.50f else 0.40f)) else null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectId(null) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "cancel",
                            size = 18.dp,
                            tint = if (isSelected) primaryAccent else textSecondary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.shoulder_none),
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textPrimary,
                                    ),
                            )
                            val assignedShoulderName =
                                selectedShoulder?.name
                                    ?: shoulders.firstOrNull { it.id == initialAssignedShoulderId }?.name
                            val noShoulderSubtitle =
                                if (assignedShoulderName != null) {
                                    stringResource(R.string.shoulder_remove_from_note, assignedShoulderName)
                                } else {
                                    stringResource(R.string.shoulder_no_assigned)
                                }
                            Text(
                                text = noShoulderSubtitle,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = textSecondary,
                                    ),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) primaryAccent else Color.Transparent)
                                    .border(
                                        if (isSelected) 0.dp else 1.5.dp,
                                        if (isSelected) Color.Transparent else if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFD1D1D6),
                                        CircleShape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 14.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    weight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected note preview with authoritative stripe and badge
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardSurface,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedShoulder != null) {
                    val activeColor = parseNoteColorHex(selectedShoulder.colorHex, primaryAccent)
                    Box(
                        modifier =
                            Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(activeColor),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    val resolvedTitle =
                        noteTitle?.ifBlank { null }
                            ?: stringResource(R.string.edit_note_title_existing)
                    Text(
                        text = resolvedTitle,
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = textPrimary,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (selectedShoulder != null) {
                                stringResource(R.string.shoulder_preview_stripe_hint)
                            } else {
                                stringResource(R.string.shoulder_no_assigned)
                            },
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = textSecondary,
                            ),
                    )
                }
                if (selectedShoulder != null) {
                    val activeColor = parseNoteColorHex(selectedShoulder.colorHex, primaryAccent)
                    val (badgeBg, badgeFg) = remember(activeColor, isDark) { readableBadgeColors(activeColor, isDark) }
                    Surface(
                        shape = CircleShape,
                        color = badgeBg,
                    ) {
                        Text(
                            text = selectedShoulder.name,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = badgeFg,
                                ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // Action buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Create new shoulder
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, primaryAccent),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onCreateNewClick),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "add",
                        size = 18.dp,
                        tint = primaryAccent,
                        weight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.shoulder_create_new),
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = primaryAccent,
                            ),
                    )
                }
            }

            // Apply button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = primaryAccent,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onApply),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val applyLabel =
                        if (selectedShoulder != null) {
                            stringResource(R.string.shoulder_apply_named, selectedShoulder.name)
                        } else {
                            stringResource(R.string.shoulder_apply)
                        }
                    Text(
                        text = applyLabel,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditShoulderContent(
    shoulder: ShoulderEntity?,
    noteTitle: String?,
    onBack: () -> Unit,
    onSave: (name: String, colorHex: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(shoulder) { mutableStateOf(shoulder?.name ?: "") }
    val initialHsv = remember(shoulder) { parseHsvFromHex(shoulder?.colorHex) }
    var hue by remember(shoulder) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(shoulder) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(shoulder) { mutableFloatStateOf(initialHsv[2]) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val initialHex =
        remember(shoulder) {
            shoulder?.colorHex?.let { if (it.startsWith("#")) it.uppercase(Locale.ROOT) else "#${it.uppercase(Locale.ROOT)}" }
                ?: formatColorToHex(hsvToRgb(initialHsv[0], initialHsv[1], initialHsv[2]))
        }
    var hexInput by remember(shoulder) { mutableStateOf(initialHex) }

    val parsedColorFromHex = remember(hexInput) { parseColorHexOrNull(hexInput) }
    val isHexValid = parsedColorFromHex != null
    val currentColor = parsedColorFromHex ?: remember(hue, saturation, brightness) { hsvToRgb(hue, saturation, brightness) }
    val canSave = name.isNotBlank() && isHexValid

    if (showDeleteConfirm && shoulder != null) {
        RememberConfirmDialog(
            title = stringResource(R.string.shoulder_delete_confirm_title),
            text = stringResource(R.string.shoulder_delete_confirm_message),
            confirmLabel = stringResource(R.string.edit_delete_forever_dialog_confirm),
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    val isDark = LocalIsDark.current
    val primaryAccent = MaterialTheme.colorScheme.primary
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF32313F)
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF89899C)
    val pillSurface = if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f) else Color(0xFFF5F4F9)
    val cardSurface = if (isDark) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f) else Color(0xFFF8F8FC)
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f) else Color(0xFFE9E3EF)
    val inputSurface = if (isDark) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f) else Color.White
    val cancelBtnBg = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant
    val cancelBtnText = if (isDark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val deleteBg = if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color(0xFFFFEBEE)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = pillSurface,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "arrow_back",
                            size = 18.dp,
                            tint = textPrimary,
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(if (shoulder != null) R.string.shoulder_edit_title else R.string.shoulder_create_title),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = textPrimary,
                            ),
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.shoulder_edit_subtitle,
                                name.ifBlank { stringResource(R.string.shoulder_none) },
                            ),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = textSecondary,
                            ),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (shoulder != null) {
                    Surface(
                        shape = CircleShape,
                        color = deleteBg,
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { showDeleteConfirm = true },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            RememberMaterialRoundedSymbol(
                                name = "delete",
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = pillSurface,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "close",
                            size = 18.dp,
                            tint = textSecondary,
                        )
                    }
                }
            }
        }

        // Name input
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.shoulder_name_label),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        color = textSecondary,
                    ),
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = inputSurface,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.shoulder_name_placeholder), color = textSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = primaryAccent,
                            focusedContainerColor = inputSurface,
                            unfocusedContainerColor = inputSurface,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Saturation & Brightness 2D Color Box
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Saturation & brightness",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            color = textSecondary,
                        ),
                )
                Text(
                    text = "${(saturation * 100).toInt()}% · ${(brightness * 100).toInt()}%",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            color = textSecondary,
                        ),
                )
            }

            val pureHueColor = remember(hue) { hsvToRgb(hue, 1f, 1f) }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(hue) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (size.width > 0 && size.height > 0) {
                                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                                        val y = (offset.y / size.height).coerceIn(0f, 1f)
                                        saturation = x
                                        brightness = 1f - y
                                        hexInput = formatColorToHex(hsvToRgb(hue, x, 1f - y))
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (size.width > 0 && size.height > 0) {
                                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val y = (change.position.y / size.height).coerceIn(0f, 1f)
                                        saturation = x
                                        brightness = 1f - y
                                        hexInput = formatColorToHex(hsvToRgb(hue, x, 1f - y))
                                    }
                                },
                            )
                        },
            ) {
                // Saturation gradient (white to pure hue)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.White, pureHueColor),
                                ),
                            ),
                )
                // Brightness gradient (transparent to black)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                ),
                            ),
                )

                // Circle handle
                val handleX = saturation.coerceIn(0f, 1f)
                val handleY = (1f - brightness).coerceIn(0f, 1f)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (size.width > 0 && size.height > 0) {
                        val cx = (handleX * size.width).coerceIn(0f, size.width)
                        val cy = (handleY * size.height).coerceIn(0f, size.height)
                        if (cx.isFinite() && cy.isFinite()) {
                            drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(cx, cy))
                            drawCircle(Color.Black.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                            drawCircle(currentColor, radius = 7.dp.toPx(), center = Offset(cx, cy))
                        }
                    }
                }
            }
        }

        // Hue Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Hue",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            color = textSecondary,
                        ),
                )
                Text(
                    text = "${hue.toInt()}° · Full spectrum",
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            color = textSecondary,
                        ),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Red,
                                        Color.Yellow,
                                        Color.Green,
                                        Color.Cyan,
                                        Color.Blue,
                                        Color.Magenta,
                                        Color.Red,
                                    ),
                            ),
                        ).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (size.width > 0) {
                                        val x = (offset.x / size.width).coerceIn(0f, 1f)
                                        val newHue = (x * 360f).coerceIn(0f, 360f)
                                        hue = newHue
                                        hexInput = formatColorToHex(hsvToRgb(newHue, saturation, brightness))
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (size.width > 0) {
                                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val newHue = (x * 360f).coerceIn(0f, 360f)
                                        hue = newHue
                                        hexInput = formatColorToHex(hsvToRgb(newHue, saturation, brightness))
                                    }
                                },
                            )
                        },
                contentAlignment = Alignment.CenterStart,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (size.width > 0 && size.height > 0) {
                        val handleX = ((hue / 360f) * size.width).coerceIn(0f, size.width)
                        val cy = size.height / 2f
                        if (handleX.isFinite() && cy.isFinite()) {
                            drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(handleX, cy))
                            drawCircle(Color.Black.copy(alpha = 0.2f), radius = 10.dp.toPx(), center = Offset(handleX, cy), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
            }
        }

        // HEX Input and Color Preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = inputSurface,
                border = BorderStroke(1.dp, if (!isHexValid && hexInput.isNotEmpty()) MaterialTheme.colorScheme.error else cardBorder),
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val clean = input.filter { ch -> ch == '#' || ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' }.take(7)
                        hexInput = clean.uppercase()
                        val parsed = parseColorHexOrNull(clean)
                        if (parsed != null) {
                            val hsv = rgbToHsv(parsed.red, parsed.green, parsed.blue)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    leadingIcon = {
                        Text(
                            text = "HEX",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = textSecondary),
                        )
                    },
                    trailingIcon = {
                        RememberMaterialRoundedSymbol(name = "edit", size = 18.dp, tint = textSecondary)
                    },
                    isError = hexInput.isNotEmpty() && !isHexValid,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = primaryAccent,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            focusedContainerColor = inputSurface,
                            unfocusedContainerColor = inputSurface,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                        ),
                )
            }

            // Authoritative Color preview box matching stored HEX value
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(currentColor)
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.18f),
                            RoundedCornerShape(14.dp),
                        ),
            )
        }

        // Note preview
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardSurface,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(currentColor),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val resolvedTitle =
                        noteTitle?.ifBlank { null }
                            ?: stringResource(R.string.edit_note_title_existing)
                    Text(
                        text = resolvedTitle,
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = textPrimary,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.shoulder_preview_stripe_hint),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = textSecondary,
                            ),
                    )
                }
                val (badgeBg, badgeFg) = remember(currentColor, isDark) { readableBadgeColors(currentColor, isDark) }
                Surface(
                    shape = CircleShape,
                    color = badgeBg,
                ) {
                    Text(
                        text = name.ifBlank { stringResource(R.string.shoulder_name_placeholder) },
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = badgeFg,
                            ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Footer Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cancelBtnBg,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onBack),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = cancelBtnText,
                            ),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (canSave) primaryAccent else primaryAccent.copy(alpha = 0.38f),
                modifier =
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = canSave) {
                            if (canSave) {
                                val finalHex = if (hexInput.startsWith("#")) hexInput.uppercase(Locale.ROOT) else "#${hexInput.uppercase(Locale.ROOT)}"
                                onSave(name.trim(), finalHex)
                            }
                        },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.shoulder_save),
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (canSave) 1f else 0.65f),
                            ),
                    )
                }
            }
        }
    }
}
