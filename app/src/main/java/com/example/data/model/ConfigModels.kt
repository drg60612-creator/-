package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ServiceLocation(
    @DocumentId val id: String = "",
    val name: String = "",
    val active: Boolean = true,
    val isDeletable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "active" to active,
            "isDeletable" to isDeletable,
            "createdAt" to (createdAt ?: Timestamp.now())
        ).filterValues { it != null }
    }
}

@IgnoreExtraProperties
data class ShiftPeriod(
    @DocumentId val id: String = "",
    val name: String = "",
    val active: Boolean = true,
    val isDeletable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "active" to active,
            "isDeletable" to isDeletable,
            "createdAt" to (createdAt ?: Timestamp.now())
        ).filterValues { it != null }
    }
}
