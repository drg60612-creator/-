package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.DiagnosticLogEntry
import com.example.data.DiagnosticsManager
import com.example.data.DiagnosticsStatus
import com.example.data.model.AuditLog
import com.example.data.model.ServiceLocation
import com.example.data.model.ShiftPeriod
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MembersViewModel,
    auditLogs: List<AuditLog>,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val serviceLocations by viewModel.serviceLocations.collectAsState()
    val periods by viewModel.periods.collectAsState()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()
    val diagnosticsStatus by viewModel.diagnosticsStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: أماكن الخدمة, 1: الفترات, 2: سجل العمليات, 3: التشخيص والاتصال

    // Dialog state for Service Location
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var locationToEdit by remember { mutableStateOf<ServiceLocation?>(null) }
    var locationToDelete by remember { mutableStateOf<ServiceLocation?>(null) }

    // Dialog state for Period
    var showAddPeriodDialog by remember { mutableStateOf(false) }
    var periodToEdit by remember { mutableStateOf<ShiftPeriod?>(null) }
    var periodToDelete by remember { mutableStateOf<ShiftPeriod?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshDiagnostics()
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
                        text = "الإعدادات والنظام",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = {
                            performSignOut(context, scope) {
                                onSignedOut()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("خروج", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("أماكن الخدمة (${serviceLocations.count { it.active }})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("الفترات (${periods.count { it.active }})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("سجل العمليات (${auditLogs.size})") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("التشخيص والاتصال (${diagnosticLogs.size})") }
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
            when (selectedTab) {
                0 -> {
                    // Service Locations Tab
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "إدارة أماكن الخدمة وأقسام المستشفى",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "تنعكس فوراً على خيارات تسجيل المناوبات",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { showAddLocationDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("+ إضافة مكان", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(serviceLocations, key = { it.id.ifBlank { it.name } }) { loc ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (loc.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = loc.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (loc.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!loc.active) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.errorContainer
                                                    ) {
                                                        Text(
                                                            text = "معطل",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = { locationToEdit = loc }) {
                                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { locationToDelete = loc }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Shift Periods Tab
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "إدارة فترات المناوبات",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "تحديد أوقات المناوبة اليومية (صباحية، مسائية، ليلية...)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { showAddPeriodDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("+ إضافة فترة", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(periods, key = { it.id.ifBlank { it.name } }) { per ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (per.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = per.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (per.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!per.active) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.errorContainer
                                                    ) {
                                                        Text(
                                                            text = "معطلة",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = { periodToEdit = per }) {
                                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { periodToDelete = per }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Audit Logs Tab
                    if (auditLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "لا توجد عمليات مسجلة بعد",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(auditLogs, key = { it.id }) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = log.action,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = log.timestamp?.toDate()?.toString() ?: "الآن",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = log.details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Diagnostics & Connection Log Tab
                    DiagnosticsTab(
                        status = diagnosticsStatus,
                        logs = diagnosticLogs,
                        isLoading = isLoading,
                        onTestConnection = viewModel::testConnection,
                        onClearLogs = { DiagnosticsManager.clearLogs() },
                        onRefresh = { viewModel.refreshDiagnostics() }
                    )
                }
            }
        }
    }

    // Add Service Location Dialog
    if (showAddLocationDialog) {
        var newLocName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddLocationDialog = false },
            title = { Text("إضافة مكان خدمة جديد") },
            text = {
                OutlinedTextField(
                    value = newLocName,
                    onValueChange = { newLocName = it },
                    label = { Text("اسم مكان الخدمة (مثال: عناية مركزة)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLocName.isNotBlank()) {
                            viewModel.addServiceLocation(newLocName.trim()) {
                                showAddLocationDialog = false
                            }
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLocationDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Edit Service Location Dialog
    if (locationToEdit != null) {
        var editName by remember { mutableStateOf(locationToEdit!!.name) }
        AlertDialog(
            onDismissRequest = { locationToEdit = null },
            title = { Text("تعديل اسم مكان الخدمة") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("اسم المكان") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateServiceLocation(locationToEdit!!.id, locationToEdit!!.name, editName.trim()) {
                                locationToEdit = null
                            }
                        }
                    }
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Service Location Confirmation Dialog
    if (locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { locationToDelete = null },
            title = { Text("تأكيد حذف أو تعطيل مكان الخدمة") },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف (${locationToDelete!!.name})؟\nإذا كان المكان مستخدماً في مناوبات مسجلة، فسيتم تعطيله بأمان لمنع فقدان البيانات التاريخية.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteServiceLocation(locationToDelete!!.id, locationToDelete!!.name) {
                            locationToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Add Period Dialog
    if (showAddPeriodDialog) {
        var newPeriodName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPeriodDialog = false },
            title = { Text("إضافة فترة مناوبة جديدة") },
            text = {
                OutlinedTextField(
                    value = newPeriodName,
                    onValueChange = { newPeriodName = it },
                    label = { Text("اسم الفترة (مثال: نوبة طوارئ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPeriodName.isNotBlank()) {
                            viewModel.addPeriod(newPeriodName.trim()) {
                                showAddPeriodDialog = false
                            }
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPeriodDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Edit Period Dialog
    if (periodToEdit != null) {
        var editPeriodName by remember { mutableStateOf(periodToEdit!!.name) }
        AlertDialog(
            onDismissRequest = { periodToEdit = null },
            title = { Text("تعديل اسم فترة المناوبة") },
            text = {
                OutlinedTextField(
                    value = editPeriodName,
                    onValueChange = { editPeriodName = it },
                    label = { Text("اسم الفترة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editPeriodName.isNotBlank()) {
                            viewModel.updatePeriod(periodToEdit!!.id, periodToEdit!!.name, editPeriodName.trim()) {
                                periodToEdit = null
                            }
                        }
                    }
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { periodToEdit = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Period Confirmation Dialog
    if (periodToDelete != null) {
        AlertDialog(
            onDismissRequest = { periodToDelete = null },
            title = { Text("تأكيد حذف أو تعطيل الفترة") },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف فترة (${periodToDelete!!.name})؟\nإذا كانت الفترة مستخدمة في مناوبات سابقة، فسيتم تعطيلها بأمان لمنع فقدان البيانات التاريخية.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePeriod(periodToDelete!!.id, periodToDelete!!.name) {
                            periodToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { periodToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun DiagnosticsTab(
    status: DiagnosticsStatus,
    logs: List<DiagnosticLogEntry>,
    isLoading: Boolean,
    onTestConnection: () -> Unit,
    onClearLogs: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حالة الاتصال والنظام السحابي",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Divider()

                    DiagnosticRow("الشبكة والإنترنت", if (status.isNetworkConnected) "متصل بالإنترنت" else "غير متصل", if (status.isNetworkConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                    DiagnosticRow("المصادقة (Auth)", if (status.isUserSignedIn) "مسجل الدخول" else "غير مسجل", if (status.isUserSignedIn) Color(0xFF10B981) else Color(0xFFF59E0B))
                    DiagnosticRow("البريد الحالي", status.userEmail, MaterialTheme.colorScheme.onSurface)
                    DiagnosticRow("معرف المستخدم (UID)", status.userId.take(16) + if (status.userId.length > 16) "..." else "", MaterialTheme.colorScheme.onSurfaceVariant)
                    DiagnosticRow("مشروع Firebase", status.projectId, MaterialTheme.colorScheme.primary)
                    DiagnosticRow("معرف Firestore", status.databaseId, MaterialTheme.colorScheme.primary)

                    if (status.lastPingResult != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "نتيجة الفحص الأخير: ${status.lastPingResult}",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Button(
                        onClick = onTestConnection,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جارٍ فحص استجابة قاعدة البيانات...")
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("فحص الاتصال المباشر بقاعدة البيانات (Live Ping)")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل العمليات والأخطاء التفصيلي (${logs.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (logs.isNotEmpty()) {
                    TextButton(onClick = onClearLogs) {
                        Text("مسح السجل", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد أخطاء مسجلة. كافة العمليات المسجلة حتى الآن سليمة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (log.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (log.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (log.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = log.operation,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = log.timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = log.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (log.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )

                        if (!log.details.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = log.details,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(title: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

private fun performSignOut(context: Context, scope: CoroutineScope, onComplete: () -> Unit) {
    FirebaseAuth.getInstance().signOut()
    scope.launch {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Ignore
        } finally {
            onComplete()
        }
    }
}
