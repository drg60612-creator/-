package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiagnosticLogEntry
import com.example.data.DiagnosticsManager
import com.example.data.DiagnosticsStatus
import com.example.data.model.AuditLog
import com.example.data.model.DayApproval
import com.example.data.model.Member
import com.example.data.model.ServiceLocation
import com.example.data.model.Shift
import com.example.data.model.ShiftPeriod
import com.example.data.repository.MemberRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MembersViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MemberRepository(application)

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _shifts = MutableStateFlow<List<Shift>>(emptyList())
    val shifts: StateFlow<List<Shift>> = _shifts.asStateFlow()

    private val _dayApprovals = MutableStateFlow<List<DayApproval>>(emptyList())
    val dayApprovals: StateFlow<List<DayApproval>> = _dayApprovals.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    private val _serviceLocations = MutableStateFlow<List<ServiceLocation>>(emptyList())
    val serviceLocations: StateFlow<List<ServiceLocation>> = _serviceLocations.asStateFlow()

    private val _periods = MutableStateFlow<List<ShiftPeriod>>(emptyList())
    val periods: StateFlow<List<ShiftPeriod>> = _periods.asStateFlow()

    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = DiagnosticsManager.logs
    val diagnosticsStatus: StateFlow<DiagnosticsStatus> = DiagnosticsManager.status

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Active, 1: Archived
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        DiagnosticsManager.updateStatus(application)
        viewModelScope.launch {
            repository.ensureDefaultConfigs()
        }
        loadData()
    }

    fun refreshDiagnostics() {
        DiagnosticsManager.updateStatus(getApplication())
    }

    fun testConnection() {
        viewModelScope.launch {
            _isLoading.value = true
            DiagnosticsManager.updateStatus(getApplication())
            val res = repository.pingFirestoreConnection()
            _isLoading.value = false
            if (res.isSuccess) {
                _userMessage.value = res.getOrNull() ?: "الاتصال بقاعدة البيانات سليم"
            } else {
                _errorMessage.value = "فشل فحص الاتصال: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.observeMembers()
                .catch { e ->
                    _errorMessage.value = "خطأ في تحميل الأعضاء: ${e.localizedMessage ?: e.message}"
                    _isLoading.value = false
                }
                .collect { list ->
                    _members.value = list
                    _isLoading.value = false
                }
        }

        viewModelScope.launch {
            repository.observeShifts()
                .catch { e ->
                    _errorMessage.value = "خطأ في تحميل المناوبات: ${e.localizedMessage ?: e.message}"
                }
                .collect { list ->
                    _shifts.value = list
                }
        }

        viewModelScope.launch {
            repository.observeDayApprovals()
                .catch { e ->
                    _errorMessage.value = "خطأ في تحميل اعتمادات الأيام: ${e.localizedMessage ?: e.message}"
                }
                .collect { list ->
                    _dayApprovals.value = list
                }
        }

        viewModelScope.launch {
            repository.observeAuditLogs()
                .collect { logs ->
                    _auditLogs.value = logs
                }
        }

        viewModelScope.launch {
            repository.observeServiceLocations()
                .collect { locs ->
                    _serviceLocations.value = locs
                }
        }

        viewModelScope.launch {
            repository.observePeriods()
                .collect { pers ->
                    _periods.value = pers
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun addMember(member: Member, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.addMember(member, "إضافة عضو جديد إلى الفريق")
                _userMessage.value = "تمت إضافة العضو (${member.name}) بنجاح إلى قاعدة البيانات"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل إضافة العضو: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun updateMember(member: Member, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateMember(member, "تعديل بيانات العضو ${member.name}")
                _userMessage.value = "تم تحديث بيانات العضو (${member.name}) بنجاح"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل تحديث بيانات العضو: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun archiveMember(memberId: String, memberName: String, archived: Boolean) {
        viewModelScope.launch {
            try {
                repository.archiveMember(memberId, memberName, archived)
                _userMessage.value = if (archived) "تمت أرشفة العضو ($memberName) بنجاح" else "تمت استعادة العضو ($memberName) بنجاح"
            } catch (e: Exception) {
                _errorMessage.value = "فشل تعديل حالة الأرشفة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun saveShift(shift: Shift, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.saveShift(shift)
                _userMessage.value = "تم حفظ المناوبة بنجاح في قاعدة البيانات"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل حفظ المناوبة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun deleteShift(shiftId: String, memberName: String, date: String) {
        viewModelScope.launch {
            try {
                repository.deleteShift(shiftId, memberName, date)
                _userMessage.value = "تم حذف مناوبة $memberName بتاريخ $date بنجاح"
            } catch (e: Exception) {
                _errorMessage.value = "فشل حذف المناوبة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun setDayApproval(date: String, approved: Boolean, userName: String) {
        viewModelScope.launch {
            try {
                repository.setDayApproval(date, approved, userName)
                _userMessage.value = if (approved) "تم اعتماد اليوم ($date) وتثبيت الإحصائيات" else "تم إلغاء اعتماد اليوم ($date)"
            } catch (e: Exception) {
                _errorMessage.value = "فشل اعتماد اليوم: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun addServiceLocation(name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.addServiceLocation(name)
                _userMessage.value = "تمت إضافة مكان الخدمة ($name) بنجاح"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل إضافة مكان الخدمة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun updateServiceLocation(id: String, oldName: String, newName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateServiceLocation(id, oldName, newName)
                _userMessage.value = "تم تعديل مكان الخدمة إلى ($newName) بنجاح"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل تعديل مكان الخدمة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun deleteServiceLocation(id: String, name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val isUsed = _shifts.value.any { it.serviceLocation == name }
                repository.deleteServiceLocation(id, name, isUsed)
                _userMessage.value = if (isUsed) "المكان مرتبط بمناوبات سابقة، تم تعطيله بأمان" else "تم حذف مكان الخدمة بنجاح"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل حذف مكان الخدمة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun addPeriod(name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.addPeriod(name)
                _userMessage.value = "تمت إضافة فترة المناوبة ($name) بنجاح"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل إضافة الفترة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun updatePeriod(id: String, oldName: String, newName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updatePeriod(id, oldName, newName)
                _userMessage.value = "تم تعديل فترة المناوبة إلى ($newName) بنجاح"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل تعديل فترة المناوبة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun deletePeriod(id: String, name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val isUsed = _shifts.value.any { it.period == name }
                repository.deletePeriod(id, name, isUsed)
                _userMessage.value = if (isUsed) "الفترة مرتبطة بمناوبات سابقة، تم تعطيلها بأمان" else "تم حذف فترة المناوبة بنجاح"
                onComplete()
            } catch (e: Exception) {
                _errorMessage.value = "فشل حذف الفترة: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun importMembersFromCsv(csvText: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val lines = csvText.lines()
                val newMembers = mutableListOf<Member>()
                val validDays = setOf(
                    "السبت", "السبت 1", "السبت 2",
                    "الأحد", "الأحد 1", "الأحد 2",
                    "الإثنين", "الإثنين 1", "الإثنين 2",
                    "الاثنين", "الاثنين 1", "الاثنين 2",
                    "الثلاثاء", "الثلاثاء 1", "الثلاثاء 2",
                    "الأربعاء", "الأربعاء 1", "الأربعاء 2",
                    "الخميس", "الخميس 1", "الخميس 2",
                    "الجمعة", "الجمعة 1", "الجمعة 2"
                )
                var unknownDayFound = false
                for (line in lines) {
                    if (line.isBlank()) continue
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size >= 5) {
                        val inputDay = parts[2]
                        if (!validDays.contains(inputDay)) {
                            unknownDayFound = true
                        }
                        newMembers.add(
                            Member(
                                name = parts[0],
                                role = if (parts[1].contains("مشرف")) "مشرف" else "مناوب",
                                defaultShiftDay = inputDay,
                                badge = parts[3],
                                supervisorName = parts[4],
                                archived = false
                            )
                        )
                    }
                }
                if (newMembers.isNotEmpty()) {
                    repository.importMembers(newMembers)
                    val msg = if (unknownDayFound) {
                        "تم استيراد ${newMembers.size} عضو بنجاح. تنبيه: وُجدت أيام غير معرفة قياسياً وتم استيرادها كما هي."
                    } else {
                        "تم استيراد ${newMembers.size} عضو بنجاح في قاعدة البيانات"
                    }
                    _userMessage.value = msg
                    onComplete(newMembers.size)
                } else {
                    _errorMessage.value = "لم يتم العثور على أسطر صالحة. يجب أن يحتوي كل سطر على 5 قيم مفصولة بفواصل: الاسم, الدور, اليوم, العلامة, المشرف"
                }
            } catch (e: Exception) {
                _errorMessage.value = "فشل استيراد الأعضاء: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _userMessage.value = null
    }
}
