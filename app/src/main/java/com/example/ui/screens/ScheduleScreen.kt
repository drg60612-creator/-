package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Member
import com.example.data.model.Shift
import com.google.firebase.auth.FirebaseAuth

data class DraftShiftRow(
    val id: String = java.util.UUID.randomUUID().toString(),
    var member: Member? = null,
    var actualShiftDay: String = "السبت",
    var date: String = "2026-10-18",
    var period: String = "صباحية",
    var serviceLocation: String = "م. رجال",
    var manualStatus: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MembersViewModel,
    members: List<Member>,
    shifts: List<Shift>,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Shift Entry Table, 1: Saved Shifts Records
    val activeMembers = members.filter { !it.archived }

    var draftRows = remember { mutableStateListOf(DraftShiftRow()) }
    var showOverrideDialog by remember { mutableStateOf<Shift?>(null) }

    val serviceLocations by viewModel.serviceLocations.collectAsState()
    val shiftPeriods by viewModel.periods.collectAsState()

    val availableLocations = remember(serviceLocations) {
        val active = serviceLocations.filter { it.active }.map { it.name }
        if (active.isEmpty()) listOf("م. رجال", "م. نساء", "trauma", "CPR", "CPR جديد", "عمليات-قسم", "دورة أو تدريب") else active
    }

    val availablePeriods = remember(shiftPeriods) {
        val active = shiftPeriods.filter { it.active }.map { it.name }
        if (active.isEmpty()) listOf("صباحية", "مسائية", "ليلية") else active
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جدول المناوبات والحضور",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("الرئيسية", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("إدخال جدول جديد") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("سجل المناوبات المحفوظة (${shifts.size})") }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "إدخال صفوف المناوبات (تعبئة تفاعلية)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                val newRow = DraftShiftRow(
                                    period = availablePeriods.firstOrNull() ?: "صباحية",
                                    serviceLocation = availableLocations.firstOrNull() ?: "م. رجال"
                                )
                                draftRows.add(newRow)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "+ جديد",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1
                            )
                        }
                    }

                    if (activeMembers.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = "لا توجد أعضاء نشطون مسجلون. يجدر بك إضافة أعضاء أولاً من صفحة الأعضاء لتتمكن من جدولتهم.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(draftRows, key = { it.id }) { row ->
                                ShiftDraftRowCard(
                                    row = row,
                                    activeMembers = activeMembers,
                                    periods = availablePeriods,
                                    locations = availableLocations,
                                    onDelete = { if (draftRows.size > 1) draftRows.remove(row) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                for (row in draftRows) {
                                    if (row.member != null) {
                                        val shift = Shift(
                                            memberId = row.member!!.id,
                                            memberName = row.member!!.name,
                                            defaultShiftDay = row.member!!.defaultShiftDay,
                                            actualShiftDay = row.actualShiftDay,
                                            date = row.date,
                                            period = row.period,
                                            serviceLocation = row.serviceLocation,
                                            status = if (row.actualShiftDay == row.member!!.defaultShiftDay) "حاضر" else "تغطية",
                                            manualOverride = false
                                        )
                                        viewModel.saveShift(shift) {}
                                    }
                                }
                                draftRows.clear()
                                draftRows.add(DraftShiftRow())
                                selectedTab = 1
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = activeMembers.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ جدول المناوبات نهائياً في السحاب")
                        }
                    }
                }
            } else {
                if (shifts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لا توجد سجلات مناوبات محفوظة بعد",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shifts, key = { it.id }) { shift ->
                            SavedShiftCard(
                                shift = shift,
                                onManualOverride = { showOverrideDialog = shift },
                                onDelete = { viewModel.deleteShift(shift.id, shift.memberName, shift.date) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOverrideDialog != null) {
        ManualOverrideDialog(
            shift = showOverrideDialog!!,
            onDismiss = { showOverrideDialog = null },
            onSave = { updatedShift ->
                viewModel.saveShift(updatedShift) {}
                showOverrideDialog = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDraftRowCard(
    row: DraftShiftRow,
    activeMembers: List<Member>,
    periods: List<String>,
    locations: List<String>,
    onDelete: () -> Unit
) {
    var memberExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }
    var locationExpanded by remember { mutableStateOf(false) }

    val days = listOf(
        "السبت", "السبت 1", "السبت 2",
        "الأحد", "الأحد 1", "الأحد 2",
        "الاثنين", "الاثنين 1", "الاثنين 2",
        "الثلاثاء", "الثلاثاء 1", "الثلاثاء 2",
        "الأربعاء", "الأربعاء 1", "الأربعاء 2",
        "الخميس", "الخميس 1", "الخميس 2",
        "الجمعة", "الجمعة 1", "الجمعة 2"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "صف مناوبة جديد",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف الصف", tint = MaterialTheme.colorScheme.error)
                }
            }

            ExposedDropdownMenuBox(
                expanded = memberExpanded,
                onExpandedChange = { memberExpanded = !memberExpanded }
            ) {
                OutlinedTextField(
                    value = row.member?.name ?: "اختر المتطوع...",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("اسم المتطوع") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = memberExpanded,
                    onDismissRequest = { memberExpanded = false }
                ) {
                    activeMembers.forEach { m ->
                        DropdownMenuItem(
                            text = { Text("${m.name} (أساسي: ${m.defaultShiftDay})") },
                            onClick = {
                                row.member = m
                                memberExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = row.member?.defaultShiftDay ?: "يُحدد تلقائياً عند اختيار العضو",
                onValueChange = {},
                readOnly = true,
                label = { Text("يوم المناوبة الأساسي (مصدره ملف العضو)") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = dayExpanded,
                onExpandedChange = { dayExpanded = !dayExpanded }
            ) {
                OutlinedTextField(
                    value = row.actualShiftDay,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("يوم المناوبة الفعلي") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = dayExpanded,
                    onDismissRequest = { dayExpanded = false }
                ) {
                    days.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d) },
                            onClick = {
                                row.actualShiftDay = d
                                dayExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = row.date,
                onValueChange = { row.date = it },
                label = { Text("التاريخ (مثال: 2026-10-18)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = periodExpanded,
                onExpandedChange = { periodExpanded = !periodExpanded }
            ) {
                OutlinedTextField(
                    value = row.period,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("الفترة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = periodExpanded,
                    onDismissRequest = { periodExpanded = false }
                ) {
                    periods.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p) },
                            onClick = {
                                row.period = p
                                periodExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = locationExpanded,
                onExpandedChange = { locationExpanded = !locationExpanded }
            ) {
                OutlinedTextField(
                    value = row.serviceLocation,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("مكان الخدمة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = locationExpanded,
                    onDismissRequest = { locationExpanded = false }
                ) {
                    locations.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(loc) },
                            onClick = {
                                row.serviceLocation = loc
                                locationExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedShiftCard(
    shift: Shift,
    onManualOverride: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shift.memberName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusBadge(status = shift.status)
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "التاريخ: ${shift.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "اليوم الأساسي: ${shift.defaultShiftDay} | الفعلي: ${shift.actualShiftDay}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "الفترة: ${shift.period} | المكان: ${shift.serviceLocation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (shift.manualOverride) {
                        Text(text = "معدل يدويًا (${shift.modifiedBy}): ${shift.overrideReason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onManualOverride) {
                        Icon(Icons.Default.EditNote, contentDescription = "تعديل يدوي للحالة", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف السجل", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, container) = when (status) {
        "حاضر" -> Color(0xFF10B981) to Color(0xFF064E3B)
        "تغطية" -> Color(0xFF3B82F6) to Color(0xFF1E3A8A)
        "مستأذن" -> Color(0xFFF59E0B) to Color(0xFF78350F)
        else -> Color(0xFFEF4444) to Color(0xFF7F1D1D)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualOverrideDialog(
    shift: Shift,
    onDismiss: () -> Unit,
    onSave: (Shift) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(shift.status) }
    var reason by remember { mutableStateOf(shift.overrideReason) }
    var statusExpanded by remember { mutableStateOf(false) }

    val statuses = listOf("حاضر", "غائب", "تغطية", "مستأذن")
    val currentUser = FirebaseAuth.getInstance().currentUser?.email ?: "مشرف النظام"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("التصحيح اليدوي لحالة المناوبة") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "المتطوع: ${shift.memberName} بتاريخ ${shift.date}", style = MaterialTheme.typography.bodyMedium)

                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = !statusExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedStatus,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الحالة اليدوية المعتمدة") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        statuses.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st) },
                                onClick = {
                                    selectedStatus = st
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب التصحيح / الإذن") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = shift.copy(
                        status = selectedStatus,
                        manualOverride = true,
                        overrideReason = reason,
                        modifiedBy = currentUser
                    )
                    onSave(updated)
                }
            ) {
                Text("حفظ التعديل المعتمد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
