package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.CategoryEntity
import com.example.data.nlp.ParsedVoiceEntry
import com.example.data.nlp.VoiceTransactionParser
import com.example.data.voice.VoiceInputManager
import com.example.data.voice.VoiceInputState
import com.example.ui.theme.Emerald700
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.WarningOrange
import com.example.util.DateUtils
import com.example.util.PaisaHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEntryBottomSheet(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSaveTransaction: (type: String, amountPkr: Double, categoryId: String, note: String, dateMillis: Long) -> Unit,
    onEditInFullForm: (type: String, amountPkrStr: String, categoryId: String, note: String, dateMillis: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val voiceManager = remember { VoiceInputManager(context) }
    val voiceState by voiceManager.state.collectAsState()

    var selectedLang by remember { mutableStateOf("auto") } // "auto", "ur-PK", "en-US"
    var parsedResult by remember { mutableStateOf<ParsedVoiceEntry?>(null) }

    // Form confirmation state
    var selectedType by remember { mutableStateOf("EXPENSE") }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionDeniedCount by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            voiceManager.startListening(selectedLang)
        } else {
            permissionDeniedCount++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
        }
    }

    // Auto start listening if permission granted
    LaunchedEffect(hasPermission) {
        if (hasPermission && voiceState is VoiceInputState.Idle && parsedResult == null) {
            voiceManager.startListening(selectedLang)
        }
    }

    // Parse voice result when received
    LaunchedEffect(voiceState) {
        val state = voiceState
        if (state is VoiceInputState.Result) {
            val parsed = VoiceTransactionParser.parse(
                text = state.finalText,
                todayMillis = System.currentTimeMillis(),
                categories = categories
            )
            parsedResult = parsed

            // Populate form
            selectedType = parsed.type
            amountInput = if (parsed.amountMinor != null && parsed.amountMinor > 0) {
                PaisaHelper.paisaToEditableString(parsed.amountMinor)
            } else ""

            val filtered = categories.filter { it.type == parsed.type }
            val matchedCat = filtered.find { it.name.equals(parsed.categoryName, ignoreCase = true) }
                ?: filtered.find { it.is_default }
                ?: filtered.firstOrNull()

            selectedCategoryId = matchedCat?.id ?: ""
            noteInput = parsed.note
            selectedDateMillis = parsed.occurredOn
        }
    }

    // Date picker dialog
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("voice_entry_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Voice Expense Entry",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Language selection buttons
            val greenColor = MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        selectedLang = "auto"
                        if (hasPermission && parsedResult == null) voiceManager.startListening("auto")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedLang == "auto") greenColor else Color.Transparent,
                        contentColor = if (selectedLang == "auto") Color.White else greenColor
                    ),
                    border = BorderStroke(1.dp, greenColor),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Auto",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        selectedLang = "ur-PK"
                        if (hasPermission && parsedResult == null) voiceManager.startListening("ur-PK")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedLang == "ur-PK") greenColor else Color.Transparent,
                        contentColor = if (selectedLang == "ur-PK") Color.White else greenColor
                    ),
                    border = BorderStroke(1.dp, greenColor),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "اردو",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        selectedLang = "en-US"
                        if (hasPermission && parsedResult == null) voiceManager.startListening("en-US")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedLang == "en-US") greenColor else Color.Transparent,
                        contentColor = if (selectedLang == "en-US") Color.White else greenColor
                    ),
                    border = BorderStroke(1.dp, greenColor),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "English",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body depending on state
            if (!hasPermission) {
                // Permission Denied View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(ExpenseRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = null,
                            tint = ExpenseRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "Microphone Permission Required",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Please allow microphone access so the app can hear your expense in Urdu or English.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (permissionDeniedCount > 1) {
                        Button(
                            onClick = { openAppSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                        ) {
                            Text("Open App Settings")
                        }
                    } else {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else if (parsedResult == null) {
                // Listening or Error View
                when (val state = voiceState) {
                    is VoiceInputState.Listening -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Pulsing mic animation
                            PulsingMicIcon(onClick = { voiceManager.stopListening() })

                            Text(
                                text = "Listening… speak now",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Emerald700
                                )
                            )

                            // Live Partial Transcript
                            val transcript = state.partialText.ifBlank { "e.g., \"Petrol 2000 ka dalwaya\" or \"Salary 50000 mili\"" }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = transcript,
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDirection = TextDirection.Content
                                    ),
                                    color = if (state.partialText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }

                    is VoiceInputState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(40.dp)
                            )

                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onDismiss) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = { voiceManager.startListening(selectedLang) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Try Again")
                                }
                            }
                        }
                    }

                    else -> {
                        // Idle state
                        Button(
                            onClick = { voiceManager.startListening(selectedLang) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                        ) {
                            Text("Start Speaking")
                        }
                    }
                }
            } else {
                // Confirmation Sheet (never saves automatically!)
                val result = parsedResult!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Original Spoken Sentence Quote Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Emerald700.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = "“${result.note}”",
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDirection = TextDirection.Content
                            ),
                            color = Emerald700,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    // Low category confidence warning banner
                    if (result.categoryConfidence < 0.5f) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Please check the category",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = WarningOrange
                                    )
                                )
                            }
                        }
                    }

                    // Type Toggle (Expense / Income)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedType == "EXPENSE") ExpenseRed else Color.Transparent)
                                .clickable {
                                    selectedType = "EXPENSE"
                                    val filtered = categories.filter { it.type == "EXPENSE" }
                                    selectedCategoryId = filtered.firstOrNull()?.id ?: ""
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Expense",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedType == "EXPENSE") Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedType == "INCOME") IncomeGreen else Color.Transparent)
                                .clickable {
                                    selectedType = "INCOME"
                                    val filtered = categories.filter { it.type == "INCOME" }
                                    selectedCategoryId = filtered.firstOrNull()?.id ?: ""
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Income",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedType == "INCOME") Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Amount Input Field
                    val isAmountEmpty = amountInput.isBlank()
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Amount (PKR)") },
                        singleLine = true,
                        isError = isAmountEmpty,
                        supportingText = if (isAmountEmpty) {
                            { Text("No amount detected. Please enter amount.", color = ExpenseRed) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_amount_input")
                    )

                    // Category Selector Dropdown
                    val currentCategories = categories.filter { it.type == selectedType }
                    var expandedCatMenu by remember { mutableStateOf(false) }
                    val currentSelectedCat = currentCategories.find { it.id == selectedCategoryId }

                    ExposedDropdownMenuBox(
                        expanded = expandedCatMenu,
                        onExpandedChange = { expandedCatMenu = !expandedCatMenu },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentSelectedCat?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCatMenu) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = expandedCatMenu,
                            onDismissRequest = { expandedCatMenu = false }
                        ) {
                            currentCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        expandedCatMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Date & Note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(DateUtils.formatRelativeDate(selectedDateMillis))
                        }

                        TextButton(
                            onClick = {
                                parsedResult = null
                                voiceManager.startListening(selectedLang)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Try Again")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons: Save & Edit in full form
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onEditInFullForm(selectedType, amountInput, selectedCategoryId, noteInput, selectedDateMillis)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit in full form", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val amountDbl = amountInput.toDoubleOrNull() ?: 0.0
                                if (amountDbl > 0.0 && selectedCategoryId.isNotBlank()) {
                                    onSaveTransaction(
                                        selectedType,
                                        amountDbl,
                                        selectedCategoryId,
                                        noteInput,
                                        selectedDateMillis
                                    )
                                    onDismiss()
                                }
                            },
                            enabled = amountInput.toDoubleOrNull()?.let { it > 0.0 } == true && selectedCategoryId.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingMicIcon(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Emerald700)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Microphone",
            tint = Color.White,
            modifier = Modifier.size(38.dp)
        )
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    context.startActivity(intent)
}
