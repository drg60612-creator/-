package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.DiagnosticsManager
import com.example.data.model.AuditLog
import com.example.data.model.DayApproval
import com.example.data.model.Member
import com.example.data.model.ServiceLocation
import com.example.data.model.Shift
import com.example.data.model.ShiftPeriod
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MemberRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً للوصول إلى قاعدة البيانات السحابية.")
    }

    private fun requireUserEmail(): String {
        return auth.currentUser?.email ?: auth.currentUser?.uid ?: "مشرف معتمد"
    }

    val defaultLocations = listOf(
        "م. رجال",
        "م. نساء",
        "trauma",
        "CPR",
        "CPR جديد",
        "عمليات-قسم",
        "دورة أو تدريب"
    )

    val defaultPeriods = listOf(
        "صباحية",
        "مسائية",
        "ليلية"
    )

    suspend fun pingFirestoreConnection(): Result<String> {
        return try {
            val startTime = System.currentTimeMillis()
            val teamDoc = db.collection("team").document("medical_support").get().await()
            val duration = System.currentTimeMillis() - startTime
            val msg = "الاتصال سليم وقاعدة البيانات تستجيب (${duration}ms) - المستند: ${if (teamDoc.exists()) "موجود" else "جديد"}"
            DiagnosticsManager.logSuccess("فحص الاتصال", msg)
            DiagnosticsManager.setPingResult(msg)
            Result.success(msg)
        } catch (e: Exception) {
            DiagnosticsManager.logError("فحص الاتصال", e, "فشل الاتصال بقاعدة بيانات Firestore")
            DiagnosticsManager.setPingResult("فشل الاتصال: ${e.localizedMessage ?: e.message}")
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultConfigs() {
        try {
            val locationsRef = db.collection("team").document("medical_support").collection("serviceLocations")
            val locSnapshot = locationsRef.get().await()
            if (locSnapshot.isEmpty) {
                val batch = db.batch()
                defaultLocations.forEach { name ->
                    val doc = locationsRef.document()
                    val loc = ServiceLocation(id = doc.id, name = name, active = true, isDeletable = true)
                    batch.set(doc, loc.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp()))
                }
                batch.commit().await()
                DiagnosticsManager.logSuccess("تهيئة الأماكن", "تمت تهيئة أماكن الخدمة الافتراضية بنجاح")
            }

            val periodsRef = db.collection("team").document("medical_support").collection("periods")
            val perSnapshot = periodsRef.get().await()
            if (perSnapshot.isEmpty) {
                val batch = db.batch()
                defaultPeriods.forEach { name ->
                    val doc = periodsRef.document()
                    val per = ShiftPeriod(id = doc.id, name = name, active = true, isDeletable = true)
                    batch.set(doc, per.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp()))
                }
                batch.commit().await()
                DiagnosticsManager.logSuccess("تهيئة الفترات", "تمت تهيئة فترات المناوبات الافتراضية بنجاح")
            }
        } catch (e: Exception) {
            Log.e("MemberRepository", "Error ensuring default configs", e)
            DiagnosticsManager.logError("تهيئة الإعدادات", e, "تعذر إنشاء السجلات الافتراضية في Firestore")
        }
    }

    fun observeServiceLocations(): Flow<List<ServiceLocation>> = flow {
        val locationsRef = db.collection("team").document("medical_support").collection("serviceLocations")
        emitAll(
            locationsRef.snapshots()
                .map { snapshot ->
                    if (snapshot.isEmpty) {
                        defaultLocations.map { ServiceLocation(id = it, name = it, active = true) }
                    } else {
                        snapshot.documents.mapNotNull { doc ->
                            doc.toObject(ServiceLocation::class.java)?.copy(id = doc.id)
                        }
                    }
                }
                .catch { e ->
                    Log.e("MemberRepository", "Error observing service locations", e)
                    DiagnosticsManager.logError("مراقبة أماكن الخدمة", e)
                    emit(defaultLocations.map { ServiceLocation(id = it, name = it, active = true) })
                }
        )
    }

    suspend fun addServiceLocation(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "اسم مكان الخدمة لا يمكن أن يكون فارغاً" }
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val locationsRef = db.collection("team").document("medical_support").collection("serviceLocations")
            val docRef = locationsRef.document()
            val loc = ServiceLocation(id = docRef.id, name = trimmed, active = true)
            docRef.set(loc.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp())).await()
            logAudit(uid, "إضافة مكان خدمة", "تمت إضافة مكان الخدمة ($trimmed) بواسطة $email")
            DiagnosticsManager.logSuccess("إضافة مكان خدمة", "تم حفظ مكان الخدمة: $trimmed بنجاح في Firestore")
        } catch (e: Exception) {
            DiagnosticsManager.logError("إضافة مكان خدمة", e, "فشل حفظ مكان الخدمة: $trimmed")
            throw e
        }
    }

    suspend fun updateServiceLocation(id: String, oldName: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "الاسم الجديد لمكان الخدمة لا يمكن أن يكون فارغاً" }
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val docRef = db.collection("team").document("medical_support").collection("serviceLocations").document(id)
            docRef.set(
                mapOf(
                    "name" to trimmed,
                    "active" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            logAudit(uid, "تعديل مكان خدمة", "تم تعديل مكان الخدمة من ($oldName) إلى ($trimmed) بواسطة $email")
            DiagnosticsManager.logSuccess("تعديل مكان خدمة", "تم تحديث مكان الخدمة: من $oldName إلى $trimmed")
        } catch (e: Exception) {
            DiagnosticsManager.logError("تعديل مكان خدمة", e, "فشل تعديل مكان الخدمة ID: $id")
            throw e
        }
    }

    suspend fun deleteServiceLocation(id: String, name: String, isUsedInShifts: Boolean) {
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val docRef = db.collection("team").document("medical_support").collection("serviceLocations").document(id)
            if (isUsedInShifts) {
                docRef.set(
                    mapOf(
                        "active" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
                logAudit(uid, "تعطيل مكان خدمة", "تم تعطيل مكان الخدمة ($name) لحماية السجلات التاريخية بواسطة $email")
                DiagnosticsManager.logSuccess("تعطيل مكان خدمة", "تم تعطيل مكان الخدمة: $name (مرتبط بسجلات)")
            } else {
                docRef.delete().await()
                logAudit(uid, "حذف مكان خدمة", "تم حذف مكان الخدمة ($name) نهائياً بواسطة $email")
                DiagnosticsManager.logSuccess("حذف مكان خدمة", "تم حذف مكان الخدمة: $name نهائياً من Firestore")
            }
        } catch (e: Exception) {
            DiagnosticsManager.logError("حذف مكان خدمة", e, "فشل حذف/تعطيل مكان الخدمة: $name")
            throw e
        }
    }

    fun observePeriods(): Flow<List<ShiftPeriod>> = flow {
        val periodsRef = db.collection("team").document("medical_support").collection("periods")
        emitAll(
            periodsRef.snapshots()
                .map { snapshot ->
                    if (snapshot.isEmpty) {
                        defaultPeriods.map { ShiftPeriod(id = it, name = it, active = true) }
                    } else {
                        snapshot.documents.mapNotNull { doc ->
                            doc.toObject(ShiftPeriod::class.java)?.copy(id = doc.id)
                        }
                    }
                }
                .catch { e ->
                    Log.e("MemberRepository", "Error observing periods", e)
                    DiagnosticsManager.logError("مراقبة الفترات", e)
                    emit(defaultPeriods.map { ShiftPeriod(id = it, name = it, active = true) })
                }
        )
    }

    suspend fun addPeriod(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "اسم الفترة لا يمكن أن يكون فارغاً" }
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val periodsRef = db.collection("team").document("medical_support").collection("periods")
            val docRef = periodsRef.document()
            val period = ShiftPeriod(id = docRef.id, name = trimmed, active = true)
            docRef.set(period.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp())).await()
            logAudit(uid, "إضافة فترة", "تمت إضافة فترة المناوبة ($trimmed) بواسطة $email")
            DiagnosticsManager.logSuccess("إضافة فترة", "تم حفظ فترة المناوبة: $trimmed بنجاح في Firestore")
        } catch (e: Exception) {
            DiagnosticsManager.logError("إضافة فترة", e, "فشل حفظ فترة المناوبة: $trimmed")
            throw e
        }
    }

    suspend fun updatePeriod(id: String, oldName: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "الاسم الجديد للفترة لا يمكن أن يكون فارغاً" }
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val docRef = db.collection("team").document("medical_support").collection("periods").document(id)
            docRef.set(
                mapOf(
                    "name" to trimmed,
                    "active" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            logAudit(uid, "تعديل فترة", "تم تعديل فترة المناوبة من ($oldName) إلى ($trimmed) بواسطة $email")
            DiagnosticsManager.logSuccess("تعديل فترة", "تم تحديث فترة المناوبة: من $oldName إلى $trimmed")
        } catch (e: Exception) {
            DiagnosticsManager.logError("تعديل فترة", e, "فشل تعديل فترة المناوبة ID: $id")
            throw e
        }
    }

    suspend fun deletePeriod(id: String, name: String, isUsedInShifts: Boolean) {
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val docRef = db.collection("team").document("medical_support").collection("periods").document(id)
            if (isUsedInShifts) {
                docRef.set(
                    mapOf(
                        "active" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
                logAudit(uid, "تعطيل فترة مناوبة", "تم تعطيل الفترة ($name) لحماية السجلات التاريخية بواسطة $email")
                DiagnosticsManager.logSuccess("تعطيل فترة", "تم تعطيل فترة المناوبة: $name (مرتبطة بسجلات)")
            } else {
                docRef.delete().await()
                logAudit(uid, "حذف فترة مناوبة", "تم حذف الفترة ($name) نهائياً بواسطة $email")
                DiagnosticsManager.logSuccess("حذف فترة", "تم حذف فترة المناوبة: $name نهائياً من Firestore")
            }
        } catch (e: Exception) {
            DiagnosticsManager.logError("حذف فترة", e, "فشل حذف/تعطيل فترة المناوبة: $name")
            throw e
        }
    }

    fun observeMembers(): Flow<List<Member>> = flow {
        val membersRef = db.collection("team").document("medical_support").collection("members")
        emitAll(
            membersRef.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Member::class.java)?.copy(id = doc.id)
                    }
                }
                .catch { e ->
                    Log.e("MemberRepository", "Error observing members", e)
                    DiagnosticsManager.logError("مراقبة الأعضاء", e, "فشل استلام تحديثات الأعضاء من Firestore")
                    throw e
                }
        )
    }

    fun observeShifts(): Flow<List<Shift>> = flow {
        val shiftsRef = db.collection("team").document("medical_support").collection("shifts")
        emitAll(
            shiftsRef.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Shift::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.date }
                }
                .catch { e ->
                    Log.e("MemberRepository", "Error observing shifts", e)
                    DiagnosticsManager.logError("مراقبة المناوبات", e)
                    throw e
                }
        )
    }

    fun observeDayApprovals(): Flow<List<DayApproval>> = flow {
        val approvalsRef = db.collection("team").document("medical_support").collection("dayApprovals")
        emitAll(
            approvalsRef.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(DayApproval::class.java)?.copy(id = doc.id)
                    }
                }
                .catch { e ->
                    Log.e("MemberRepository", "Error observing day approvals", e)
                    DiagnosticsManager.logError("مراقبة اعتمادات الأيام", e)
                    throw e
                }
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val logsRef = db.collection("team").document("medical_support").collection("auditLogs")
        emitAll(
            logsRef.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(AuditLog::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.timestamp }
                }
                .catch { e ->
                    Log.e("MemberRepository", "Error observing audit logs", e)
                    DiagnosticsManager.logError("مراقبة سجل التدقيق", e)
                    emit(emptyList())
                }
        )
    }

    suspend fun addMember(member: Member, actionDetails: String) {
        val uid = requireUserId()
        val email = requireUserEmail()
        require(member.name.isNotBlank()) { "اسم المتطوع مطلوب" }
        try {
            val membersRef = db.collection("team").document("medical_support").collection("members")
            val docRef = if (member.id.isNotBlank()) membersRef.document(member.id) else membersRef.document()
            val memberId = docRef.id

            val memberData = member.copy(id = memberId, userId = uid, createdAt = null, updatedAt = null).toMap() + mapOf(
                "createdAt" to FieldValue.serverTimestamp()
            )

            docRef.set(memberData).await()
            logAudit(uid, "إضافة عضو", "تم إضافة العضو: ${member.name} بواسطة $email ($actionDetails)")
            DiagnosticsManager.logSuccess("إضافة عضو", "تم حفظ العضو: ${member.name} بنجاح في Firestore")
        } catch (e: Exception) {
            DiagnosticsManager.logError("إضافة عضو", e, "فشل حفظ العضو: ${member.name}")
            throw e
        }
    }

    suspend fun updateMember(member: Member, actionDetails: String) {
        val uid = requireUserId()
        val email = requireUserEmail()
        require(member.id.isNotBlank()) { "معرّف العضو مطلوب للتعديل" }
        require(member.name.isNotBlank()) { "اسم المتطوع مطلوب" }
        try {
            val docRef = db.collection("team").document("medical_support").collection("members").document(member.id)
            val memberData = member.copy(userId = uid, updatedAt = null).toMap() + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(memberData, SetOptions.merge()).await()
            logAudit(uid, "تعديل عضو", "تم تحديث بيانات العضو: ${member.name} بواسطة $email ($actionDetails)")
            DiagnosticsManager.logSuccess("تعديل عضو", "تم تحديث بيانات العضو: ${member.name}")
        } catch (e: Exception) {
            DiagnosticsManager.logError("تعديل عضو", e, "فشل تعديل العضو: ${member.name}")
            throw e
        }
    }

    suspend fun archiveMember(memberId: String, memberName: String, archiveState: Boolean) {
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val docRef = db.collection("team").document("medical_support").collection("members").document(memberId)
            docRef.set(
                mapOf(
                    "archived" to archiveState,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            val actionName = if (archiveState) "أرشفة عضو" else "استعادة عضو"
            logAudit(uid, actionName, "تم تغيير حالة الأرشفة للعضو $memberName إلى: $archiveState بواسطة $email")
            DiagnosticsManager.logSuccess(actionName, "تم تحديث حالة الأرشفة للعضو: $memberName")
        } catch (e: Exception) {
            DiagnosticsManager.logError("أرشفة عضو", e, "فشل أرشفة/استعادة العضو: $memberName")
            throw e
        }
    }

    suspend fun importMembers(members: List<Member>) {
        val uid = requireUserId()
        val email = requireUserEmail()
        require(members.isNotEmpty()) { "قائمة الأعضاء للاستيراد فارغة" }
        try {
            val batch = db.batch()
            val membersRef = db.collection("team").document("medical_support").collection("members")

            for (member in members) {
                val docRef = membersRef.document()
                val memberData = member.copy(id = docRef.id, userId = uid, createdAt = null).toMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, memberData)
            }
            batch.commit().await()
            logAudit(uid, "استيراد أعضاء", "تم استيراد ${members.size} عضو بنجاح بواسطة $email.")
            DiagnosticsManager.logSuccess("استيراد أعضاء", "تم استيراد ${members.size} عضو بنجاح في Firestore")
        } catch (e: Exception) {
            DiagnosticsManager.logError("استيراد أعضاء", e, "فشل استيراد الأعضاء إلى Firestore")
            throw e
        }
    }

    suspend fun saveShift(shift: Shift) {
        val uid = requireUserId()
        val email = requireUserEmail()
        require(shift.memberId.isNotBlank()) { "يجب اختيار العضو للمناوبة" }
        require(shift.date.isNotBlank()) { "تاريخ المناوبة مطلوب" }
        try {
            val shiftsRef = db.collection("team").document("medical_support").collection("shifts")
            val docRef = if (shift.id.isNotBlank()) shiftsRef.document(shift.id) else shiftsRef.document()
            val shiftId = docRef.id

            val finalStatus = if (shift.manualOverride) {
                shift.status
            } else {
                if (shift.actualShiftDay == shift.defaultShiftDay) "حاضر" else "تغطية"
            }

            val shiftData = shift.copy(
                id = shiftId,
                userId = uid,
                modifiedBy = if (shift.manualOverride) email else shift.modifiedBy,
                status = finalStatus,
                createdAt = null,
                updatedAt = null
            ).toMap() + mapOf(
                "createdAt" to FieldValue.serverTimestamp()
            )

            docRef.set(shiftData, SetOptions.merge()).await()
            logAudit(uid, "تسجيل مناوبة", "تم تسجيل مناوبة للعضو ${shift.memberName} بتاريخ ${shift.date} بالحالة: $finalStatus بواسطة $email")
            DiagnosticsManager.logSuccess("تسجيل مناوبة", "تم تسجيل مناوبة العضو: ${shift.memberName} بتاريخ ${shift.date}")
        } catch (e: Exception) {
            DiagnosticsManager.logError("تسجيل مناوبة", e, "فشل تسجيل مناوبة العضو: ${shift.memberName}")
            throw e
        }
    }

    suspend fun deleteShift(shiftId: String, memberName: String, date: String) {
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val docRef = db.collection("team").document("medical_support").collection("shifts").document(shiftId)
            docRef.delete().await()
            logAudit(uid, "حذف مناوبة", "تم حذف مناوبة العضو $memberName بتاريخ $date بواسطة $email")
            DiagnosticsManager.logSuccess("حذف مناوبة", "تم حذف مناوبة العضو: $memberName بتاريخ $date")
        } catch (e: Exception) {
            DiagnosticsManager.logError("حذف مناوبة", e, "فشل حذف مناوبة العضو: $memberName")
            throw e
        }
    }

    suspend fun setDayApproval(date: String, approved: Boolean, userName: String) {
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val approvalRef = db.collection("team").document("medical_support").collection("dayApprovals").document(date)
            val data = DayApproval(
                id = date,
                userId = uid,
                date = date,
                approved = approved,
                approvedBy = email,
                updatedAt = null
            ).toMap() + mapOf("updatedAt" to FieldValue.serverTimestamp())

            approvalRef.set(data, SetOptions.merge()).await()
            logAudit(uid, "اعتماد يوم", "تم تغيير حالة اعتماد اليوم $date إلى: $approved بواسطة $email")
            DiagnosticsManager.logSuccess("اعتماد يوم", "تم تغيير حالة اعتماد اليوم $date إلى: ${if (approved) "معتمد" else "غير معتمد"}")
        } catch (e: Exception) {
            DiagnosticsManager.logError("اعتماد يوم", e, "فشل اعتماد اليوم: $date")
            throw e
        }
    }

    private suspend fun logAudit(uid: String, action: String, details: String) {
        try {
            val logsRef = db.collection("team").document("medical_support").collection("auditLogs")
            val docRef = logsRef.document()
            val logData = AuditLog(
                id = docRef.id,
                userId = uid,
                action = action,
                details = details,
                timestamp = null
            ).toMap() + mapOf("timestamp" to FieldValue.serverTimestamp())
            docRef.set(logData).await()
        } catch (e: Exception) {
            Log.e("MemberRepository", "Failed to write audit log", e)
        }
    }
}
