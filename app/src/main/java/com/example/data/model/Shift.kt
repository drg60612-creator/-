package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Shift(
    @DocumentId val id: String = "",
    val userId: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val defaultShiftDay: String = "",
    val actualShiftDay: String = "",
    val date: String = "", // التاريخ بصيغة يي-مم-يوم أو تاريخ واضح
    val period: String = "صباحية", // صباحية، مسائية، ليلية
    val serviceLocation: String = "م. رجال", // م. رجال، م. نساء، truma، CPR، CPR جديد، عمليات-قسم، دورة أو تدريب
    val status: String = "حاضر", // حاضر، غائب، تغطية، مستأذن
    val manualOverride: Boolean = false,
    val overrideReason: String = "",
    val modifiedBy: String = "",
    val approved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "memberId" to memberId,
            "memberName" to memberName,
            "defaultShiftDay" to defaultShiftDay,
            "actualShiftDay" to actualShiftDay,
            "date" to date,
            "period" to period,
            "serviceLocation" to serviceLocation,
            "status" to status,
            "manualOverride" to manualOverride,
            "overrideReason" to overrideReason,
            "modifiedBy" to modifiedBy,
            "approved" to approved,
            "createdAt" to (createdAt ?: Timestamp.now()),
            "updatedAt" to updatedAt
        ).filterValues { it != null }
    }
}

@IgnoreExtraProperties
data class DayApproval(
    @DocumentId val id: String = "",
    val userId: String = "",
    val date: String = "",
    val approved: Boolean = false,
    val approvedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "date" to date,
            "approved" to approved,
            "approvedBy" to approvedBy,
            "updatedAt" to (updatedAt ?: Timestamp.now())
        ).filterValues { it != null }
    }
}
