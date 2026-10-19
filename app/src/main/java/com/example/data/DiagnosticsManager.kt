package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val id: String = System.currentTimeMillis().toString(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(Date()),
    val operation: String,
    val isSuccess: Boolean,
    val message: String,
    val details: String? = null
)

data class DiagnosticsStatus(
    val isNetworkConnected: Boolean = true,
    val isUserSignedIn: Boolean = false,
    val userEmail: String = "غير مسجل الدخول",
    val userId: String = "غير متوفر",
    val projectId: String = "",
    val databaseId: String = "",
    val lastPingResult: String? = null
)

object DiagnosticsManager {
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    private val _status = MutableStateFlow(DiagnosticsStatus())
    val status: StateFlow<DiagnosticsStatus> = _status.asStateFlow()

    fun updateStatus(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val hasNet = cm?.activeNetwork?.let { nw ->
            cm.getNetworkCapabilities(nw)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: true

        val projId = try {
            val resId = context.resources.getIdentifier("project_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else "gen-lang-client-0446403116"
        } catch (e: Exception) {
            "gen-lang-client-0446403116"
        }

        val dbId = try {
            context.getString(R.string.firestore_database_id)
        } catch (e: Exception) {
            "غير محدد"
        }

        _status.value = _status.value.copy(
            isNetworkConnected = hasNet,
            isUserSignedIn = user != null,
            userEmail = user?.email ?: if (user != null) "مستخدم معتمد (${user.uid.take(6)}...)" else "غير مسجل الدخول",
            userId = user?.uid ?: "غير متوفر",
            projectId = projId,
            databaseId = dbId
        )
    }

    fun setPingResult(result: String) {
        _status.value = _status.value.copy(lastPingResult = result)
    }

    fun logSuccess(operation: String, message: String, details: String? = null) {
        val entry = DiagnosticLogEntry(
            operation = operation,
            isSuccess = true,
            message = message,
            details = details
        )
        addEntry(entry)
    }

    fun logError(operation: String, error: Throwable, details: String? = null) {
        val errMsg = error.localizedMessage ?: error.message ?: error.javaClass.simpleName
        val entry = DiagnosticLogEntry(
            operation = operation,
            isSuccess = false,
            message = errMsg,
            details = details ?: error.stackTraceToString().take(300)
        )
        addEntry(entry)
    }

    private fun addEntry(entry: DiagnosticLogEntry) {
        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
