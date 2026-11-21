package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.Appointment
import com.example.data.DaySchedule
import com.example.data.SlotLock
import com.example.data.SystemConfig
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DateChoice(
    val isoDate: String,      // YYYY-MM-DD
    val dayKey: String,       // sunday, monday, ...
    val arabicDayName: String,
    val dayNumber: String,
    val arabicMonthName: String,
    val isOpenDay: Boolean,
    val isFullDayBlocked: Boolean,
)

data class TimeSlotOption(
    val startTime: String,
    val endTime: String,
    val isAvailable: Boolean,
    val unavailableReason: String? = null,
)

object BookingUtils {
    private val enLocale = Locale.US
    private val arLocale = Locale.forLanguageTag("ar")

    private val dayKeys = listOf(
        Calendar.SUNDAY to ("sunday" to "الأحد"),
        Calendar.MONDAY to ("monday" to "الإثنين"),
        Calendar.TUESDAY to ("tuesday" to "الثلاثاء"),
        Calendar.WEDNESDAY to ("wednesday" to "الأربعاء"),
        Calendar.THURSDAY to ("thursday" to "الخميس"),
        Calendar.FRIDAY to ("friday" to "الجمعة"),
        Calendar.SATURDAY to ("saturday" to "السبت"),
    ).toMap()

    val orderedDays = listOf(
        "sunday" to "الأحد",
        "monday" to "الإثنين",
        "tuesday" to "الثلاثاء",
        "wednesday" to "الأربعاء",
        "thursday" to "الخميس",
        "friday" to "الجمعة",
        "saturday" to "السبت",
    )

    fun buildUpcomingDates(
        daysCount: Int = 14,
        workingHours: Map<String, DaySchedule>,
        blockedSlots: List<String>,
    ): List<DateChoice> {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", enLocale)
        val dayNumFormat = SimpleDateFormat("d", enLocale)
        val monthFormat = SimpleDateFormat("MMM", arLocale)
        val cal = Calendar.getInstance()

        return (0 until daysCount).map { offset ->
            val current = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val iso = isoFormat.format(current.time)
            val dow = current.get(Calendar.DAY_OF_WEEK)
            val (key, arName) = dayKeys[dow] ?: ("sunday" to "الأحد")
            val schedule = workingHours[key] ?: DaySchedule()
            DateChoice(
                isoDate = iso,
                dayKey = key,
                arabicDayName = if (offset == 0) "اليوم" else if (offset == 1) "غداً" else arName,
                dayNumber = dayNumFormat.format(current.time),
                arabicMonthName = monthFormat.format(current.time),
                isOpenDay = schedule.open,
                isFullDayBlocked = blockedSlots.contains(iso),
            )
        }
    }

