package three.haddad.smartnotas.ui.edit

import androidx.compose.foundation.BorderStroke
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.ui.common.AppBottomSheet
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.components.RememberConfirmDialog
import three.haddad.smartnotas.ui.settings.SettingsDesignTokens

private val DEFAULT_SHOULDER_COLOR = SettingsDesignTokens.PrimaryPurple

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
        containerColor = Color.White,
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
                        onUpdateShoulder(target.copy(name = cleanName, normalizedName = cleanName.lowercase(), colorHex = colorHex))
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
    val filtered = remember(shoulders, searchQuery) {
        if (searchQuery.isBlank()) shoulders else shoulders.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val selectedShoulder = shoulders.firstOrNull { it.id == selectedShoulderId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF32313F),
                    ),
                )
                Text(
                    text = stringResource(R.string.shoulder_sheet_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF89899C),
                    ),
                )
            }
            Surface(
                shape = CircleShape,
                color = Color(0xFFF5F4F9),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "close",
                        size = 18.dp,
                        tint = Color(0xFF89899C),
                    )
                }
            }
        }

        // Search box
        Surface(
            shape = CircleShape,
            color = Color(0xFFF5F4F9),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.shoulder_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF89899C)),
                    )
                },
                leadingIcon = {
                    RememberMaterialRoundedSymbol(
                        name = "search",
                        size = 20.dp,
                        tint = Color(0xFF89899C),
                    )
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = SettingsDesignTokens.PrimaryPurple,
                    focusedContainerColor = Color(0xFFF5F4F9),
                    unfocusedContainerColor = Color(0xFFF5F4F9),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Shoulder items list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.id }) { shoulder ->
                val isSelected = selectedShoulderId == shoulder.id
                val shoulderColor = parseColorHex(shoulder.colorHex, DEFAULT_SHOULDER_COLOR)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) shoulderColor.copy(alpha = 0.12f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectId(shoulder.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Color dot
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(shoulderColor),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shoulder.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF32313F),
                                ),
                            )
                            Text(
                                text = stringResource(
                                    if (isSelected) R.string.shoulder_assigned_status else R.string.shoulder_assign_status
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = if (isSelected) shoulderColor else Color(0xFF89899C),
                                ),
                            )
                        }

                        // Selection check
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) shoulderColor else Color.Transparent)
                                .border(
                                    if (isSelected) 0.dp else 1.5.dp,
                                    if (isSelected) Color.Transparent else Color(0xFFD1D1D6),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 14.dp,
                                    tint = Color.White,
                                    weight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        // Edit pencil button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onEditShoulder(shoulder) },
                            contentAlignment = Alignment.Center,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "edit",
                                size = 18.dp,
                                tint = SettingsDesignTokens.PrimaryPurple,
                            )
                        }
                    }
                }
            }

            // No shoulder option
            item {
                val isSelected = selectedShoulderId == null
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) SettingsDesignTokens.SoftLavender else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectId(null) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "cancel",
                            size = 18.dp,
                            tint = if (isSelected) SettingsDesignTokens.PrimaryPurple else Color(0xFF89899C),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.shoulder_none),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF32313F),
                                ),
                            )
                            val assignedShoulderName = selectedShoulder?.name
                                ?: shoulders.firstOrNull { it.id == initialAssignedShoulderId }?.name
                            val noShoulderSubtitle = if (assignedShoulderName != null) {
                                stringResource(R.string.shoulder_remove_from_note, assignedShoulderName)
                            } else {
                                stringResource(R.string.shoulder_no_assigned)
                            }
                            Text(
                                text = noShoulderSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFF89899C),
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SettingsDesignTokens.PrimaryPurple else Color.Transparent)
                                .border(
                                    if (isSelected) 0.dp else 1.5.dp,
                                    if (isSelected) Color.Transparent else Color(0xFFD1D1D6),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 14.dp,
                                    tint = Color.White,
                                    weight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected note preview
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF8F8FC),
            border = BorderStroke(1.dp, Color(0xFFE9E3EF)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedShoulder != null) {
                    val activeColor = parseColorHex(selectedShoulder.colorHex, DEFAULT_SHOULDER_COLOR)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(activeColor),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    val resolvedTitle = noteTitle?.ifBlank { null }
                        ?: stringResource(R.string.edit_note_title_existing)
                    Text(
                        text = resolvedTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF32313F),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.shoulder_preview_stripe_hint),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = Color(0xFF89899C),
                        ),
                    )
                }
                if (selectedShoulder != null) {
                    val activeColor = parseColorHex(selectedShoulder.colorHex, DEFAULT_SHOULDER_COLOR)
                    Surface(
                        shape = CircleShape,
                        color = activeColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = selectedShoulder.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = activeColor,
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
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, SettingsDesignTokens.PrimaryPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(onClick = onCreateNewClick),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "add",
                        size = 18.dp,
                        tint = SettingsDesignTokens.PrimaryPurple,
                        weight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.shoulder_create_new),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SettingsDesignTokens.PrimaryPurple,
                        ),
                    )
                }
            }

            // Apply button
            Surface(
                shape = CircleShape,
                color = SettingsDesignTokens.PrimaryPurple,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(onClick = onApply),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val applyLabel = if (selectedShoulder != null) {
                        stringResource(R.string.shoulder_apply_named, selectedShoulder.name)
                    } else {
                        stringResource(R.string.shoulder_apply)
                    }
                    Text(
                        text = applyLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
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
    var hue by remember(shoulder) { mutableFloatStateOf(parseHueFromHex(shoulder?.colorHex)) }
    var saturation by remember(shoulder) { mutableFloatStateOf(parseSaturationFromHex(shoulder?.colorHex)) }
    var brightness by remember(shoulder) { mutableFloatStateOf(parseValueFromHex(shoulder?.colorHex)) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

    val currentColor = remember(hue, saturation, brightness) {
        val hsv = floatArrayOf(hue, saturation, brightness)
        Color(AndroidColor.HSVToColor(hsv))
    }
    val hexString = remember(currentColor) {
        String.format("#%06X", 0xFFFFFF and AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                    color = Color(0xFFF5F4F9),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "arrow_back",
                            size = 18.dp,
                            tint = Color(0xFF32313F),
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(if (shoulder != null) R.string.shoulder_edit_title else R.string.shoulder_create_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color(0xFF32313F),
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.shoulder_edit_subtitle,
                            name.ifBlank { stringResource(R.string.shoulder_none) },
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = Color(0xFF89899C),
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
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier
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
                    color = Color(0xFFF5F4F9),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = "close",
                            size = 18.dp,
                            tint = Color(0xFF89899C),
                        )
                    }
                }
            }
        }

        // Name input
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.shoulder_name_label),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    color = Color(0xFF89899C),
                ),
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE9E3EF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.shoulder_name_placeholder), color = Color(0xFF89899C)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = SettingsDesignTokens.PrimaryPurple,
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
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF89899C),
                    ),
                )
                Text(
                    text = "${(saturation * 100).toInt()}% · ${(brightness * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF89899C),
                    ),
                )
            }

            val pureHueColor = remember(hue) {
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(hue) {
                        detectDragGestures { change, _ ->
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            val y = (change.position.y / size.height).coerceIn(0f, 1f)
                            saturation = x
                            brightness = 1f - y
                        }
                    },
            ) {
                // Saturation gradient (white to pure hue)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.White, pureHueColor),
                            ),
                        ),
                )
                // Brightness gradient (transparent to black)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                            ),
                        ),
                )

                // Circle handle
                val handleX = saturation
                val handleY = 1f - brightness

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = handleX * size.width
                    val cy = handleY * size.height
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(Color.Black.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    drawCircle(currentColor, radius = 7.dp.toPx(), center = Offset(cx, cy))
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
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF89899C),
                    ),
                )
                Text(
                    text = "${hue.toInt()}° · Full spectrum",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF89899C),
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                            ),
                        ),
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            hue = x * 360f
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val handleX = (hue / 360f) * size.width
                    drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(handleX, size.height / 2f))
                    drawCircle(Color.Black.copy(alpha = 0.2f), radius = 10.dp.toPx(), center = Offset(handleX, size.height / 2f), style = Stroke(2.dp.toPx()))
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
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9E3EF)),
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = hexString,
                    onValueChange = { input ->
                        val parsed = parseColorHexOrNull(input)
                        if (parsed != null) {
                            val hsv = FloatArray(3)
                            AndroidColor.colorToHSV(parsed, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    leadingIcon = {
                        Text("HEX", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF89899C)))
                    },
                    trailingIcon = {
                        RememberMaterialRoundedSymbol(name = "edit", size = 18.dp, tint = Color(0xFF89899C))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = SettingsDesignTokens.PrimaryPurple,
                    ),
                )
            }

            // Color preview box
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(currentColor),
            )
        }

        // Note preview
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF8F8FC),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9E3EF)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(currentColor),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val resolvedTitle = noteTitle?.ifBlank { null }
                        ?: stringResource(R.string.edit_note_title_existing)
                    Text(
                        text = resolvedTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF32313F),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.shoulder_preview_stripe_hint),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = Color(0xFF89899C),
                        ),
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = currentColor.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = name.ifBlank { stringResource(R.string.shoulder_name_placeholder) },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = currentColor,
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
                shape = CircleShape,
                color = SettingsDesignTokens.SoftLavender,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SettingsDesignTokens.PrimaryPurple,
                        ),
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = SettingsDesignTokens.PrimaryPurple,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable {
                        if (name.isNotBlank()) {
                            onSave(name.trim(), hexString)
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.shoulder_save),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                        ),
                    )
                }
            }
        }
    }
}

private fun parseColorHex(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (_: Throwable) {
        fallback
    }
}

private fun parseColorHexOrNull(hex: String): Int? {
    val clean = if (hex.startsWith("#")) hex else "#$hex"
    if (clean.length != 7) return null
    return try {
        AndroidColor.parseColor(clean)
    } catch (_: Throwable) {
        null
    }
}

private fun parseHueFromHex(hex: String?): Float {
    if (hex.isNullOrBlank()) return 296f
    return try {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex"), hsv)
        hsv[0]
    } catch (_: Throwable) {
        296f
    }
}

private fun parseSaturationFromHex(hex: String?): Float {
    if (hex.isNullOrBlank()) return 0.5f
    return try {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex"), hsv)
        hsv[1]
    } catch (_: Throwable) {
        0.5f
    }
}

private fun parseValueFromHex(hex: String?): Float {
    if (hex.isNullOrBlank()) return 0.65f
    return try {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex"), hsv)
        hsv[2]
    } catch (_: Throwable) {
        0.65f
    }
}
