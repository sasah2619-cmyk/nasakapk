package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ServiceItem
import com.example.data.SlotLock
import com.example.data.SystemConfig
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.ui.components.NasaqEmptyState
import com.example.ui.components.NasaqErrorBanner
import com.example.ui.components.NasaqLoadingState
import com.example.ui.components.NasaqSectionHeader
import com.example.ui.theme.NasaqEmerald
import com.example.ui.theme.NasaqNavyDeep
import com.example.ui.theme.NasaqRoyalBlue
import com.example.ui.theme.NasaqTurquoise
import com.example.util.BookingUtils
import com.example.util.DateChoice
import com.example.util.TimeSlotOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerBookingScreen(
    config: SystemConfig,
    userProfile: UserProfile?,
    servicesState: UiState<List<ServiceItem>>,
    selectedDate: String,
    slotLocks: List<SlotLock>,
    isBusy: Boolean,
    onSelectDate: (String) -> Unit,
    onCreateBooking: (
        service: ServiceItem,
        date: String,
        startTime: String,
        endTime: String,
        customerName: String,
        customerPhone: String,
        customerNotes: String,
        onBooked: (String) -> Unit,
    ) -> Unit,
    onNavigateToMyBookings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedServiceId by remember { mutableStateOf<String?>(null) }
    var selectedSlot by remember { mutableStateOf<TimeSlotOption?>(null) }
    var customerName by remember { mutableStateOf(userProfile?.display_name ?: "") }
    var customerPhone by remember { mutableStateOf(userProfile?.phone ?: "") }
    var customerNotes by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var createdCodeDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userProfile?.display_name, userProfile?.phone) {
        val profile = userProfile ?: return@LaunchedEffect
        if (customerName.isBlank() && profile.display_name.isNotBlank()) {
            customerName = profile.display_name
        }
        if (customerPhone.isBlank() && profile.phone.isNotBlank()) {
            customerPhone = profile.phone
        }
    }

    val activeServices = remember(servicesState) {
        (servicesState as? UiState.Success)?.data?.filter { it.active } ?: emptyList()
    }
    val selectedService = remember(activeServices, selectedServiceId) {
        activeServices.firstOrNull { it.id == selectedServiceId } ?: activeServices.firstOrNull()
    }

    LaunchedEffect(selectedService?.id, selectedDate) {
        selectedSlot = null
    }

    val upcomingDates = remember(config.working_hours, config.blocked_slots) {
        BookingUtils.buildUpcomingDates(
            daysCount = 14,
            workingHours = config.working_hours,
            blockedSlots = config.blocked_slots,
        )
    }

    val availableSlots = remember(selectedDate, selectedService, config, slotLocks) {
        if (selectedService == null) emptyList()
        else BookingUtils.generateTimeSlots(
            isoDate = selectedDate,
            serviceDurationMinutes = selectedService.duration_minutes,
            workingHours = config.working_hours,
            blockedSlots = config.blocked_slots,
            activeLocks = slotLocks,
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Business Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NasaqNavyDeep, NasaqRoyalBlue)
                                )
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = config.business_name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        if (config.business_description.isNotBlank()) {
                            Text(
                                text = config.business_description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (config.business_phone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { BookingUtils.openDialer(context, config.business_phone) },
                                    border = BorderStroke(1.dp, NasaqTurquoise.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(config.business_phone, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (config.business_address.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { BookingUtils.openAddressOrMap(context, config.business_address) },
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "الموقع",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step 1: Select Service
            item {
                NasaqSectionHeader(
                    title = "1. اختر خدمة الحلاقة",
                    subtitle = "قص شعر، تهذيب لحية، أو باقة متكاملة",
                    icon = Icons.Default.ContentCut,
                )
            }

            when (servicesState) {
                is UiState.Loading -> item { NasaqLoadingState("جاري تحميل الخدمات...") }
                is UiState.Error -> item { NasaqErrorBanner(servicesState.message) }
                is UiState.Success -> {
                    if (activeServices.isEmpty()) {
                        item {
                            NasaqEmptyState(
                                icon = Icons.Default.ContentCut,
                                title = "لا توجد خدمات متاحة حالياً",
                                subtitle = "سيتم عرض الخدمات هنا فور إضافتها من قبل الحلاق.",
                            )
                        }
                    } else {
                        items(activeServices, key = { it.id }) { service ->
                            val isSelected = selectedService?.id == service.id
                            ServiceChoiceCard(
                                service = service,
                                currency = config.currency,
                                isSelected = isSelected,
                                onClick = { selectedServiceId = service.id },
                            )
                        }
                    }
                }
            }

            if (selectedService != null) {
                // Step 2: Select Date
                item {
                    NasaqSectionHeader(
                        title = "2. اختر اليوم المناسب",
                        subtitle = "الأيام المتاحة للحجز خلال الأسبوعين القادمين",
                        icon = Icons.Default.CalendarToday,
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(upcomingDates, key = { it.isoDate }) { day ->
                            DateChipCard(
                                day = day,
                                isSelected = day.isoDate == selectedDate,
                                onClick = { onSelectDate(day.isoDate) },
                            )
                        }
                    }
                }

                // Step 3: Available Time Slots
                item {
                    NasaqSectionHeader(
                        title = "3. اختر الوقت المتاح",
                        subtitle = "المدة: ${selectedService.duration_minutes} دقيقة",
                        icon = Icons.Default.AccessTime,
                    )
                }

                item {
                    val openSlots = availableSlots.filter { it.isAvailable }
                    if (availableSlots.isEmpty()) {
                        NasaqEmptyState(
                            icon = Icons.Default.EventAvailable,
                            title = "اليوم مغلق أو محظور",
                            subtitle = "يرجى اختيار يوم آخر متاح من القائمة أعلاه.",
                        )
                    } else if (openSlots.isEmpty()) {
                        NasaqEmptyState(
                            icon = Icons.Default.AccessTime,
                            title = "لا توجد أوقات شاغرة في هذا اليوم",
                            subtitle = "جميع الأوقات محجوزة أو منتهية، جرب يوماً آخر.",
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                availableSlots.forEach { slot ->
                                    val chosen = selectedSlot?.startTime == slot.startTime
                                    val bgColor = when {
                                        !slot.isAvailable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        chosen -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    val textColor = when {
                                        !slot.isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        chosen -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    val borderColor = when {
                                        !slot.isAvailable -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        chosen -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(enabled = slot.isAvailable) {
                                                selectedSlot = slot
                                                formError = null
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = bgColor,
                                        border = BorderStroke(1.dp, borderColor),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = BookingUtils.formatArabicTime12h(slot.startTime),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = textColor,
                                            )
                                            if (!slot.isAvailable && slot.unavailableReason != null) {
                                                Text(
                                                    text = slot.unavailableReason,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = textColor,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 4: Customer Info & Submit
                item {
                    NasaqSectionHeader(
                        title = "4. بيانات الحجز والتأكيد",
                        subtitle = "تصل رسالة تأكيد الموعد من الحلاق على رقم هاتفك",
                        icon = Icons.Default.Person,
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
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it; formError = null },
                                label = { Text("الاسم الكامل") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("booking_name_input"),
                            )
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it; formError = null },
                                label = { Text("رقم الهاتف (واتساب)") },
                                placeholder = { Text("0770XXXXXXX") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("booking_phone_input"),
                            )
                            OutlinedTextField(
                                value = customerNotes,
                                onValueChange = { customerNotes = it },
                                label = { Text("ملاحظات إضافية (اختياري)") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (selectedSlot != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(
                                                text = selectedService.name,
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                text = "$selectedDate • ${BookingUtils.formatArabicTime12h(selectedSlot!!.startTime)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = BookingUtils.formatPrice(selectedService.price, config.currency),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            if (formError != null) {
                                NasaqErrorBanner(message = formError!!)
                            }

                            Button(
                                onClick = {
                                    val slot = selectedSlot
                                    when {
                                        slot == null -> formError = "يرجى اختيار وقت متاح للموعد أولاً"
                                        customerName.trim().isEmpty() -> formError = "يرجى إدخال الاسم الكامل"
                                        customerPhone.trim().length < 6 -> formError = "يرجى إدخال رقم هاتف صحيح للتواصل"
                                        else -> {
                                            formError = null
                                            onCreateBooking(
                                                selectedService,
                                                selectedDate,
                                                slot.startTime,
                                                slot.endTime,
                                                customerName,
                                                customerPhone,
                                                customerNotes,
                                            ) { code ->
                                                customerNotes = ""
                                                selectedSlot = null
                                                createdCodeDialog = code
                                            }
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("confirm_booking_button"),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = NasaqEmerald),
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("تأكيد طلب الحجز", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (createdCodeDialog != null) {
        AlertDialog(
            onDismissRequest = { createdCodeDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = NasaqEmerald,
                    modifier = Modifier.size(44.dp),
                )
            },
            title = { Text("تم إرسال طلب الحجز!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تم إرسال طلب موعدك بنجاح، وسيتم تأكيده قريباً.")
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "رمز الحجز: $createdCodeDialog",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        createdCodeDialog = null
                        onNavigateToMyBookings()
                    }
                ) {
                    Text("عرض حجوزاتي")
                }
            },
            dismissButton = {
                TextButton(onClick = { createdCodeDialog = null }) {
                    Text("إغلاق")
                }
            },
        )
    }
}

@Composable
private fun ServiceChoiceCard(
    service: ServiceItem,
    currency: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("service_card_${service.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (service.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = service.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "${service.duration_minutes} دقيقة",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = BookingUtils.formatPrice(service.price, currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                if (isSelected) {
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "محدد",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateChipCard(
    day: DateChoice,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isClosed = !day.isOpenDay || day.isFullDayBlocked
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isClosed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isClosed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 0.dp else 1.dp,
            color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = day.arabicDayName,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
            )
            Text(
                text = day.dayNumber,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Text(
                text = if (isClosed) "مغلق" else day.arabicMonthName,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}
