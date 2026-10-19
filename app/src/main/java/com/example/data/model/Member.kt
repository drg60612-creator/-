package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Member(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val role: String = "مناوب", // مناوب أو مشرف
    val defaultShiftDay: String = "السبت", // يوم المناوبة الأساسي
    val badge: String = "بدون علامة", // بيضاء، حمراء، أو بدون علامة
    val supervisorName: String = "", // المشرف عليه
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "name" to name,
            "role" to role,
            "defaultShiftDay" to defaultShiftDay,
            "badge" to badge,
            "supervisorName" to supervisorName,
            "archived" to archived,
            "createdAt" to (createdAt ?: Timestamp.now()),
            "updatedAt" to updatedAt
        ).filterValues { it != null }
    }
}

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val action: String = "",
    val details: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "action" to action,
            "details" to details,
            "timestamp" to (timestamp ?: Timestamp.now())
        ).filterValues { it != null }
    }
}
