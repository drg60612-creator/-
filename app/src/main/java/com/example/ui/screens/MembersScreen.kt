package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.model.DayApproval

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    members: List<Member>,
    shifts: List<Shift>,
    dayApprovals: List<DayApproval>,
    searchQuery: String,
    selectedTab: Int, // 0: Active, 1: Archived
    onSearchQueryChange: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
    onAddMember: (Member, () -> Unit) -> Unit,
    onUpdateMember: (Member, () -> Unit) -> Unit,
    onArchiveMember: (String, String, Boolean) -> Unit,
    onImportCsv: (String, (Int) -> Unit) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }
    var inspectingMember by remember { mutableStateOf<Member?>(null) }

    val filteredMembers = members.filter { member ->
        val matchesTab = if (selectedTab == 0) !member.archived else member.archived
        val matchesSearch = member.name.contains(searchQuery, ignoreCase = true) ||
                member.supervisorName.contains(searchQuery, ignoreCase = true) ||
                member.defaultShiftDay.contains(searchQuery, ignoreCase = true)
        matchesTab && matchesSearch
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
                        text = "إدارة الأعضاء والمتطوعين",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showImportDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "استيراد ملف", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(
                            onClick = {
                                editingMember = null
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "+ عضو",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("بحث بالاسم، المشرف، أو يوم المناوبة...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) },
                        text = { Text("الأعضاء النشطون (${members.count { !it.archived }})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        text = { Text("الأرشيف (${members.count { it.archived }})") }
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
        ) {
            if (filteredMembers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.GroupOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد بيانات مطابقة",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMembers, key = { it.id }) { member ->
                        val memberShifts = shifts.filter { it.memberId == member.id }
                        val approvedAbsences = memberShifts.count { it.status == "غائب" && (dayApprovals.find { da -> da.date == it.date }?.approved == true) }

                        MemberCard(
                            member = member,
                            approvedAbsences = approvedAbsences,
                            onInspect = { inspectingMember = member },
                            onEdit = {
                                editingMember = member
                                showAddDialog = true
                            },
                            onArchiveToggle = { archivedState ->
                                onArchiveMember(member.id, member.name, archivedState)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemberFormDialog(
            memberToEdit = editingMember,
            onDismiss = { showAddDialog = false },
            onSave = { member ->
                if (editingMember == null) {
                    onAddMember(member) { showAddDialog = false }
                } else {
                    onUpdateMember(member) { showAddDialog = false }
                }
            }
        )
    }

    if (inspectingMember != null) {
        MemberHistoryDialog(
            member = inspectingMember!!,
            shifts = shifts.filter { it.memberId == inspectingMember!!.id },
            onDismiss = { inspectingMember = null }
        )
    }

    if (showImportDialog) {
        ImportCsvDialog(
            onDismiss = { showImportDialog = false },
            onImport = { csvText, onResult ->
                onImportCsv(csvText) { count ->
                    onResult(count)
                    showImportDialog = false
                }
            }
        )
    }
}

@Composable
fun MemberCard(
    member: Member,
    approvedAbsences: Int,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onArchiveToggle: (Boolean) -> Unit
) {
    // Border color based on absences: >= 4 red, < 4 green
    val borderColor = if (approvedAbsences >= 4) Color(0xFFEF4444) else Color(0xFF00A86B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (member.role == "مشرف") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = member.role,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (member.role == "مشرف") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column {
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "الغيابات المعتمدة: $approvedAbsences",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (approvedAbsences >= 4) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                BadgeIndicator(badge = member.badge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "يوم المناوبة: ${member.defaultShiftDay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المشرف: ${member.supervisorName.ifBlank { "غير محدد" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onArchiveToggle(!member.archived) }) {
                        Icon(
                            imageVector = if (member.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (member.archived) "استعادة" else "أرشفة",
                            tint = if (member.archived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemberHistoryDialog(
    member: Member,
    shifts: List<Shift>,
    onDismiss: () -> Unit
) {
    val presentCount = shifts.count { it.status == "حاضر" }
    val absentCount = shifts.count { it.status == "غائب" }
    val coverageCount = shifts.count { it.status == "تغطية" }
    val excusedCount = shifts.count { it.status == "مستأذن" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل العضو: ${member.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 4 Statistical Summary Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox("حضور", presentCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatBox("غياب", absentCount.toString(), Color(0xFFEF4444), Modifier.weight(1f))
                    StatBox("تغطية", coverageCount.toString(), Color(0xFF3B82F6), Modifier.weight(1f))
                    StatBox("استئذان", excusedCount.toString(), Color(0xFFF59E0B), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "السجل الزمني للمناوبات:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                if (shifts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "لا توجد سجلات مناوبات لهذا العضو بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shifts, key = { it.id }) { shift ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "التاريخ: ${shift.date}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        Text(text = "الأساسي: ${shift.defaultShiftDay} | الفعلي: ${shift.actualShiftDay}", style = MaterialTheme.typography.labelSmall)
                                        Text(text = "الفترة: ${shift.period} | المكان: ${shift.serviceLocation}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    StatusBadge(status = shift.status)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
fun StatBox(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BadgeIndicator(badge: String) {
    val (color, text) = when (badge) {
        "حمراء" -> Color(0xFFEF4444) to "علامة حمراء"
        "بيضاء" -> Color(0xFFE2E8F0) to "علامة بيضاء"
        else -> Color.Transparent to "بدون علامة"
    }

    if (badge != "بدون علامة") {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, color)
        ) {
            Text(
                text = badge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (badge == "حمراء") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberFormDialog(
    memberToEdit: Member?,
    onDismiss: () -> Unit,
    onSave: (Member) -> Unit
) {
    var name by remember { mutableStateOf(memberToEdit?.name ?: "") }
    var role by remember { mutableStateOf(memberToEdit?.role ?: "مناوب") }
    var defaultShiftDay by remember { mutableStateOf(memberToEdit?.defaultShiftDay ?: "السبت") }
    var badge by remember { mutableStateOf(memberToEdit?.badge ?: "بدون علامة") }
    var supervisorName by remember { mutableStateOf(memberToEdit?.supervisorName ?: "") }

    var roleExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }
    var badgeExpanded by remember { mutableStateOf(false) }

    val roles = listOf("مناوب", "مشرف")
    val days = listOf(
        "السبت", "السبت 1", "السبت 2",
        "الأحد", "الأحد 1", "الأحد 2",
        "الاثنين", "الاثنين 1", "الاثنين 2",
        "الثلاثاء", "الثلاثاء 1", "الثلاثاء 2",
        "الأربعاء", "الأربعاء 1", "الأربعاء 2",
        "الخميس", "الخميس 1", "الخميس 2",
        "الجمعة", "الجمعة 1", "الجمعة 2"
    )
    val badges = listOf("بدون علامة", "بيضاء", "حمراء")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (memberToEdit == null) "إضافة عضو جديد" else "تعديل بيانات العضو") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المتطوع") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = !roleExpanded }
                ) {
                    OutlinedTextField(
                        value = role,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الدور في الفريق") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roles.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    role = option
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = dayExpanded,
                    onExpandedChange = { dayExpanded = !dayExpanded }
                ) {
                    OutlinedTextField(
                        value = defaultShiftDay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("يوم المناوبة الأساسي") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dayExpanded,
                        onDismissRequest = { dayExpanded = false }
                    ) {
                        days.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    defaultShiftDay = option
                                    dayExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = badgeExpanded,
                    onExpandedChange = { badgeExpanded = !badgeExpanded }
                ) {
                    OutlinedTextField(
                        value = badge,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("العلامة المميزة") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = badgeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = badgeExpanded,
                        onDismissRequest = { badgeExpanded = false }
                    ) {
                        badges.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    badge = option
                                    badgeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = supervisorName,
                    onValueChange = { supervisorName = it },
                    label = { Text("المشرف عليه") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val updated = memberToEdit?.copy(
                            name = name,
                            role = role,
                            defaultShiftDay = defaultShiftDay,
                            badge = badge,
                            supervisorName = supervisorName
                        ) ?: Member(
                            name = name,
                            role = role,
                            defaultShiftDay = defaultShiftDay,
                            badge = badge,
                            supervisorName = supervisorName,
                            archived = false
                        )
                        onSave(updated)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun ImportCsvDialog(
    onDismiss: () -> Unit,
    onImport: (String, (Int) -> Unit) -> Unit
) {
    var csvText by remember {
        mutableStateOf(
            "أحمد محمد علي,مناوب,السبت,بدون علامة,د. خالد صالح\nفاطمة أحمد حسن,مشرف,الأحد,بيضاء,د. سامي عبد الله"
        )
    }
    var importedCount by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استيراد الأعضاء (ملف CSV / Excel)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "أدخل بيانات الأعضاء (اسم المتطوع, الدور, يوم المناوبة, العلامة, المشرف عليه) بحيث يكون كل عضو في سطر مستقل:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                if (importedCount != null) {
                    Text(
                        text = "تم استيراد $importedCount عضو بنجاح!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImport(csvText) { count ->
                        importedCount = count
                    }
                }
            ) {
                Text("استيراد فعلي")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
