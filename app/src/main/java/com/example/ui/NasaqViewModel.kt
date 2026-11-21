package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Appointment
import com.example.data.AppointmentStatus
import com.example.data.INITIAL_OWNER_EMAIL
import com.example.data.NasaqRepository
import com.example.data.ServiceItem
import com.example.data.SlotLock
import com.example.data.SystemConfig
import com.example.data.UiState
import com.example.data.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NasaqViewModel(
    private val repository: NasaqRepository,
    private val currentUserId: String,
    private val currentUserEmail: String,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(todayIsoDate())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _actionMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val actionMessage: SharedFlow<String> = _actionMessage.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private fun <T> Flow<T>.toUiStateFlow(
        label: String,
        initial: UiState<T> = UiState.Loading,
    ): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "حدث خطأ أثناء تحميل $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = initial,
            )

    val systemConfigState: StateFlow<UiState<SystemConfig>> =
        repository.observeSystemConfig()
            .toUiStateFlow("إعدادات النظام")

    val currentUserProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiStateFlow("الملف الشخصي")

    val servicesState: StateFlow<UiState<List<ServiceItem>>> =
        repository.observeServices()
            .toUiStateFlow("الخدمات")

    val myAppointmentsState: StateFlow<UiState<List<Appointment>>> =
        repository.observeMyAppointments(currentUserId)
            .toUiStateFlow("حجوزاتي")

    val slotLocksForSelectedDate: StateFlow<List<SlotLock>> =
        _selectedDate
            .flatMapLatest { date ->
                repository.observeActiveSlotLocksForDate(date)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing slot locks", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList(),
            )

    val isOwner: StateFlow<Boolean> =
        systemConfigState
            .map { state ->
                val ownerEmail = (state as? UiState.Success)?.data?.owner_email ?: INITIAL_OWNER_EMAIL
                currentUserEmail.equals(ownerEmail, ignoreCase = true)
            }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = currentUserEmail.equals(INITIAL_OWNER_EMAIL, ignoreCase = true),
            )

    // Owner-only streams: NEVER attached unless isOwner == true
    val allAppointmentsState: StateFlow<UiState<List<Appointment>>> =
        isOwner
            .flatMapLatest { owner ->
                if (!owner) {
                    flowOf<UiState<List<Appointment>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeAllAppointments()
                        .map<List<Appointment>, UiState<List<Appointment>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing all appointments", rootError)
                            emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل قائمة الطلبات"))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val allUsersState: StateFlow<UiState<List<UserProfile>>> =
        isOwner
            .flatMapLatest { owner ->
                if (!owner) {
                    flowOf<UiState<List<UserProfile>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeAllUsers()
                        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing users", rootError)
                            emit(UiState.Error(rootError.localizedMessage ?: "تعذر تحميل قائمة العملاء"))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    fun syncSignedInUser(user: FirebaseUser) {
        viewModelScope.launch {
            repository.ensureSignedInSetup(user).onFailure { err ->
                _actionMessage.tryEmit(err.localizedMessage ?: "تعذر مزامنة بيانات الحساب")
            }
        }
    }

    fun selectDate(isoDate: String) {
        _selectedDate.value = isoDate
    }

    fun createCustomerBooking(
        service: ServiceItem,
        date: String,
        startTime: String,
        endTime: String,
        customerName: String,
        customerPhone: String,
        customerNotes: String,
        onBooked: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.createCustomerBooking(
                service = service,
                date = date,
                startTime = startTime,
                endTime = endTime,
                customerName = customerName,
                customerPhone = customerPhone,
                customerNotes = customerNotes,
            ).onSuccess { code ->
                _actionMessage.tryEmit("تم إرسال طلب الحجز بنجاح ($code)")
                onBooked(code)
            }.onFailure { err ->
                _actionMessage.tryEmit(err.localizedMessage ?: "تعذر إتمام الحجز")
            }
            _isBusy.value = false
        }
    }

    fun cancelMyAppointment(appointmentId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.cancelMyAppointment(appointmentId)
                .onSuccess { _actionMessage.tryEmit("تم إلغاء الموعد بنجاح") }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر إلغاء الموعد") }
            _isBusy.value = false
        }
    }

    fun createOwnerManualBooking(
        service: ServiceItem,
        date: String,
        startTime: String,
        endTime: String,
        customerName: String,
        customerPhone: String,
        customerNotes: String,
        status: AppointmentStatus,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.createOwnerAppointment(
                service = service,
                date = date,
                startTime = startTime,
                endTime = endTime,
                customerName = customerName,
                customerPhone = customerPhone,
                customerNotes = customerNotes,
                status = status,
            ).onSuccess { code ->
                _actionMessage.tryEmit("تمت إضافة الحجز ($code)")
                onSuccess()
            }.onFailure { err ->
                _actionMessage.tryEmit(err.localizedMessage ?: "تعذر إضافة الحجز")
            }
            _isBusy.value = false
        }
    }

    fun updateAppointmentStatus(appointment: Appointment, status: AppointmentStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateAppointmentStatus(appointment, status)
                .onSuccess { _actionMessage.tryEmit("تم تحديث الحالة إلى: ${status.arabicLabel}") }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر تحديث الحالة") }
            _isBusy.value = false
        }
    }

    fun updateAppointmentAdminNotes(appointmentId: String, notes: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateAppointmentAdminNotes(appointmentId, notes)
                .onSuccess { _actionMessage.tryEmit("تم حفظ ملاحظات الإدارة") }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر حفظ الملاحظات") }
            _isBusy.value = false
        }
    }

    fun markReminderSent(appointmentId: String) {
        viewModelScope.launch {
            repository.markReminderSent(appointmentId)
                .onSuccess { _actionMessage.tryEmit("تم تسجيل إرسال التذكير") }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر تحديث حالة التذكير") }
        }
    }

    fun deleteAppointment(appointmentId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteAppointment(appointmentId)
                .onSuccess { _actionMessage.tryEmit("تم حذف الحجز") }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر حذف الحجز") }
            _isBusy.value = false
        }
    }

    fun saveService(
        serviceId: String?,
        name: String,
        description: String,
        price: Double,
        durationMinutes: Int,
        isActive: Boolean,
        orderIndex: Int,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveService(serviceId, name, description, price, durationMinutes, isActive, orderIndex)
                .onSuccess {
                    _actionMessage.tryEmit(if (serviceId == null) "تمت إضافة الخدمة" else "تم تحديث الخدمة")
                    onSaved()
                }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر حفظ الخدمة") }
            _isBusy.value = false
        }
    }

    fun deleteService(serviceId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteService(serviceId)
                .onSuccess { _actionMessage.tryEmit("تم حذف الخدمة") }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر حذف الخدمة") }
            _isBusy.value = false
        }
    }

    fun updateCustomerAdmin(targetUid: String, isBlocked: Boolean, adminNotes: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateCustomerAdmin(targetUid, isBlocked, adminNotes)
                .onSuccess { _actionMessage.tryEmit("تم تحديث بيانات العميل") }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر تحديث بيانات العميل") }
            _isBusy.value = false
        }
    }

    fun updateSystemConfig(config: SystemConfig, message: String = "تم حفظ الإعدادات") {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateSystemConfig(config)
                .onSuccess { _actionMessage.tryEmit(message) }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر حفظ الإعدادات") }
            _isBusy.value = false
        }
    }

    fun transferOwnership(currentConfig: SystemConfig, newOwnerEmail: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.transferOwnership(currentConfig, newOwnerEmail)
                .onSuccess {
                    _actionMessage.tryEmit("تم نقل الملكية بنجاح إلى $newOwnerEmail")
                    onDone()
                }
                .onFailure { err -> _actionMessage.tryEmit(err.localizedMessage ?: "تعذر نقل الملكية") }
            _isBusy.value = false
        }
    }

    private companion object {
        const val TAG = "NasaqViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L

        fun todayIsoDate(): String {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            return fmt.format(java.util.Calendar.getInstance().time)
        }
    }
}
