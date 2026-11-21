package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

const val INITIAL_OWNER_EMAIL = "sasah2619@gmail.com"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class DaySchedule(
    val open: Boolean = true,
    val from: String = "09:00",
    val to: String = "18:00",
) {
    fun toMap(): Map<String, Any> = mapOf(
        "open" to open,
        "from" to from,
        "to" to to,
    )
}

@Keep
@IgnoreExtraProperties
data class SystemConfig(
    val app_name: String = "حلاقة المصطفى",
    val owner_email: String = INITIAL_OWNER_EMAIL,
    val business_name: String = "حلاقة المصطفى",
    val business_description: String = "أهلاً بك في حلاقة المصطفى — احجز موعدك بسهولة بدون انتظار.",
    val business_phone: String = "07700000000",
    val business_address: String = "بغداد - المنصور",
    val currency: String = "د.ع",
    val country_code: String = "+964",
    val whatsapp_confirm_template: String =
        "مرحباً {customer_name}، تم تأكيد موعدك في {business_name} لخدمة ({service_name}) يوم {date} الساعة {start_time}. رمز الحجز: {booking_code}",
    val whatsapp_reminder_template: String =
        "تذكير بموعدك في {business_name}: خدمة ({service_name}) يوم {date} الساعة {start_time}. رمز الحجز: {booking_code}",
    val working_hours: Map<String, DaySchedule> = defaultWorkingHours(),
    val blocked_slots: List<String> = emptyList(),
    val previous_owner_email: String? = null,
    val ownership_transferred_at: Timestamp? = null,
    @ServerTimestamp val updated_at: Timestamp? = null,
) {
    fun toWriteMap(includeTransferFields: Boolean = false): Map<String, Any> {
        val hoursMap = working_hours.mapValues { (_, schedule) -> schedule.toMap() }
        val base = mutableMapOf<String, Any>(
            "app_name" to app_name,
            "owner_email" to owner_email,
            "business_name" to business_name,
            "business_description" to business_description,
            "business_phone" to business_phone,
            "business_address" to business_address,
            "currency" to currency,
            "country_code" to country_code,
            "whatsapp_confirm_template" to whatsapp_confirm_template,
            "whatsapp_reminder_template" to whatsapp_reminder_template,
            "working_hours" to hoursMap,
            "blocked_slots" to blocked_slots,
            "updated_at" to FieldValue.serverTimestamp(),
        )
        if (includeTransferFields || !previous_owner_email.isNullOrBlank()) {
            previous_owner_email?.let { base["previous_owner_email"] = it }
        }
        if (includeTransferFields) {
            base["ownership_transferred_at"] = FieldValue.serverTimestamp()
        } else if (ownership_transferred_at != null) {
            base["ownership_transferred_at"] = ownership_transferred_at
        }
        return base
    }
}

fun defaultWorkingHours(): Map<String, DaySchedule> = linkedMapOf(
    "sunday" to DaySchedule(open = true, from = "09:00", to = "18:00"),
    "monday" to DaySchedule(open = true, from = "09:00", to = "18:00"),
    "tuesday" to DaySchedule(open = true, from = "09:00", to = "18:00"),
    "wednesday" to DaySchedule(open = true, from = "09:00", to = "18:00"),
    "thursday" to DaySchedule(open = true, from = "09:00", to = "18:00"),
    "friday" to DaySchedule(open = false, from = "09:00", to = "18:00"),
    "saturday" to DaySchedule(open = true, from = "10:00", to = "16:00"),
)

