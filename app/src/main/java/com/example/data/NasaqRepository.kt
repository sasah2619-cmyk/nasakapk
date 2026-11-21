package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class NasaqRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب جوجل أولاً.")

    private fun <T> Flow<T>.logFirestoreErrors(
        uid: String,
        operationType: OperationType,
        path: String,
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observeSystemConfig(): Flow<SystemConfig> = flow {
        val uid = requireUserId()
        val path = "system_config/main"
        emitAll(
            db.collection("system_config").document("main")
                .snapshots()
                .map { snapshot -> snapshot.toSystemConfigOrNull() ?: SystemConfig() }
                .logFirestoreErrors(uid, OperationType.GET, path)
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot -> snapshot.toUserProfileOrNull() }
                .logFirestoreErrors(uid, OperationType.GET, path)
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toUserProfileOrNull() }
                        .sortedByDescending { it.last_login?.seconds ?: 0L }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeServices(): Flow<List<ServiceItem>> = flow {
        val uid = requireUserId()
        val path = "services"
        emitAll(
            db.collection("services")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toServiceItemOrNull() }
                        .sortedBy { it.order_index }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeMyAppointments(userId: String): Flow<List<Appointment>> = flow {
        val uid = requireUserId()
        val path = "appointments"
        emitAll(
            db.collection("appointments")
                .whereEqualTo("user_uid", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toAppointmentOrNull() }
                        .sortedByDescending { it.created_at?.seconds ?: 0L }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeAllAppointments(): Flow<List<Appointment>> = flow {
        val uid = requireUserId()
        val path = "appointments"
        emitAll(
            db.collection("appointments")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toAppointmentOrNull() }
                        .sortedByDescending { it.created_at?.seconds ?: 0L }
                }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    fun observeActiveSlotLocksForDate(date: String): Flow<List<SlotLock>> = flow {
        val uid = requireUserId()
        val path = "slot_locks"
        emitAll(
            db.collection("slot_locks")
                .whereEqualTo("date", date)
                .whereEqualTo("status", "active")
                .snapshots()
                .map { snapshot -> snapshot.documents.mapNotNull { it.toSlotLockOrNull() } }
                .logFirestoreErrors(uid, OperationType.LIST, path)
        )
    }

    suspend fun ensureSignedInSetup(user: FirebaseUser): Result<Unit> {
        var path = "users/${user.uid}"
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "تعارض في معرف المستخدم الحالي" }
            val userEmail = user.email ?: throw IllegalStateException("البريد الإلكتروني غير متوفر")

            val configRef = db.collection("system_config").document("main")
            val configSnap = configRef.get().await()
            var ownerEmail = configSnap.toSystemConfigOrNull()?.owner_email ?: INITIAL_OWNER_EMAIL

            if (!configSnap.exists() && userEmail.equals(INITIAL_OWNER_EMAIL, ignoreCase = true)) {
                path = configRef.path
                val initialConfig = SystemConfig(owner_email = INITIAL_OWNER_EMAIL).toWriteMap()
                configRef.set(initialConfig).await()
                ownerEmail = INITIAL_OWNER_EMAIL
            }

            val isOwner = userEmail.equals(ownerEmail, ignoreCase = true)
            if (configSnap.exists() && isOwner) {
                val parsed = configSnap.toSystemConfigOrNull()
                val rawApp = configSnap.getString("app_name").orEmpty()
                val rawBiz = configSnap.getString("business_name").orEmpty()
                val rawDesc = configSnap.getString("business_description").orEmpty()
                val rawConf = configSnap.getString("whatsapp_confirm_template").orEmpty()
                val rawRem = configSnap.getString("whatsapp_reminder_template").orEmpty()
                if (parsed != null && (
                        rawApp != parsed.app_name ||
                        rawBiz != parsed.business_name ||
                        rawDesc != parsed.business_description ||
                        rawConf != parsed.whatsapp_confirm_template ||
                        rawRem != parsed.whatsapp_reminder_template
                    )
                ) {
                    path = configRef.path
                    configRef.update(
                        mapOf(
                            "app_name" to parsed.app_name,
                            "business_name" to parsed.business_name,
                            "business_description" to parsed.business_description,
                            "whatsapp_confirm_template" to parsed.whatsapp_confirm_template,
                            "whatsapp_reminder_template" to parsed.whatsapp_reminder_template,
                            "updated_at" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            }
            val userRef = db.collection("users").document(uid)
            path = userRef.path
            val userSnap = userRef.get().await()

            if (!userSnap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "email" to userEmail,
                    "display_name" to (user.displayName?.take(120) ?: userEmail.substringBefore("@")),
                    "phone" to (user.phoneNumber?.take(30) ?: ""),
                    "role" to if (isOwner) "owner" else "user",
                    "is_blocked" to false,
                    "total_bookings" to 0L,
                    "total_spent" to 0.0,
                    "admin_notes" to "",
                    "created_at" to FieldValue.serverTimestamp(),
                    "last_login" to FieldValue.serverTimestamp(),
                )
                user.photoUrl?.toString()?.take(500)?.let { payload["photo_url"] = it }
                userRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "display_name" to (user.displayName?.take(120) ?: userSnap.getString("display_name") ?: ""),
                    "last_login" to FieldValue.serverTimestamp(),
                )
                user.photoUrl?.toString()?.take(500)?.let { updates["photo_url"] = it }
                if (isOwner && userSnap.getString("role") != "owner") {
                    updates["role"] = "owner"
                }
                userRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMyPhone(phone: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val cleaned = phone.trim().take(30)
            val userRef = db.collection("users").document(uid)
            path = userRef.path
            userRef.update(
                mapOf(
                    "phone" to cleaned,
                    "last_login" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createCustomerBooking(
        service: ServiceItem,
        date: String,
        startTime: String,
        endTime: String,
        customerName: String,
        customerPhone: String,
        customerNotes: String,
    ): Result<String> {
        var path = "appointments"
        return try {
            val uid = requireUserId()
            val email = auth.currentUser?.email
                ?: throw IllegalStateException("البريد الإلكتروني للمستخدم غير متوفر")
            require(service.id.isNotBlank()) { "يرجى اختيار خدمة صالحة" }
            val safeName = customerName.trim().take(120)
            require(safeName.isNotEmpty()) { "يرجى إدخال الاسم" }
            val safePhone = customerPhone.trim().take(30)
            require(safePhone.length >= 6) { "يرجى إدخال رقم هاتف صحيح" }
            val safeNotes = customerNotes.trim().take(500)

            val bookingCode = "#NSQ-${Random.nextInt(1000, 9999)}"
            val docRef = db.collection("appointments").document()
            val lockRef = db.collection("slot_locks").document(docRef.id)
            path = docRef.path

            val appointmentPayload = mapOf(
                "booking_code" to bookingCode,
                "user_uid" to uid,
                "customer_name" to safeName,
                "customer_email" to email.take(150),
                "customer_phone" to safePhone,
                "service_id" to service.id,
                "service_name" to service.name.take(100),
                "service_price" to service.price,
                "service_duration" to service.duration_minutes.coerceIn(5, 720),
                "date" to date,
                "start_time" to startTime,
                "end_time" to endTime,
                "status" to AppointmentStatus.PENDING.raw,
                "customer_notes" to safeNotes,
                "admin_notes" to "",
                "reminder_sent" to false,
                "created_at" to FieldValue.serverTimestamp(),
                "updated_at" to FieldValue.serverTimestamp(),
            )
            val lockPayload = mapOf(
                "date" to date,
                "start_time" to startTime,
                "end_time" to endTime,
                "status" to "active",
                "updated_at" to FieldValue.serverTimestamp(),
            )

            db.runBatch { batch ->
                batch.set(docRef, appointmentPayload)
                batch.set(lockRef, lockPayload)
            }.await()

            // Best-effort save phone on user profile for convenience on future bookings
            runCatching {
                db.collection("users").document(uid).update(
                    mapOf("phone" to safePhone, "last_login" to FieldValue.serverTimestamp())
                ).await()
            }

            Result.success(bookingCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun cancelMyAppointment(appointmentId: String): Result<Unit> {
        var path = "appointments/$appointmentId"
        return try {
            requireUserId()
            require(appointmentId.isNotBlank()) { "معرف الحجز غير صالح" }
            val docRef = db.collection("appointments").document(appointmentId)
            val lockRef = db.collection("slot_locks").document(appointmentId)
            path = docRef.path

            db.runBatch { batch ->
                batch.update(
                    docRef,
                    mapOf(
                        "status" to AppointmentStatus.CANCELLED.raw,
                        "updated_at" to FieldValue.serverTimestamp(),
                    )
                )
                batch.update(
                    lockRef,
                    mapOf(
                        "status" to "released",
                        "updated_at" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createOwnerAppointment(
        service: ServiceItem,
        date: String,
        startTime: String,
        endTime: String,
        customerName: String,
        customerPhone: String,
        customerNotes: String,
        status: AppointmentStatus,
    ): Result<String> {
        var path = "appointments"
        return try {
            val uid = requireUserId()
            val email = auth.currentUser?.email ?: INITIAL_OWNER_EMAIL
            val safeName = customerName.trim().take(120)
            require(safeName.isNotEmpty()) { "يرجى إدخال اسم العميل" }
            val safePhone = customerPhone.trim().take(30)
            val bookingCode = "#NSQ-${Random.nextInt(1000, 9999)}"
            val docRef = db.collection("appointments").document()
            val lockRef = db.collection("slot_locks").document(docRef.id)
            path = docRef.path

            val lockStatus = if (status == AppointmentStatus.CANCELLED || status == AppointmentStatus.REJECTED) {
                "released"
            } else {
                "active"
            }

            db.runBatch { batch ->
                batch.set(
                    docRef,
                    mapOf(
                        "booking_code" to bookingCode,
                        "user_uid" to uid,
                        "customer_name" to safeName,
                        "customer_email" to email,
                        "customer_phone" to safePhone,
                        "service_id" to service.id,
                        "service_name" to service.name.take(100),
                        "service_price" to service.price,
                        "service_duration" to service.duration_minutes.coerceIn(5, 720),
                        "date" to date,
                        "start_time" to startTime,
                        "end_time" to endTime,
                        "status" to status.raw,
                        "customer_notes" to customerNotes.trim().take(500),
                        "admin_notes" to "حجز مضاف من لوحة الإدارة",
                        "reminder_sent" to false,
                        "created_at" to FieldValue.serverTimestamp(),
                        "updated_at" to FieldValue.serverTimestamp(),
                    )
                )
                batch.set(
                    lockRef,
                    mapOf(
                        "date" to date,
                        "start_time" to startTime,
                        "end_time" to endTime,
                        "status" to lockStatus,
                        "updated_at" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()
            Result.success(bookingCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAppointmentStatus(
        appointment: Appointment,
        newStatus: AppointmentStatus,
    ): Result<Unit> {
        var path = "appointments/${appointment.id}"
        return try {
            requireUserId()
            require(appointment.id.isNotBlank()) { "معرف الحجز غير صالح" }
            val docRef = db.collection("appointments").document(appointment.id)
            val lockRef = db.collection("slot_locks").document(appointment.id)
            path = docRef.path

            val shouldRelease =
                newStatus == AppointmentStatus.CANCELLED || newStatus == AppointmentStatus.REJECTED

            val lockSnap = lockRef.get().await()
            db.runBatch { batch ->
                batch.update(
                    docRef,
                    mapOf(
                        "status" to newStatus.raw,
                        "updated_at" to FieldValue.serverTimestamp(),
                    )
                )
                if (lockSnap.exists()) {
                    batch.update(
                        lockRef,
                        mapOf(
                            "status" to if (shouldRelease) "released" else "active",
                            "updated_at" to FieldValue.serverTimestamp(),
                        )
                    )
                }
            }.await()

            // Increment user booking stats once when transitioned to COMPLETED
            if (newStatus == AppointmentStatus.COMPLETED &&
                appointment.statusEnum != AppointmentStatus.COMPLETED &&
                appointment.user_uid.isNotBlank()
            ) {
                val userRef = db.collection("users").document(appointment.user_uid)
                val userSnap = userRef.get().await()
                if (userSnap.exists()) {
                    userRef.update(
                        mapOf(
                            "total_bookings" to FieldValue.increment(1L),
                            "total_spent" to FieldValue.increment(appointment.service_price),
                        )
                    ).await()
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAppointmentAdminNotes(appointmentId: String, notes: String): Result<Unit> {
        val path = "appointments/$appointmentId"
        return try {
            requireUserId()
            db.collection("appointments").document(appointmentId).update(
                mapOf(
                    "admin_notes" to notes.trim().take(500),
                    "updated_at" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markReminderSent(appointmentId: String): Result<Unit> {
        val path = "appointments/$appointmentId"
        return try {
            requireUserId()
            db.collection("appointments").document(appointmentId).update(
                mapOf(
                    "reminder_sent" to true,
                    "updated_at" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAppointment(appointmentId: String): Result<Unit> {
        val path = "appointments/$appointmentId"
        return try {
            requireUserId()
            val docRef = db.collection("appointments").document(appointmentId)
            val lockRef = db.collection("slot_locks").document(appointmentId)
            val lockExists = lockRef.get().await().exists()
            db.runBatch { batch ->
                batch.delete(docRef)
                if (lockExists) {
                    batch.delete(lockRef)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveService(
        serviceId: String?,
        name: String,
        description: String,
        price: Double,
        durationMinutes: Int,
        isActive: Boolean,
        orderIndex: Int,
    ): Result<Unit> {
        var path = "services"
        return try {
            requireUserId()
            val safeName = name.trim().take(100)
            require(safeName.isNotEmpty()) { "يرجى إدخال اسم الخدمة" }
            val safeDesc = description.trim().take(500)
            val safePrice = price.coerceAtLeast(0.0)
            val safeDuration = durationMinutes.coerceIn(5, 720)
            val safeOrder = orderIndex.coerceAtLeast(0)

            if (serviceId.isNullOrBlank()) {
                val ref = db.collection("services").document()
                path = ref.path
                ref.set(
                    mapOf(
                        "name" to safeName,
                        "description" to safeDesc,
                        "price" to safePrice,
                        "duration_minutes" to safeDuration,
                        "is_active" to isActive,
                        "order_index" to safeOrder,
                        "created_at" to FieldValue.serverTimestamp(),
                        "updated_at" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                val ref = db.collection("services").document(serviceId)
                path = ref.path
                ref.update(
                    mapOf(
                        "name" to safeName,
                        "description" to safeDesc,
                        "price" to safePrice,
                        "duration_minutes" to safeDuration,
                        "is_active" to isActive,
                        "order_index" to safeOrder,
                        "updated_at" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteService(serviceId: String): Result<Unit> {
        val path = "services/$serviceId"
        return try {
            requireUserId()
            db.collection("services").document(serviceId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCustomerAdmin(
        targetUid: String,
        isBlocked: Boolean,
        adminNotes: String,
    ): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            db.collection("users").document(targetUid).update(
                mapOf(
                    "is_blocked" to isBlocked,
                    "admin_notes" to adminNotes.trim().take(1000),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSystemConfig(config: SystemConfig): Result<Unit> {
        val path = "system_config/main"
        return try {
            requireUserId()
            require(config.business_name.trim().isNotEmpty()) { "يرجى إدخال اسم صالون الحلاقة" }
            db.collection("system_config").document("main")
                .update(config.toWriteMap(includeTransferFields = false))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun transferOwnership(currentConfig: SystemConfig, newOwnerEmail: String): Result<Unit> {
        var path = "system_config/main"
        return try {
            val currentUid = requireUserId()
            val normalizedNew = newOwnerEmail.trim().lowercase()
            require(normalizedNew.contains("@") && normalizedNew.length >= 4 && normalizedNew.length <= 150) {
                "يرجى إدخال بريد إلكتروني صحيح للمالك الجديد"
            }
            require(!normalizedNew.equals(currentConfig.owner_email, ignoreCase = true)) {
                "البريد الإلكتروني المدخل هو بالفعل المالك الحالي"
            }

            // Update user role documents BEFORE updating system_config/main while current user still holds owner rights
            val newOwnerQuery = db.collection("users")
                .whereEqualTo("email", normalizedNew)
                .get()
                .await()
            for (doc in newOwnerQuery.documents) {
                path = doc.reference.path
                doc.reference.update("role", "owner").await()
            }

            val currentUserRef = db.collection("users").document(currentUid)
            val currentUserSnap = currentUserRef.get().await()
            if (currentUserSnap.exists()) {
                path = currentUserRef.path
                currentUserRef.update("role", "user").await()
            }

            path = "system_config/main"
            val updatedConfig = currentConfig.copy(
                owner_email = normalizedNew,
                previous_owner_email = currentConfig.owner_email,
            )
            db.collection("system_config").document("main")
                .update(updatedConfig.toWriteMap(includeTransferFields = true))
                .await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
