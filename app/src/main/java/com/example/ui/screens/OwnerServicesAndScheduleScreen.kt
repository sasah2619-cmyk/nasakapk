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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.DaySchedule
import com.example.data.ServiceItem
import com.example.data.SystemConfig
import com.example.data.UiState
import com.example.ui.components.NasaqEmptyState
import com.example.ui.components.NasaqErrorBanner
import com.example.ui.components.NasaqLoadingState
import com.example.ui.components.NasaqSectionHeader
import com.example.util.BookingUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun OwnerServicesScreen(
    servicesState: UiState<List<ServiceItem>>,
    currency: String,
    isBusy: Boolean,
    onSaveService: (
        serviceId: String?,
        name: String,
        description: String,
        price: Double,
        durationMinutes: Int,
        isActive: Boolean,
        orderIndex: Int,
        onDone: () -> Unit,
    ) -> Unit,
    onDeleteService: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingService by remember { mutableStateOf<ServiceItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ServiceItem?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NasaqSectionHeader(
                    title = "إدارة خدمات الحلاقة",
                    subtitle = "أضف أو عدّل خدمات القص واللحية والأسعار والمدة",
                    icon = Icons.Default.ContentCut,
                )
            }

            when (servicesState) {
                is UiState.Loading -> item { NasaqLoadingState("جاري تحميل الخدمات...") }
                is UiState.Error -> item { NasaqErrorBanner(servicesState.message) }
                is UiState.Success -> {
                    val list = servicesState.data
                    if (list.isEmpty()) {
                        item {
                            NasaqEmptyState(
                                icon = Icons.Default.ContentCut,
                                title = "لا توجد خدمات مسجلة",
                                subtitle = "اضغط على زر (+) لإضافة أول خدمة حلاقة.",
                            )
                        }
                    } else {
                        items(list, key = { it.id }) { svc ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = svc.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Surface(
                                                color = if (svc.active) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Text(
                                                    text = if (svc.active) "نشطة" else "مخفية",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                )
                                            }
                                        }
                                        if (svc.description.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = svc.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "${BookingUtils.formatPrice(svc.price, currency)} • ${svc.duration_minutes} دقيقة",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingService = svc }) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل")
                                        }
                                        IconButton(onClick = { deleteTarget = svc }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "حذف",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_service_fab"),
        ) {
            Icon(Icons.Default.Add, contentDescription = "إضافة خدمة")
        }
    }

    if (showCreateDialog || editingService != null) {
        val current = editingService
        val nextIndex = ((servicesState as? UiState.Success)?.data?.size ?: 0)
        ServiceFormDialog(
            initial = current,
            defaultOrder = current?.order_index ?: nextIndex,
            isBusy = isBusy,
            onDismiss = {
                showCreateDialog = false
                editingService = null
            },
            onSave = { name, desc, price, duration, active, order ->
                onSaveService(current?.id, name, desc, price, duration, active, order) {
                    showCreateDialog = false
                    editingService = null
                }
            },
        )
    }

    deleteTarget?.let { svc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الخدمة") },
            text = { Text("هل تريد حذف خدمة (${svc.name})؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteService(svc.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("تراجع") }
            },
        )
    }
}

