package com.example.ui.screens

import androidx.compose.foundation.background
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
import com.example.data.model.DayApproval
import com.example.data.model.Shift
import com.example.utils.PdfReportGenerator
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MembersViewModel,
    shifts: List<Shift>,
    dayApprovals: List<DayApproval>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Daily, 1: Weekly, 2: Monthly

    var selectedDate by remember { mutableStateOf("2026-10-18") }
    var weeklyStartDate by remember { mutableStateOf("2026-10-18") }
    var monthlyYearMonth by remember { mutableStateOf("2026-10") }

    val currentUser = FirebaseAuth.getInstance().currentUser?.email ?: "مشرف النظام"

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
                        text = "التقارير الشاملة وتصدير PDF",
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
                        text = { Text("التقرير اليومي") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("التقرير الأسبوعي") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("التقرير الشهري") }
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
                0 -> DailyReportTab(
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    shifts = shifts,
                    dayApprovals = dayApprovals,
                    onToggleApproval = { date, approved ->
                        viewModel.setDayApproval(date, approved, currentUser)
                    },
                    onExportPdf = { date, dayShifts, p, a, c, e, approved ->
                        PdfReportGenerator.generateDailyReportPdf(context, date, dayShifts, p, a, c, e, approved)
                    }
                )
                1 -> WeeklyReportTab(
                    startDate = weeklyStartDate,
                    onDateChange = { weeklyStartDate = it },
                    shifts = shifts,
                    onExportPdf = { start, weekShifts, summary ->
                        PdfReportGenerator.generateWeeklyReportPdf(context, start, weekShifts, summary)
                    }
                )
                2 -> MonthlyReportTab(
                    yearMonth = monthlyYearMonth,
                    onYearMonthChange = { monthlyYearMonth = it },
                    shifts = shifts,
                    onExportPdf = { ym, monthShifts, summary ->
                        PdfReportGenerator.generateMonthlyReportPdf(context, ym, monthShifts, summary)
                    }
                )
            }
        }
    }
}

@Composable
fun DailyReportTab(
    selectedDate: String,
    onDateChange: (String) -> Unit,
    shifts: List<Shift>,
    dayApprovals: List<DayApproval>,
    onToggleApproval: (String, Boolean) -> Unit,
    onExportPdf: (String, List<Shift>, Int, Int, Int, Int, Boolean) -> Unit
) {
    val dayShifts = shifts.filter { it.date == selectedDate }
    val approvalRecord = dayApprovals.find { it.id == selectedDate }
    val isApproved = approvalRecord?.approved == true

    val presentCount = dayShifts.count { it.status == "حاضر" }
    val coverageCount = dayShifts.count { it.status == "تغطية" }
    val excusedCount = dayShifts.count { it.status == "مستأذن" }
    val absentCount = if (isApproved) dayShifts.count { it.status == "غائب" } else 0

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = selectedDate,
            onValueChange = onDateChange,
            label = { Text("اختر التاريخ (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "حالة اليوم:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isApproved) "معتمد ومنتهي" else "غير معتمد (قيد المراجعة)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isApproved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = { onToggleApproval(selectedDate, !isApproved) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isApproved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (isApproved) Icons.Default.Close else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isApproved) "إلغاء الاعتماد" else "اعتماد اليوم",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }

                if (!isApproved) {
                    Text(
                        text = "ملاحظة: لا يُحتسب الغياب تلقائياً إلا بعد اعتماد اكتمال سجلات اليوم رسمياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox("حاضر", presentCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatBox("غائب", absentCount.toString(), Color(0xFFEF4444), Modifier.weight(1f))
                    StatBox("تغطية", coverageCount.toString(), Color(0xFF3B82F6), Modifier.weight(1f))
                    StatBox("استئذان", excusedCount.toString(), Color(0xFFF59E0B), Modifier.weight(1f))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "قائمة السجلات والحضور المعتمد:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { onExportPdf(selectedDate, dayShifts, presentCount, absentCount, coverageCount, excusedCount, isApproved) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تصدير التقرير PDF",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondary,
                    maxLines = 1
                )
            }
        }

        if (dayShifts.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("لا توجد سجلات مسجلة لهذا التاريخ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dayShifts, key = { it.id }) { shift ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = shift.memberName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(text = "الفترة: ${shift.period} | المكان: ${shift.serviceLocation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(status = shift.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyReportTab(
    startDate: String,
    onDateChange: (String) -> Unit,
    shifts: List<Shift>,
    onExportPdf: (String, List<Shift>, String) -> Unit
) {
    val weekShifts = shifts.take(30)
    val uniquePeople = weekShifts.map { it.memberId }.distinct().size
    val summary = "إجمالي السجلات: ${weekShifts.size} | الأشخاص الفريدون: $uniquePeople"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = startDate,
            onValueChange = onDateChange,
            label = { Text("تاريخ بداية الأسبوع (السبت)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "الملخص الأسبوعي", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "سجلات الأسبوع (7 أيام متتالية):", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Button(
                onClick = { onExportPdf(startDate, weekShifts, summary) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير التقرير الأسبوعي PDF")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weekShifts, key = { it.id }) { shift ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "${shift.date} - ${shift.memberName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "المكان: ${shift.serviceLocation} | الفترة: ${shift.period}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusBadge(status = shift.status)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyReportTab(
    yearMonth: String,
    onYearMonthChange: (String) -> Unit,
    shifts: List<Shift>,
    onExportPdf: (String, List<Shift>, String) -> Unit
) {
    val monthShifts = shifts.filter { it.date.startsWith(yearMonth) }
    val uniquePeople = monthShifts.map { it.memberId }.distinct().size
    val summary = "إجمالي السجلات الشهرية: ${monthShifts.size} | الأشخاص الفريدون: $uniquePeople"

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = yearMonth,
            onValueChange = onYearMonthChange,
            label = { Text("الشهر والسنة (YYYY-MM)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "الملخص الشهري", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "سجلات الشهر:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Button(
                onClick = { onExportPdf(yearMonth, monthShifts, summary) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير التقرير الشهري PDF")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(monthShifts, key = { it.id }) { shift ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "${shift.date} - ${shift.memberName}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(text = "المكان: ${shift.serviceLocation} | الفترة: ${shift.period}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusBadge(status = shift.status)
                    }
                }
            }
        }
    }
}