    fun dayKeyForIsoDate(isoDate: String): String {
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd", enLocale)
            val parsed = isoFormat.parse(isoDate) ?: return "sunday"
            val cal = Calendar.getInstance().apply { time = parsed }
            dayKeys[cal.get(Calendar.DAY_OF_WEEK)]?.first ?: "sunday"
        } catch (_: Exception) {
            "sunday"
        }
    }

    fun parseMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size != 2) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        return h * 60 + m
    }

    fun formatMinutes(totalMinutes: Int): String {
        val clamped = totalMinutes.coerceIn(0, 23 * 60 + 59)
        val h = clamped / 60
        val m = clamped % 60
        return String.format(enLocale, "%02d:%02d", h, m)
    }

    fun formatArabicTime12h(hhmm: String): String {
        val mins = parseMinutes(hhmm)
        val h24 = mins / 60
        val m = mins % 60
        val period = if (h24 < 12) "ص" else "م"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format(enLocale, "%d:%02d %s", h12, m, period)
    }

    fun generateTimeSlots(
        isoDate: String,
        serviceDurationMinutes: Int,
        workingHours: Map<String, DaySchedule>,
        blockedSlots: List<String>,
        activeLocks: List<SlotLock>,
    ): List<TimeSlotOption> {
        val dayKey = dayKeyForIsoDate(isoDate)
        val schedule = workingHours[dayKey] ?: return emptyList()
        if (!schedule.open || blockedSlots.contains(isoDate)) return emptyList()

        val startMin = parseMinutes(schedule.from)
        val endMin = parseMinutes(schedule.to)
        val duration = serviceDurationMinutes.coerceAtLeast(15)
        if (endMin - startMin < duration) return emptyList()

        val step = 30
        val todayIso = SimpleDateFormat("yyyy-MM-dd", enLocale).format(Calendar.getInstance().time)
        val nowCal = Calendar.getInstance()
        val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        val slots = mutableListOf<TimeSlotOption>()
        var cursor = startMin
        while (cursor + duration <= endMin) {
            val slotStart = formatMinutes(cursor)
            val slotEnd = formatMinutes(cursor + duration)
            val isPastToday = isoDate == todayIso && cursor <= nowMinutes + 15
            val isManuallyBlocked = blockedSlots.contains("$isoDate|$slotStart")
            val overlapsBooking = activeLocks.any { lock ->
                val lockStart = parseMinutes(lock.start_time)
                val lockEnd = parseMinutes(lock.end_time)
                cursor < lockEnd && (cursor + duration) > lockStart
            }

            val available = !isPastToday && !isManuallyBlocked && !overlapsBooking
            val reason = when {
                isPastToday -> "فات الوقت"
                isManuallyBlocked -> "محظور"
                overlapsBooking -> "محجوز"
                else -> null
            }
            slots.add(TimeSlotOption(slotStart, slotEnd, available, reason))
            cursor += step
        }
        return slots
    }

    fun formatPrice(price: Double, currency: String): String {
        val formatter = NumberFormat.getNumberInstance(enLocale).apply {
            maximumFractionDigits = if (price % 1.0 == 0.0) 0 else 2
        }
        return "${formatter.format(price)} $currency"
    }

    fun formatWhatsAppPhone(rawPhone: String, countryCode: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        val ccDigits = countryCode.filter { it.isDigit() }
        return when {
            rawPhone.trim().startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            ccDigits.isNotEmpty() && digits.startsWith(ccDigits) -> digits
            ccDigits.isNotEmpty() && digits.startsWith("0") -> ccDigits + digits.drop(1)
            ccDigits.isNotEmpty() -> ccDigits + digits
            else -> digits
        }
    }

    fun renderTemplate(
        template: String,
        appointment: Appointment,
        config: SystemConfig,
    ): String {
        val legacyWordRegex = Regex("(^|\\s)نَ?سَ?ق(?=$|\\s)")
        val rawBiz = config.business_name.trim()
        val brandName = rawBiz
            .takeIf { it.isNotEmpty() && !it.contains("نَسَق") && !legacyWordRegex.containsMatchIn(it) }
            ?: "حلاقة المصطفى"
        val sourceTemplate = template.ifBlank { SystemConfig().whatsapp_confirm_template }
        return sourceTemplate
            .replace("{customer_name}", appointment.customer_name)
            .replace("{service_name}", appointment.service_name)
            .replace("{date}", appointment.date)
            .replace("{start_time}", formatArabicTime12h(appointment.start_time))
            .replace("{booking_code}", appointment.booking_code)
            .replace("{business_name}", brandName)
            .replace("مركز نَسَق للخدمات", brandName)
            .replace("مركز نسق للخدمات", brandName)
            .replace("صالون نَسَق للحلاقة", brandName)
            .replace("صالون نسق للحلاقة", brandName)
            .replace("نَسَق", brandName)
            .replace(legacyWordRegex, "$1$brandName")
    }

    fun openWhatsApp(
        context: Context,
        phone: String,
        countryCode: String,
        message: String,
    ): Boolean {
        val normalized = formatWhatsAppPhone(phone, countryCode)
        if (normalized.isBlank()) return false
        return try {
            val uri = Uri.parse("https://wa.me/$normalized?text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openDialer(context: Context, phone: String): Boolean {
        if (phone.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openAddressOrMap(context: Context, addressOrUrl: String): Boolean {
        if (addressOrUrl.isBlank()) return false
        return try {
            val uri = if (addressOrUrl.startsWith("http://") || addressOrUrl.startsWith("https://")) {
                Uri.parse(addressOrUrl.trim())
            } else {
                Uri.parse("geo:0,0?q=${Uri.encode(addressOrUrl.trim())}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
