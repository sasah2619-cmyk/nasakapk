package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Appointment
import com.example.data.AppointmentStatus
import com.example.data.UiState
import com.example.ui.components.NasaqEmptyState
import com.example.ui.components.NasaqErrorBanner
import com.example.ui.components.NasaqLoadingState
import com.example.ui.components.NasaqSectionHeader
import com.example.ui.components.NasaqStatusBadge
import com.example.util.BookingUtils

@Composable
fun CustomerBookingsScreen(
    appointmentsState: UiState<List<Appointment>>,
    currency: String,
    isBusy: Boolean,
    onCancelAppointment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var appointmentToCancel by remember { mutableStateOf<Appointment?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                NasaqSectionHeader(
                    title = "حجوزاتي",
                    subtitle = "مواعيدك القادمة والسابقة",
                    icon = Icons.AutoMirrored.Filled.EventNote,
                )
            }

            when (appointmentsState) {
                is UiState.Loading -> item { NasaqLoadingState("جاري تحميل حجوزاتك...") }
                is UiState.Error -> item { NasaqErrorBanner(appointmentsState.message) }
                is UiState.Success -> {
                    val list = appointmentsState.data
                    if (list.isEmpty()) {
                        item {
                            NasaqEmptyState(
                                icon = Icons.Default.CalendarMonth,
                                title = "لا توجد حجوزات حتى الآن",
                                subtitle = "انتقل إلى تبويب حجز موعد لاختيار الخدمة والوقت المناسبين لك.",
                            )
                        }
                    } else {
                        items(list, key = { it.id }) { item ->
                            CustomerBookingCard(
                                appointment = item,
                                currency = currency,
                                isBusy = isBusy,
                                onRequestCancel = { appointmentToCancel = item },
                            )
                        }
                    }
                }
            }
        }
    }

    val target = appointmentToCancel
    if (target != null) {
        AlertDialog(
            onDismissRequest = { appointmentToCancel = null },
            title = { Text("تأكيد إلغاء الحجز") },
            text = {
                Text("هل أنت متأكد من إلغاء موعد (${target.service_name}) بتاريخ ${target.date} الساعة ${BookingUtils.formatArabicTime12h(target.start_time)}؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelAppointment(target.id)
                        appointmentToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("نعم، إلغاء الموعد")
                }
            },
            dismissButton = {
                TextButton(onClick = { appointmentToCancel = null }) {
                    Text("تراجع")
                }
            },
        )
    }
}

@Composable
private fun CustomerBookingCard(
    appointment: Appointment,
    currency: String,
    isBusy: Boolean,
    onRequestCancel: () -> Unit,
) {
    val status = appointment.statusEnum
    val canCancel = status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED

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
                NasaqStatusBadge(status = status)
            }

            Text(
                text = appointment.service_name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = appointment.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${BookingUtils.formatArabicTime12h(appointment.start_time)} - ${BookingUtils.formatArabicTime12h(appointment.end_time)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "التكلفة: ${BookingUtils.formatPrice(appointment.service_price, currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                if (canCancel) {
                    OutlinedButton(
                        onClick = onRequestCancel,
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("cancel_booking_${appointment.id}"),
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إلغاء الحجز", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (appointment.customer_notes.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "ملاحظاتك: ${appointment.customer_notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}