fun DocumentSnapshot.toSystemConfigOrNull(): SystemConfig? {
    val data = data ?: return null
    val rawHours = data["working_hours"] as? Map<*, *>
    val defaults = defaultWorkingHours()
    val parsedHours = defaults.mapValues { (day, fallback) ->
        val dayMap = rawHours?.get(day) as? Map<*, *>
        if (dayMap == null) fallback
        else DaySchedule(
            open = (dayMap["open"] as? Boolean) ?: fallback.open,
            from = (dayMap["from"] as? String)?.takeIf { it.length == 5 } ?: fallback.from,
            to = (dayMap["to"] as? String)?.takeIf { it.length == 5 } ?: fallback.to,
        )
    }
    val blocked = (data["blocked_slots"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val defaultsConfig = SystemConfig()
    val rawAppName = (data["app_name"] as? String).orEmpty().trim()
    val rawBusinessName = (data["business_name"] as? String).orEmpty().trim()
    val rawDescription = (data["business_description"] as? String).orEmpty().trim()
    val rawConfirmTemplate = (data["whatsapp_confirm_template"] as? String).orEmpty().trim()
    val rawReminderTemplate = (data["whatsapp_reminder_template"] as? String).orEmpty().trim()

    val legacyWordRegex = Regex("(^|\\s)نَ?سَ?ق(?=$|\\s)")
    fun String.hasLegacyBrand(): Boolean = contains("نَسَق") || legacyWordRegex.containsMatchIn(this)
    fun String.replaceLegacyBrand(): String = this
        .replace("مركز نَسَق للخدمات", "حلاقة المصطفى")
        .replace("مركز نسق للخدمات", "حلاقة المصطفى")
        .replace("صالون نَسَق للحلاقة", "حلاقة المصطفى")
        .replace("صالون نسق للحلاقة", "حلاقة المصطفى")
        .replace("نَسَق", "حلاقة المصطفى")
        .replace(legacyWordRegex, "$1حلاقة المصطفى")

    val normalizedBusinessName = when {
        rawBusinessName.isBlank() || rawBusinessName.hasLegacyBrand() -> defaultsConfig.business_name
        else -> rawBusinessName
    }
    val normalizedDescription = when {
        rawDescription.hasLegacyBrand() -> defaultsConfig.business_description
        else -> rawDescription
    }
    val normalizedConfirmTemplate = when {
        rawConfirmTemplate.isBlank() -> defaultsConfig.whatsapp_confirm_template
        else -> rawConfirmTemplate.replaceLegacyBrand()
    }
    val normalizedReminderTemplate = when {
        rawReminderTemplate.isBlank() -> defaultsConfig.whatsapp_reminder_template
        else -> rawReminderTemplate.replaceLegacyBrand()
    }

    return SystemConfig(
        app_name = if (rawAppName.isBlank() || rawAppName.hasLegacyBrand()) defaultsConfig.app_name else rawAppName,
        owner_email = (data["owner_email"] as? String) ?: INITIAL_OWNER_EMAIL,
        business_name = normalizedBusinessName,
        business_description = normalizedDescription,
        business_phone = (data["business_phone"] as? String) ?: "",
        business_address = (data["business_address"] as? String) ?: "",
        currency = (data["currency"] as? String) ?: "د.ع",
        country_code = (data["country_code"] as? String) ?: "+964",
        whatsapp_confirm_template = normalizedConfirmTemplate,
        whatsapp_reminder_template = normalizedReminderTemplate,
        working_hours = parsedHours,
        blocked_slots = blocked,
        previous_owner_email = data["previous_owner_email"] as? String,
        ownership_transferred_at = getTimestamp("ownership_transferred_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        updated_at = getTimestamp("updated_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val display_name: String = "",
    val photo_url: String? = null,
    val phone: String = "",
    val role: String = "user",
    @get:PropertyName("is_blocked")
    @PropertyName("is_blocked")
    val blocked: Boolean = false,
    val total_bookings: Long = 0L,
    val total_spent: Double = 0.0,
    val admin_notes: String = "",
    @ServerTimestamp val created_at: Timestamp? = null,
    @ServerTimestamp val last_login: Timestamp? = null,
)

fun DocumentSnapshot.toUserProfileOrNull(): UserProfile? {
    val data = data ?: return null
    return UserProfile(
        uid = (data["uid"] as? String) ?: id,
        email = (data["email"] as? String) ?: "",
        display_name = (data["display_name"] as? String) ?: "",
        photo_url = data["photo_url"] as? String,
        phone = (data["phone"] as? String) ?: "",
        role = (data["role"] as? String) ?: "user",
        blocked = (data["is_blocked"] as? Boolean) ?: false,
        total_bookings = (data["total_bookings"] as? Number)?.toLong() ?: 0L,
        total_spent = (data["total_spent"] as? Number)?.toDouble() ?: 0.0,
        admin_notes = (data["admin_notes"] as? String) ?: "",
        created_at = getTimestamp("created_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        last_login = getTimestamp("last_login", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
    )
}

@Keep
@IgnoreExtraProperties
data class ServiceItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val duration_minutes: Int = 30,
    @get:PropertyName("is_active")
    @PropertyName("is_active")
    val active: Boolean = true,
    val order_index: Int = 0,
    @ServerTimestamp val created_at: Timestamp? = null,
    @ServerTimestamp val updated_at: Timestamp? = null,
)

fun DocumentSnapshot.toServiceItemOrNull(): ServiceItem? {
    val data = data ?: return null
    return ServiceItem(
        id = id,
        name = (data["name"] as? String) ?: "",
        description = (data["description"] as? String) ?: "",
        price = (data["price"] as? Number)?.toDouble() ?: 0.0,
        duration_minutes = (data["duration_minutes"] as? Number)?.toInt() ?: 30,
        active = (data["is_active"] as? Boolean) ?: true,
        order_index = (data["order_index"] as? Number)?.toInt() ?: 0,
        created_at = getTimestamp("created_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        updated_at = getTimestamp("updated_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
    )
}

enum class AppointmentStatus(val raw: String, val arabicLabel: String) {
    PENDING("pending", "قيد الانتظار"),
    CONFIRMED("confirmed", "مؤكد"),
    COMPLETED("completed", "مكتمل"),
    CANCELLED("cancelled", "ملغي"),
    REJECTED("rejected", "مرفوض");

    companion object {
        fun fromRaw(raw: String?): AppointmentStatus =
            entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class Appointment(
    @DocumentId val id: String = "",
    val booking_code: String = "",
    val user_uid: String = "",
    val customer_name: String = "",
    val customer_email: String = "",
    val customer_phone: String = "",
    val service_id: String = "",
    val service_name: String = "",
    val service_price: Double = 0.0,
    val service_duration: Int = 30,
    val date: String = "",
    val start_time: String = "",
    val end_time: String = "",
    val status: String = AppointmentStatus.PENDING.raw,
    val customer_notes: String = "",
    val admin_notes: String = "",
    val reminder_sent: Boolean = false,
    @ServerTimestamp val created_at: Timestamp? = null,
    @ServerTimestamp val updated_at: Timestamp? = null,
) {
    val statusEnum: AppointmentStatus
        get() = AppointmentStatus.fromRaw(status)
}

fun DocumentSnapshot.toAppointmentOrNull(): Appointment? {
    val data = data ?: return null
    return Appointment(
        id = id,
        booking_code = (data["booking_code"] as? String) ?: "",
        user_uid = (data["user_uid"] as? String) ?: "",
        customer_name = (data["customer_name"] as? String) ?: "",
        customer_email = (data["customer_email"] as? String) ?: "",
        customer_phone = (data["customer_phone"] as? String) ?: "",
        service_id = (data["service_id"] as? String) ?: "",
        service_name = (data["service_name"] as? String) ?: "",
        service_price = (data["service_price"] as? Number)?.toDouble() ?: 0.0,
        service_duration = (data["service_duration"] as? Number)?.toInt() ?: 30,
        date = (data["date"] as? String) ?: "",
        start_time = (data["start_time"] as? String) ?: "",
        end_time = (data["end_time"] as? String) ?: "",
        status = (data["status"] as? String) ?: AppointmentStatus.PENDING.raw,
        customer_notes = (data["customer_notes"] as? String) ?: "",
        admin_notes = (data["admin_notes"] as? String) ?: "",
        reminder_sent = (data["reminder_sent"] as? Boolean) ?: false,
        created_at = getTimestamp("created_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
        updated_at = getTimestamp("updated_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
    )
}

@Keep
@IgnoreExtraProperties
data class SlotLock(
    @DocumentId val id: String = "",
    val date: String = "",
    val start_time: String = "",
    val end_time: String = "",
    val status: String = "active",
    @ServerTimestamp val updated_at: Timestamp? = null,
)

fun DocumentSnapshot.toSlotLockOrNull(): SlotLock? {
    val data = data ?: return null
    return SlotLock(
        id = id,
        date = (data["date"] as? String) ?: "",
        start_time = (data["start_time"] as? String) ?: "",
        end_time = (data["end_time"] as? String) ?: "",
        status = (data["status"] as? String) ?: "active",
        updated_at = getTimestamp("updated_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
    )
}
