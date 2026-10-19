package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DayApproval
import com.example.data.model.Shift
import com.example.utils.PdfReportGenerator
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyReportScreen(
    viewModel: MembersViewModel,
    shifts: List<Shift>,
    dayApprovals: List<DayApproval>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()) }
    var selectedDate by remember { mutableStateOf(todayDate) }
    val currentUser = FirebaseAuth.getInstance().currentUser?.email ?: "مشرف النظام"

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تقرير الحضور والمناوبات",
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            DailyReportTab(
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
        }
    }
}
