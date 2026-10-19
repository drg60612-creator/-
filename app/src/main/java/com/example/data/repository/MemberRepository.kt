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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MemberRepository(val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        resolveFirestore(context)
    )

    companion object {
        private const val TAG = "MemberRepository"

        fun resolveFirestore(context: Context): FirebaseFirestore {
            val dbId = try {
                context.applicationContext.getString(R.string.firestore_database_id)
            } catch (e: Exception) {
                ""
            }
            return if (dbId.isNotBlank()) {
                Log.d(TAG, "Connecting to provisioned Firestore database: $dbId")
                FirebaseFirestore.getInstance(dbId)
            } else {
                Log.d(TAG, "Connecting to default Firestore database")
                FirebaseFirestore.getInstance()
            }
        }
    }

    private val auth = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: "anonymous_supervisor"
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
            val teamQuery = db.collection("team").document("medical_support").collection("members").limit(1).get().await()
            val rootQuery = db.collection("members").limit(1).get().await()
            val duration = System.currentTimeMillis() - startTime
            val count = teamQuery.size() + rootQuery.size()
            val msg = "الاتصال سليم بقاعدة البيانات السحابية ($duration ms) - تم التحقق من مسار الفريق والمسار العام ($count سجل)"
            DiagnosticsManager.logSuccess("فحص الاتصال", msg)
            DiagnosticsManager.setPingResult(msg)
            Result.success(msg)
        } catch (e: Exception) {
            val errMsg = "فشل الاتصال بقاعدة بيانات Firestore: ${e.localizedMessage ?: e.message}"
            DiagnosticsManager.logError("فحص الاتصال", e, errMsg)
            DiagnosticsManager.setPingResult(errMsg)
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultConfigs() {
        try {
            // Check both team path and root path
            val rootLocRef = db.collection("serviceLocations")
            val teamLocRef = db.collection("team").document("medical_support").collection("serviceLocations")
            val locSnapshot = rootLocRef.get().await()
            if (locSnapshot.isEmpty) {
                val batch = db.batch()
                defaultLocations.forEach { name ->
                    val rootDoc = rootLocRef.document()
                    val loc = ServiceLocation(id = rootDoc.id, name = name, active = true, isDeletable = true)
                    val data = loc.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp())
                    batch.set(rootDoc, data)
                    batch.set(teamLocRef.document(rootDoc.id), data)
                }
                batch.commit().await()
                DiagnosticsManager.logSuccess("تهيئة الأماكن", "تمت تهيئة أماكن الخدمة الافتراضية بنجاح")
            }

            val rootPerRef = db.collection("periods")
            val teamPerRef = db.collection("team").document("medical_support").collection("periods")
            val perSnapshot = rootPerRef.get().await()
            if (perSnapshot.isEmpty) {
                val batch = db.batch()
                defaultPeriods.forEach { name ->
                    val rootDoc = rootPerRef.document()
                    val per = ShiftPeriod(id = rootDoc.id, name = name, active = true, isDeletable = true)
                    val data = per.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp())
                    batch.set(rootDoc, data)
                    batch.set(teamPerRef.document(rootDoc.id), data)
                }
                batch.commit().await()
                DiagnosticsManager.logSuccess("تهيئة الفترات", "تمت تهيئة فترات المناوبات الافتراضية بنجاح")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring default configs", e)
            DiagnosticsManager.logError("تهيئة الإعدادات", e, "تعذر إنشاء السجلات الافتراضية في Firestore")
        }
    }

    // --- Service Locations ---

    fun observeServiceLocations(): Flow<List<ServiceLocation>> = flow {
        val rootFlow = db.collection("serviceLocations").snapshots().map { snap ->
            snap.documents.mapNotNull { parseServiceLocation(it) }
        }.catch { emit(emptyList()) }

        val teamFlow = db.collection("team").document("medical_support").collection("serviceLocations").snapshots().map { snap ->
            snap.documents.mapNotNull { parseServiceLocation(it) }
        }.catch { emit(emptyList()) }

        emitAll(
            combine(teamFlow, rootFlow) { teamList, rootList ->
                val combined = (teamList + rootList).distinctBy { it.name.trim() }
                if (combined.isEmpty()) {
                    defaultLocations.map { ServiceLocation(id = it, name = it, active = true) }
                } else {
                    combined
                }
            }.catch { e ->
                Log.e(TAG, "Error observing service locations", e)
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
            val rootDoc = db.collection("serviceLocations").document()
            val teamDoc = db.collection("team").document("medical_support").collection("serviceLocations").document(rootDoc.id)
            val loc = ServiceLocation(id = rootDoc.id, name = trimmed, active = true)
            val data = loc.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp())

            val batch = db.batch()
            batch.set(rootDoc, data)
            batch.set(teamDoc, data)
            batch.commit().await()

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
            val rootDoc = db.collection("serviceLocations").document(id)
            val teamDoc = db.collection("team").document("medical_support").collection("serviceLocations").document(id)
            val updateData = mapOf(
                "name" to trimmed,
                "active" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(rootDoc, updateData, SetOptions.merge())
            batch.set(teamDoc, updateData, SetOptions.merge())
            batch.commit().await()

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
            val rootDoc = db.collection("serviceLocations").document(id)
            val teamDoc = db.collection("team").document("medical_support").collection("serviceLocations").document(id)
            val batch = db.batch()

            if (isUsedInShifts) {
                val updateData = mapOf(
                    "active" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(rootDoc, updateData, SetOptions.merge())
                batch.set(teamDoc, updateData, SetOptions.merge())
                batch.commit().await()
                logAudit(uid, "تعطيل مكان خدمة", "تم تعطيل مكان الخدمة ($name) لحماية السجلات التاريخية بواسطة $email")
                DiagnosticsManager.logSuccess("تعطيل مكان خدمة", "تم تعطيل مكان الخدمة: $name (مرتبط بسجلات)")
            } else {
                batch.delete(rootDoc)
                batch.delete(teamDoc)
                batch.commit().await()
                logAudit(uid, "حذف مكان خدمة", "تم حذف مكان الخدمة ($name) نهائياً بواسطة $email")
                DiagnosticsManager.logSuccess("حذف مكان خدمة", "تم حذف مكان الخدمة: $name نهائياً من Firestore")
            }
        } catch (e: Exception) {
            DiagnosticsManager.logError("حذف مكان خدمة", e, "فشل حذف/تعطيل مكان الخدمة: $name")
            throw e
        }
    }

    // --- Periods ---

    fun observePeriods(): Flow<List<ShiftPeriod>> = flow {
        val rootFlow = db.collection("periods").snapshots().map { snap ->
            snap.documents.mapNotNull { parsePeriod(it) }
        }.catch { emit(emptyList()) }

        val teamFlow = db.collection("team").document("medical_support").collection("periods").snapshots().map { snap ->
            snap.documents.mapNotNull { parsePeriod(it) }
        }.catch { emit(emptyList()) }

        emitAll(
            combine(teamFlow, rootFlow) { teamList, rootList ->
                val combined = (teamList + rootList).distinctBy { it.name.trim() }
                if (combined.isEmpty()) {
                    defaultPeriods.map { ShiftPeriod(id = it, name = it, active = true) }
                } else {
                    combined
                }
            }.catch { e ->
                Log.e(TAG, "Error observing periods", e)
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
            val rootDoc = db.collection("periods").document()
            val teamDoc = db.collection("team").document("medical_support").collection("periods").document(rootDoc.id)
            val period = ShiftPeriod(id = rootDoc.id, name = trimmed, active = true)
            val data = period.toMap() + mapOf("createdAt" to FieldValue.serverTimestamp())

            val batch = db.batch()
            batch.set(rootDoc, data)
            batch.set(teamDoc, data)
            batch.commit().await()

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
            val rootDoc = db.collection("periods").document(id)
            val teamDoc = db.collection("team").document("medical_support").collection("periods").document(id)
            val updateData = mapOf(
                "name" to trimmed,
                "active" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(rootDoc, updateData, SetOptions.merge())
            batch.set(teamDoc, updateData, SetOptions.merge())
            batch.commit().await()

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
            val rootDoc = db.collection("periods").document(id)
            val teamDoc = db.collection("team").document("medical_support").collection("periods").document(id)
            val batch = db.batch()

            if (isUsedInShifts) {
                val updateData = mapOf(
                    "active" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(rootDoc, updateData, SetOptions.merge())
                batch.set(teamDoc, updateData, SetOptions.merge())
                batch.commit().await()
                logAudit(uid, "تعطيل فترة مناوبة", "تم تعطيل الفترة ($name) لحماية السجلات التاريخية بواسطة $email")
                DiagnosticsManager.logSuccess("تعطيل فترة", "تم تعطيل فترة المناوبة: $name (مرتبطة بسجلات)")
            } else {
                batch.delete(rootDoc)
                batch.delete(teamDoc)
                batch.commit().await()
                logAudit(uid, "حذف فترة مناوبة", "تم حذف الفترة ($name) نهائياً بواسطة $email")
                DiagnosticsManager.logSuccess("حذف فترة", "تم حذف فترة المناوبة: $name نهائياً من Firestore")
            }
        } catch (e: Exception) {
            DiagnosticsManager.logError("حذف فترة", e, "فشل حذف/تعطيل فترة المناوبة: $name")
            throw e
        }
    }

    // --- Members ---

    fun observeMembers(): Flow<List<Member>> = flow {
        val rootFlow = db.collection("members").snapshots().map { snap ->
            snap.documents.mapNotNull { parseMember(it) }
        }.catch { e ->
            Log.w(TAG, "Root members flow error: ${e.message}")
            emit(emptyList())
        }

        val teamFlow = db.collection("team").document("medical_support").collection("members").snapshots().map { snap ->
            snap.documents.mapNotNull { parseMember(it) }
        }.catch { e ->
            Log.w(TAG, "Team members flow error: ${e.message}")
            emit(emptyList())
        }

        emitAll(
            combine(teamFlow, rootFlow) { teamList, rootList ->
                val combined = (teamList + rootList).distinctBy { it.id }
                Log.d(TAG, "Observed members update: ${combined.size} members (team=${teamList.size}, root=${rootList.size})")
                combined.sortedBy { it.name }
            }.catch { e ->
                Log.e(TAG, "Error observing members combined", e)
                DiagnosticsManager.logError("مراقبة الأعضاء", e, "فشل استلام تحديثات الأعضاء من Firestore")
                emit(emptyList())
            }
        )
    }

    suspend fun addMember(member: Member, actionDetails: String) {
        val uid = requireUserId()
        val email = requireUserEmail()
        require(member.name.isNotBlank()) { "اسم المتطوع مطلوب" }
        try {
            val rootRef = db.collection("members")
            val rootDoc = if (member.id.isNotBlank()) rootRef.document(member.id) else rootRef.document()
            val memberId = rootDoc.id
            val teamDoc = db.collection("team").document("medical_support").collection("members").document(memberId)

            val memberData = member.copy(id = memberId, userId = uid, createdAt = null, updatedAt = null).toMap() + mapOf(
                "createdAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(rootDoc, memberData)
            batch.set(teamDoc, memberData)
            batch.commit().await()

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
            val rootDoc = db.collection("members").document(member.id)
            val teamDoc = db.collection("team").document("medical_support").collection("members").document(member.id)
            val memberData = member.copy(userId = uid, updatedAt = null).toMap() + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(rootDoc, memberData, SetOptions.merge())
            batch.set(teamDoc, memberData, SetOptions.merge())
            batch.commit().await()

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
            val rootDoc = db.collection("members").document(memberId)
            val teamDoc = db.collection("team").document("medical_support").collection("members").document(memberId)
            val updateData = mapOf(
                "archived" to archiveState,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(rootDoc, updateData, SetOptions.merge())
            batch.set(teamDoc, updateData, SetOptions.merge())
            batch.commit().await()

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
            val rootRef = db.collection("members")
            val teamRef = db.collection("team").document("medical_support").collection("members")

            for (member in members) {
                val rootDoc = rootRef.document()
                val memberId = rootDoc.id
                val teamDoc = teamRef.document(memberId)
                val memberData = member.copy(id = memberId, userId = uid, createdAt = null).toMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(rootDoc, memberData)
                batch.set(teamDoc, memberData)
            }
            batch.commit().await()
            logAudit(uid, "استيراد أعضاء", "تم استيراد ${members.size} عضو بنجاح بواسطة $email.")
            DiagnosticsManager.logSuccess("استيراد أعضاء", "تم استيراد ${members.size} عضو بنجاح في Firestore")
        } catch (e: Exception) {
            DiagnosticsManager.logError("استيراد أعضاء", e, "فشل استيراد الأعضاء إلى Firestore")
            throw e
        }
    }

    // --- Shifts ---

    fun observeShifts(): Flow<List<Shift>> = flow {
        val rootFlow = db.collection("shifts").snapshots().map { snap ->
            snap.documents.mapNotNull { parseShift(it) }
        }.catch { e ->
            Log.w(TAG, "Root shifts flow error: ${e.message}")
            emit(emptyList())
        }

        val teamFlow = db.collection("team").document("medical_support").collection("shifts").snapshots().map { snap ->
            snap.documents.mapNotNull { parseShift(it) }
        }.catch { e ->
            Log.w(TAG, "Team shifts flow error: ${e.message}")
            emit(emptyList())
        }

        emitAll(
            combine(teamFlow, rootFlow) { teamList, rootList ->
                val combined = (teamList + rootList).distinctBy { it.id }
                combined.sortedByDescending { it.date }
            }.catch { e ->
                Log.e(TAG, "Error observing shifts combined", e)
                DiagnosticsManager.logError("مراقبة المناوبات", e)
                emit(emptyList())
            }
        )
    }

    suspend fun saveShift(shift: Shift) {
        val uid = requireUserId()
        val email = requireUserEmail()
        require(shift.memberId.isNotBlank()) { "يجب اختيار العضو للمناوبة" }
        require(shift.date.isNotBlank()) { "تاريخ المناوبة مطلوب" }
        try {
            val rootRef = db.collection("shifts")
            val rootDoc = if (shift.id.isNotBlank()) rootRef.document(shift.id) else rootRef.document()
            val shiftId = rootDoc.id
            val teamDoc = db.collection("team").document("medical_support").collection("shifts").document(shiftId)

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

            val batch = db.batch()
            batch.set(rootDoc, shiftData, SetOptions.merge())
            batch.set(teamDoc, shiftData, SetOptions.merge())
            batch.commit().await()

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
            val rootDoc = db.collection("shifts").document(shiftId)
            val teamDoc = db.collection("team").document("medical_support").collection("shifts").document(shiftId)
            val batch = db.batch()
            batch.delete(rootDoc)
            batch.delete(teamDoc)
            batch.commit().await()

            logAudit(uid, "حذف مناوبة", "تم حذف مناوبة العضو $memberName بتاريخ $date بواسطة $email")
            DiagnosticsManager.logSuccess("حذف مناوبة", "تم حذف مناوبة العضو: $memberName بتاريخ $date")
        } catch (e: Exception) {
            DiagnosticsManager.logError("حذف مناوبة", e, "فشل حذف مناوبة العضو: $memberName")
            throw e
        }
    }

    // --- Day Approvals ---

    fun observeDayApprovals(): Flow<List<DayApproval>> = flow {
        val rootFlow = db.collection("dayApprovals").snapshots().map { snap ->
            snap.documents.mapNotNull { parseDayApproval(it) }
        }.catch { e ->
            Log.w(TAG, "Root approvals flow error: ${e.message}")
            emit(emptyList())
        }

        val teamFlow = db.collection("team").document("medical_support").collection("dayApprovals").snapshots().map { snap ->
            snap.documents.mapNotNull { parseDayApproval(it) }
        }.catch { e ->
            Log.w(TAG, "Team approvals flow error: ${e.message}")
            emit(emptyList())
        }

        emitAll(
            combine(teamFlow, rootFlow) { teamList, rootList ->
                (teamList + rootList).distinctBy { it.id }
            }.catch { e ->
                Log.e(TAG, "Error observing day approvals combined", e)
                DiagnosticsManager.logError("مراقبة اعتمادات الأيام", e)
                emit(emptyList())
            }
        )
    }

    suspend fun setDayApproval(date: String, approved: Boolean, userName: String) {
        val uid = requireUserId()
        val email = requireUserEmail()
        try {
            val rootDoc = db.collection("dayApprovals").document(date)
            val teamDoc = db.collection("team").document("medical_support").collection("dayApprovals").document(date)
            val data = DayApproval(
                id = date,
                userId = uid,
                date = date,
                approved = approved,
                approvedBy = email,
                updatedAt = null
            ).toMap() + mapOf("updatedAt" to FieldValue.serverTimestamp())

            val batch = db.batch()
            batch.set(rootDoc, data, SetOptions.merge())
            batch.set(teamDoc, data, SetOptions.merge())
            batch.commit().await()

            logAudit(uid, "اعتماد يوم", "تم تغيير حالة اعتماد اليوم $date إلى: $approved بواسطة $email")
            DiagnosticsManager.logSuccess("اعتماد يوم", "تم تغيير حالة اعتماد اليوم $date إلى: ${if (approved) "معتمد" else "غير معتمد"}")
        } catch (e: Exception) {
            DiagnosticsManager.logError("اعتماد يوم", e, "فشل اعتماد اليوم: $date")
            throw e
        }
    }

    // --- Audit Logs ---

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val rootFlow = db.collection("auditLogs").snapshots().map { snap ->
            snap.documents.mapNotNull { parseAuditLog(it) }
        }.catch { emit(emptyList()) }

        val teamFlow = db.collection("team").document("medical_support").collection("auditLogs").snapshots().map { snap ->
            snap.documents.mapNotNull { parseAuditLog(it) }
        }.catch { emit(emptyList()) }

        emitAll(
            combine(teamFlow, rootFlow) { teamList, rootList ->
                (teamList + rootList).distinctBy { it.id }.sortedByDescending { it.timestamp }
            }.catch { e ->
                Log.e(TAG, "Error observing audit logs", e)
                DiagnosticsManager.logError("مراقبة سجل التدقيق", e)
                emit(emptyList())
            }
        )
    }

    private suspend fun logAudit(uid: String, action: String, details: String) {
        try {
            val rootDoc = db.collection("auditLogs").document()
            val teamDoc = db.collection("team").document("medical_support").collection("auditLogs").document(rootDoc.id)
            val logData = AuditLog(
                id = rootDoc.id,
                userId = uid,
                action = action,
                details = details,
                timestamp = null
            ).toMap() + mapOf("timestamp" to FieldValue.serverTimestamp())

            val batch = db.batch()
            batch.set(rootDoc, logData)
            batch.set(teamDoc, logData)
            batch.commit().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log", e)
        }
    }

    // --- Safe Parsers (Prevent serialization crashes on any document format) ---

    private fun parseMember(doc: DocumentSnapshot): Member? {
        return try {
            val obj = doc.toObject(Member::class.java)
            if (obj != null && obj.name.isNotBlank()) {
                obj.copy(id = doc.id)
            } else {
                fallbackParseMember(doc)
            }
        } catch (e: Exception) {
            fallbackParseMember(doc)
        }
    }

    private fun fallbackParseMember(doc: DocumentSnapshot): Member? {
        return try {
            val data = doc.data ?: return null
            val name = data["name"] as? String ?: return null
            if (name.isBlank()) return null
            Member(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                name = name,
                role = data["role"] as? String ?: "مناوب",
                defaultShiftDay = data["defaultShiftDay"] as? String ?: "السبت",
                badge = data["badge"] as? String ?: "بدون علامة",
                supervisorName = data["supervisorName"] as? String ?: "",
                archived = when (val a = data["archived"]) {
                    is Boolean -> a
                    is String -> a.toBoolean()
                    is Number -> a.toInt() != 0
                    else -> false
                },
                createdAt = data["createdAt"] as? Timestamp,
                updatedAt = data["updatedAt"] as? Timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallbackParseMember for ${doc.id}", e)
            null
        }
    }

    private fun parseShift(doc: DocumentSnapshot): Shift? {
        return try {
            val obj = doc.toObject(Shift::class.java)
            if (obj != null && obj.memberName.isNotBlank()) {
                obj.copy(id = doc.id)
            } else {
                fallbackParseShift(doc)
            }
        } catch (e: Exception) {
            fallbackParseShift(doc)
        }
    }

    private fun fallbackParseShift(doc: DocumentSnapshot): Shift? {
        return try {
            val data = doc.data ?: return null
            val memberName = data["memberName"] as? String ?: return null
            Shift(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                memberId = data["memberId"] as? String ?: "",
                memberName = memberName,
                defaultShiftDay = data["defaultShiftDay"] as? String ?: "",
                actualShiftDay = data["actualShiftDay"] as? String ?: "",
                date = data["date"] as? String ?: "",
                period = data["period"] as? String ?: "صباحية",
                serviceLocation = data["serviceLocation"] as? String ?: "",
                status = data["status"] as? String ?: "حاضر",
                manualOverride = when (val m = data["manualOverride"]) {
                    is Boolean -> m
                    is String -> m.toBoolean()
                    is Number -> m.toInt() != 0
                    else -> false
                },
                overrideReason = data["overrideReason"] as? String ?: "",
                modifiedBy = data["modifiedBy"] as? String ?: "",
                approved = when (val a = data["approved"]) {
                    is Boolean -> a
                    is String -> a.toBoolean()
                    is Number -> a.toInt() != 0
                    else -> false
                },
                createdAt = data["createdAt"] as? Timestamp,
                updatedAt = data["updatedAt"] as? Timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallbackParseShift for ${doc.id}", e)
            null
        }
    }

    private fun parseDayApproval(doc: DocumentSnapshot): DayApproval? {
        return try {
            val obj = doc.toObject(DayApproval::class.java)
            if (obj != null) {
                obj.copy(id = doc.id)
            } else {
                fallbackParseDayApproval(doc)
            }
        } catch (e: Exception) {
            fallbackParseDayApproval(doc)
        }
    }

    private fun fallbackParseDayApproval(doc: DocumentSnapshot): DayApproval? {
        return try {
            val data = doc.data ?: return null
            DayApproval(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                date = data["date"] as? String ?: doc.id,
                approved = when (val a = data["approved"]) {
                    is Boolean -> a
                    is String -> a.toBoolean()
                    is Number -> a.toInt() != 0
                    else -> false
                },
                approvedBy = data["approvedBy"] as? String ?: "",
                updatedAt = data["updatedAt"] as? Timestamp
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseServiceLocation(doc: DocumentSnapshot): ServiceLocation? {
        return try {
            val obj = doc.toObject(ServiceLocation::class.java)
            if (obj != null && obj.name.isNotBlank()) {
                obj.copy(id = doc.id)
            } else {
                val data = doc.data ?: return null
                val name = data["name"] as? String ?: doc.id
                ServiceLocation(
                    id = doc.id,
                    name = name,
                    active = when (val a = data["active"]) {
                        is Boolean -> a
                        is String -> a.toBoolean()
                        is Number -> a.toInt() != 0
                        else -> true
                    },
                    isDeletable = when (val d = data["isDeletable"]) {
                        is Boolean -> d
                        is String -> d.toBoolean()
                        is Number -> d.toInt() != 0
                        else -> true
                    }
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePeriod(doc: DocumentSnapshot): ShiftPeriod? {
        return try {
            val obj = doc.toObject(ShiftPeriod::class.java)
            if (obj != null && obj.name.isNotBlank()) {
                obj.copy(id = doc.id)
            } else {
                val data = doc.data ?: return null
                val name = data["name"] as? String ?: doc.id
                ShiftPeriod(
                    id = doc.id,
                    name = name,
                    active = when (val a = data["active"]) {
                        is Boolean -> a
                        is String -> a.toBoolean()
                        is Number -> a.toInt() != 0
                        else -> true
                    },
                    isDeletable = when (val d = data["isDeletable"]) {
                        is Boolean -> d
                        is String -> d.toBoolean()
                        is Number -> d.toInt() != 0
                        else -> true
                    }
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAuditLog(doc: DocumentSnapshot): AuditLog? {
        return try {
            val obj = doc.toObject(AuditLog::class.java)
            if (obj != null) {
                obj.copy(id = doc.id)
            } else {
                val data = doc.data ?: return null
                AuditLog(
                    id = doc.id,
                    userId = data["userId"] as? String ?: "",
                    action = data["action"] as? String ?: "",
                    details = data["details"] as? String ?: "",
                    timestamp = data["timestamp"] as? Timestamp
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