@Composable
private fun ServiceFormDialog(
    initial: ServiceItem?,
    defaultOrder: Int,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Int, Boolean, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var priceStr by remember { mutableStateOf(initial?.price?.toInt()?.toString() ?: "") }
    var durationStr by remember { mutableStateOf((initial?.duration_minutes ?: 30).toString()) }
    var isActive by remember { mutableStateOf(initial?.active ?: true) }
    var orderStr by remember { mutableStateOf(defaultOrder.toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة خدمة جديدة" else "تعديل الخدمة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("اسم الخدمة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف مختصر") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it; errorMsg = null },
                        label = { Text("السعر") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = durationStr,
                        onValueChange = { durationStr = it; errorMsg = null },
                        label = { Text("المدة (دقيقة)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("إتاحة الخدمة للعملاء", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val parsedPrice = priceStr.toDoubleOrNull()
                    val parsedDuration = durationStr.toIntOrNull()
                    val parsedOrder = orderStr.toIntOrNull() ?: 0
                    when {
                        name.trim().isEmpty() -> errorMsg = "أدخل اسم الخدمة"
                        parsedPrice == null || parsedPrice < 0 -> errorMsg = "أدخل سعراً صحيحاً"
                        parsedDuration == null || parsedDuration !in 5..720 -> errorMsg = "المدة بين 5 و 720 دقيقة"
                        else -> onSave(name, description, parsedPrice, parsedDuration, isActive, parsedOrder)
                    }
                },
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerScheduleScreen(
    config: SystemConfig,
    isBusy: Boolean,
    onSaveConfig: (SystemConfig, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var workingHours by remember(config.working_hours) { mutableStateOf(config.working_hours) }
    var blockedSlots by remember(config.blocked_slots) { mutableStateOf(config.blocked_slots) }
    var blockDateInput by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time))
    }
    var blockHourInput by remember { mutableStateOf("") }
    var blockError by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NasaqSectionHeader(
                    title = "ساعات العمل الأسبوعية",
                    subtitle = "حدد أيام الدوام والعطل وساعات العمل بصيغة HH:MM",
                    icon = Icons.Default.Schedule,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BookingUtils.orderedDays.forEach { (dayKey, arabicDay) ->
                            val schedule = workingHours[dayKey] ?: DaySchedule()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = arabicDay,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.width(68.dp),
                                )
                                Switch(
                                    checked = schedule.open,
                                    onCheckedChange = { checked ->
                                        workingHours = workingHours.toMutableMap().apply {
                                            put(dayKey, schedule.copy(open = checked))
                                        }
                                    },
                                )
                                if (schedule.open) {
                                    OutlinedTextField(
                                        value = schedule.from,
                                        onValueChange = { v ->
                                            workingHours = workingHours.toMutableMap().apply {
                                                put(dayKey, schedule.copy(from = v.take(5)))
                                            }
                                        },
                                        label = { Text("من") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = schedule.to,
                                        onValueChange = { v ->
                                            workingHours = workingHours.toMutableMap().apply {
                                                put(dayKey, schedule.copy(to = v.take(5)))
                                            }
                                        },
                                        label = { Text("إلى") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Text(
                                        text = "عطلة",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }

                        Button(
                            enabled = !isBusy,
                            onClick = {
                                onSaveConfig(
                                    config.copy(working_hours = workingHours, blocked_slots = blockedSlots),
                                    "تم حفظ جدول ساعات العمل",
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("حفظ ساعات العمل")
                        }
                    }
                }
            }

            item {
                NasaqSectionHeader(
                    title = "حظر تواريخ أو ساعات يدوياً",
                    subtitle = "امنع الحجز في يوم كامل أو ساعة محددة",
                    icon = Icons.Default.Block,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = blockDateInput,
                                onValueChange = { blockDateInput = it; blockError = null },
                                label = { Text("التاريخ YYYY-MM-DD") },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f),
                            )
                            OutlinedTextField(
                                value = blockHourInput,
                                onValueChange = { blockHourInput = it; blockError = null },
                                label = { Text("الساعة (اختياري HH:MM)") },
                                placeholder = { Text("14:00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (blockError != null) {
                            Text(blockError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                val d = blockDateInput.trim()
                                val h = blockHourInput.trim()
                                val dateOk = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(d)
                                val hourOk = h.isEmpty() || Regex("^\\d{2}:\\d{2}$").matches(h)
                                if (!dateOk) {
                                    blockError = "صيغة التاريخ يجب أن تكون YYYY-MM-DD"
                                } else if (!hourOk) {
                                    blockError = "صيغة الساعة يجب أن تكون HH:MM أو اتركها فارغة لحظر اليوم كاملاً"
                                } else {
                                    val token = if (h.isEmpty()) d else "$d|$h"
                                    val next = (blockedSlots + token).distinct()
                                    blockedSlots = next
                                    blockHourInput = ""
                                    onSaveConfig(
                                        config.copy(working_hours = workingHours, blocked_slots = next),
                                        "تمت إضافة الحظر بنجاح",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إضافة حظر")
                        }

                        if (blockedSlots.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                blockedSlots.forEach { slot ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            val next = blockedSlots.filterNot { it == slot }
                                            blockedSlots = next
                                            onSaveConfig(
                                                config.copy(working_hours = workingHours, blocked_slots = next),
                                                "تمت إزالة الحظر",
                                            )
                                        },
                                        label = {
                                            Text(
                                                if (slot.contains("|")) slot.replace("|", " • ")
                                                else "$slot (يوم كامل)"
                                            )
                                        },
                                        trailingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = "إزالة", modifier = Modifier.size(16.dp))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
