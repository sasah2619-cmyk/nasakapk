package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.Appointment
import com.example.data.AppointmentStatus
import com.example.data.ServiceItem
import com.example.data.SystemConfig
import com.example.data.UiState
import com.example.ui.components.NasaqEmptyState
import com.example.ui.components.NasaqErrorBanner
import com.example.ui.components.NasaqLoadingState
import com.example.ui.components.NasaqStatusBadge
import com.example.ui.theme.NasaqEmerald
import com.example.ui.theme.NasaqPrimaryBlue
import com.example.ui.theme.StatusConfirmedFg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.WhatsAppGreen
import com.example.util.BookingUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerDashboardScreen(
    config: SystemConfig,
    appointmentsState: UiState<List<Appointment>>,
    services: List<ServiceItem>,
    isBusy: Boolean,
    onUpdateStatus: (Appointment, AppointmentStatus) -> Unit,
    onSaveAdminNotes: (String, String) -> Unit,
    onMarkReminderSent: (String) -> Unit,
    onDeleteAppointment: (String) -> Unit,
    onCreateManualBooking: (
        service: ServiceItem,
        date: String,
        startTime: String,
        endTime: String,
        customerName: String,
        customerPhone: String,
        customerNotes: String,
        status: AppointmentStatus,
        onSuccess: () -> Unit,
    ) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<AppointmentStatus?>(null) }
    var editingNotesTarget by remember { mutableStateOf<Appointment?>(null) }
    var deleteTarget by remember { mutableStateOf<Appointment?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (appointmentsState) {
                is UiState.Loading -> item { NasaqLoadingState("جاري تحميل الطلبات والمواعيد...") }
                is UiState.Error -> item { NasaqErrorBanner(appointmentsState.message) }
                is UiState.Success -> {
                    val allAppointments = appointmentsState.data
                    val todayCount = allAppointments.count {
                        it.date == todayIso && it.statusEnum != AppointmentStatus.CANCELLED && it.statusEnum != AppointmentStatus.REJECTED
                    }
                    val pendingCount = allAppointments.count { it.statusEnum == AppointmentStatus.PENDING }
                    val confirmedCount = allAppointments.count { it.statusEnum == AppointmentStatus.CONFIRMED }
                    val completedRevenue = allAppointments
                        .filter { it.statusEnum == AppointmentStatus.COMPLETED }
                        .sumOf { it.service_price }

                    // KPI Grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                KpiCard(
                                    title = "مواعيد اليوم",
                                    value = todayCount.toString(),
                                    accent = NasaqPrimaryBlue,
                                    modifier = Modifier.weight(1f),
                                )
                                KpiCard(
                                    title = "قيد الانتظار",
                                    value = pendingCount.toString(),
                                    accent = StatusPendingFg,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                KpiCard(
                                    title = "المؤكدة",
                                    value = confirmedCount.toString(),
                                    accent = StatusConfirmedFg,
                                    modifier = Modifier.weight(1f),
                                )
                                KpiCard(
                                    title = "إيرادات المكتملة",
                                    value = BookingUtils.formatPrice(completedRevenue, config.currency),
                                    accent = NasaqEmerald,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Search & Filters
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث برمز الحجز، اسم العميل، أو الهاتف...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("owner_search_input"),
                                shape = MaterialTheme.shapes.medium,
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedStatusFilter == null,
                                        onClick = { selectedStatusFilter = null },
                                        label = { Text("الكل (${allAppointments.size})") },
                                    )
                                }
                                items(AppointmentStatus.entries) { status ->
                                    val count = allAppointments.count { it.statusEnum == status }
                                    FilterChip(
                                        selected = selectedStatusFilter == status,
                                        onClick = {
                                            selectedStatusFilter = if (selectedStatusFilter == status) null else status
                                        },
                                        label = { Text("${status.arabicLabel} ($count)") },
                                    )
                                }
                            }
                        }
                    }

                    val filtered = allAppointments.filter { apt ->
                        val matchesStatus = selectedStatusFilter == null || apt.statusEnum == selectedStatusFilter
                        val q = searchQuery.trim()
                        val matchesQuery = q.isEmpty() ||
                            apt.customer_name.contains(q, ignoreCase = true) ||
                            apt.customer_phone.contains(q, ignoreCase = true) ||
                            apt.booking_code.contains(q, ignoreCase = true) ||
                            apt.service_name.contains(q, ignoreCase = true)
                        matchesStatus && matchesQuery
                    }

                    if (filtered.isEmpty()) {
                        item {
                            NasaqEmptyState(
                                icon = Icons.Default.EventAvailable,
                                title = "لا توجد حجوزات مطابقة",
                                subtitle = "جرب تغيير عامل التصفية أو أضف حجزاً يدوياً من الزر السفلي.",
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { apt ->
                            OwnerAppointmentCard(
                                appointment = apt,
                                config = config,
                                isBusy = isBusy,
                                onConfirm = { onUpdateStatus(apt, AppointmentStatus.CONFIRMED) },
                                onComplete = { onUpdateStatus(apt, AppointmentStatus.COMPLETED) },
                                onReject = { onUpdateStatus(apt, AppointmentStatus.REJECTED) },
                                onCancel = { onUpdateStatus(apt, AppointmentStatus.CANCELLED) },
                                onEditNotes = { editingNotesTarget = apt },
                                onDelete = { deleteTarget = apt },
                                onCall = {
                                    if (!BookingUtils.openDialer(context, apt.customer_phone)) {
                                        onShowMessage("لا يوجد رقم هاتف صالح")
                                    }
                                },
                                onWhatsAppConfirm = {
                                    val msg = BookingUtils.renderTemplate(
                                        config.whatsapp_confirm_template,
                                        apt,
                                        config,
                                    )
                                    if (!BookingUtils.openWhatsApp(context, apt.customer_phone, config.country_code, msg)) {
                                        onShowMessage("تعذر فتح واتساب — تأكد من رقم الهاتف")
                                    }
                                },
                                onWhatsAppReminder = {
                                    val msg = BookingUtils.renderTemplate(
                                        config.whatsapp_reminder_template,
                                        apt,
                                        config,
                                    )
                                    if (BookingUtils.openWhatsApp(context, apt.customer_phone, config.country_code, msg)) {
                                        onMarkReminderSent(apt.id)
                                    } else {
                                        onShowMessage("تعذر فتح واتساب — تأكد من رقم الهاتف")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_manual_booking_fab"),
        ) {
            Icon(Icons.Default.Add, contentDescription = "إضافة حجز يدوي")
        }
    }

    editingNotesTarget?.let { target ->
        var notesText by remember(target.id) { mutableStateOf(target.admin_notes) }
        AlertDialog(
            onDismissRequest = { editingNotesTarget = null },
            title = { Text("ملاحظات الإدارة (${target.booking_code})") },
            text = {
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("ملاحظات داخلية سرية (لا يراها العميل)") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAdminNotes(target.id, notesText)
                        editingNotesTarget = null
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNotesTarget = null }) { Text("إلغاء") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الحجز نهائياً") },
            text = { Text("هل تريد حذف حجز ${target.customer_name} (${target.booking_code})؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAppointment(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("تراجع") }
            },
        )
    }

    if (showAddDialog) {
        ManualBookingDialog(
            services = services,
            defaultDate = todayIso,
            isBusy = isBusy,
            onDismiss = { showAddDialog = false },
            onSave = { svc, date, start, end, name, phone, notes, status ->
                onCreateManualBooking(svc, date, start, end, name, phone, notes, status) {
                    showAddDialog = false
                }
            },
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnerAppointmentCard(
    appointment: Appointment,
    config: SystemConfig,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onComplete: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    onEditNotes: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onWhatsAppConfirm: () -> Unit,
    onWhatsAppReminder: () -> Unit,
) {
    val status = appointment.statusEnum
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = appointment.booking_code,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (appointment.reminder_sent) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = "تم التذكير",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                NasaqStatusBadge(status = status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appointment.customer_name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${appointment.service_name} • ${BookingUtils.formatPrice(appointment.service_price, config.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "${appointment.date} • ${BookingUtils.formatArabicTime12h(appointment.start_time)} - ${BookingUtils.formatArabicTime12h(appointment.end_time)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (appointment.customer_phone.isNotBlank()) {
                        Text(
                            text = "هاتف: ${appointment.customer_phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row {
                    IconButton(onClick = onCall) {
                        Icon(Icons.Default.Call, contentDescription = "اتصال")
                    }
                    IconButton(onClick = onEditNotes) {
                        Icon(Icons.Default.EditNote, contentDescription = "ملاحظات الإدارة")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (appointment.customer_notes.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "ملاحظة العميل: ${appointment.customer_notes}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    )
                }
            }

            if (appointment.admin_notes.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "ملاحظة الإدارة: ${appointment.admin_notes}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status == AppointmentStatus.PENDING) {
                    Button(
                        onClick = onConfirm,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = NasaqEmerald),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تأكيد", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("رفض", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (status == AppointmentStatus.CONFIRMED) {
                    Button(
                        onClick = onComplete,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = NasaqPrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إكمال", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("إلغاء", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedButton(
                    onClick = onWhatsAppConfirm,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                    border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تأكيد واتساب", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onWhatsAppReminder,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                    border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تذكير واتساب", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ManualBookingDialog(
    services: List<ServiceItem>,
    defaultDate: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (ServiceItem, String, String, String, String, String, String, AppointmentStatus) -> Unit,
) {
    var selectedService by remember(services) { mutableStateOf(services.firstOrNull()) }
    var serviceMenuExpanded by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaultDate) }
    var startTime by remember { mutableStateOf("10:00") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة حجز يدوي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (services.isEmpty()) {
                    Text("يرجى إضافة خدمة أولاً من تبويب الخدمات.")
                } else {
                    Box {
                        OutlinedButton(
                            onClick = { serviceMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(selectedService?.name ?: "اختر الخدمة")
                        }
                        DropdownMenu(
                            expanded = serviceMenuExpanded,
                            onDismissRequest = { serviceMenuExpanded = false },
                        ) {
                            services.forEach { svc ->
                                DropdownMenuItem(
                                    text = { Text("${svc.name} (${svc.duration_minutes} د)") },
                                    onClick = {
                                        selectedService = svc
                                        serviceMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it; errorMsg = null },
                        label = { Text("اسم العميل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("رقم الهاتف") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it; errorMsg = null },
                            label = { Text("التاريخ YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f),
                        )
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it; errorMsg = null },
                            label = { Text("الوقت HH:MM") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy && selectedService != null,
                onClick = {
                    val svc = selectedService ?: return@Button
                    val dateOk = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date.trim())
                    val timeOk = Regex("^\\d{2}:\\d{2}$").matches(startTime.trim())
                    when {
                        customerName.trim().isEmpty() -> errorMsg = "أدخل اسم العميل"
                        !dateOk -> errorMsg = "صيغة التاريخ يجب أن تكون YYYY-MM-DD"
                        !timeOk -> errorMsg = "صيغة الوقت يجب أن تكون HH:MM"
                        else -> {
                            val endMin = BookingUtils.parseMinutes(startTime.trim()) + svc.duration_minutes
                            val endTime = BookingUtils.formatMinutes(endMin)
                            onSave(
                                svc,
                                date.trim(),
                                startTime.trim(),
                                endTime,
                                customerName,
                                customerPhone,
                                notes,
                                AppointmentStatus.CONFIRMED,
                            )
                        }
                    }
                },
            ) {
                Text("حفظ وتأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
