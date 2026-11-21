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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.SystemConfig
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.ui.components.NasaqEmptyState
import com.example.ui.components.NasaqErrorBanner
import com.example.ui.components.NasaqLoadingState
import com.example.ui.components.NasaqSectionHeader
import com.example.ui.theme.NasaqEmerald
import com.example.ui.theme.WhatsAppGreen
import com.example.util.BookingUtils

@Composable
fun OwnerCustomersScreen(
    usersState: UiState<List<UserProfile>>,
    currency: String,
    countryCode: String,
    isBusy: Boolean,
    onUpdateCustomer: (targetUid: String, isBlocked: Boolean, adminNotes: String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editingCustomer by remember { mutableStateOf<UserProfile?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NasaqSectionHeader(
                    title = "إدارة العملاء",
                    subtitle = "متابعة إحصائيات الحجوزات، ملاحظات الإدارة، وحالة الحظر",
                    icon = Icons.Default.Group,
                )
            }

            when (usersState) {
                is UiState.Loading -> item { NasaqLoadingState("جاري تحميل قائمة العملاء...") }
                is UiState.Error -> item { NasaqErrorBanner(usersState.message) }
                is UiState.Success -> {
                    val list = usersState.data
                    if (list.isEmpty()) {
                        item {
                            NasaqEmptyState(
                                icon = Icons.Default.Group,
                                title = "لا يوجد عملاء مسجلون بعد",
                                subtitle = "سيظهر هنا كل عميل يقوم بتسجيل الدخول عبر حساب جوجل.",
                            )
                        }
                    } else {
                        items(list, key = { it.uid }) { user ->
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
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (!user.photo_url.isNullOrBlank()) {
                                            AsyncImage(
                                                model = user.photo_url,
                                                contentDescription = user.display_name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape),
                                            )
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = user.display_name.ifBlank { user.email },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                if (user.role == "owner") {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(6.dp),
                                                    ) {
                                                        Text(
                                                            text = "المالك",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                                if (user.blocked) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.errorContainer,
                                                        shape = RoundedCornerShape(6.dp),
                                                    ) {
                                                        Text(
                                                            text = "محظور",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = user.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (user.phone.isNotBlank()) {
                                                Text(
                                                    text = "هاتف: ${user.phone}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Row {
                                            if (user.phone.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        if (!BookingUtils.openDialer(context, user.phone)) {
                                                            onShowMessage("تعذر فتح الاتصال")
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Call, contentDescription = "اتصال")
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (!BookingUtils.openWhatsApp(context, user.phone, countryCode, "مرحباً ${user.display_name}")) {
                                                            onShowMessage("تعذر فتح واتساب")
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Chat,
                                                        contentDescription = "واتساب",
                                                        tint = WhatsAppGreen,
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { editingCustomer = user }) {
                                                Icon(Icons.Default.EditNote, contentDescription = "إدارة العميل")
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "الحجوزات المكتملة: ${user.total_bookings}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "إجمالي الإنفاق: ${BookingUtils.formatPrice(user.total_spent, currency)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }

                                    if (user.admin_notes.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text(
                                                text = "ملاحظات الإدارة: ${user.admin_notes}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
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
    }

    editingCustomer?.let { target ->
        var isBlocked by remember(target.uid) { mutableStateOf(target.blocked) }
        var notes by remember(target.uid) { mutableStateOf(target.admin_notes) }

        AlertDialog(
            onDismissRequest = { editingCustomer = null },
            title = { Text("إدارة العميل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(target.display_name.ifBlank { target.email }, fontWeight = FontWeight.Bold)
                    if (target.role != "owner") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("حظر العميل من الحجز")
                            }
                            Switch(checked = isBlocked, onCheckedChange = { isBlocked = it })
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات الإدارة الخاصة") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        onUpdateCustomer(target.uid, isBlocked, notes)
                        editingCustomer = null
                    },
                ) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { editingCustomer = null }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
fun OwnerSettingsScreen(
    config: SystemConfig,
    isBusy: Boolean,
    onSaveConfig: (SystemConfig) -> Unit,
    onTransferOwnership: (String, () -> Unit) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var businessName by remember(config.business_name) { mutableStateOf(config.business_name) }
    var businessDesc by remember(config.business_description) { mutableStateOf(config.business_description) }
    var businessPhone by remember(config.business_phone) { mutableStateOf(config.business_phone) }
    var businessAddress by remember(config.business_address) { mutableStateOf(config.business_address) }
    var currency by remember(config.currency) { mutableStateOf(config.currency) }
    var countryCode by remember(config.country_code) { mutableStateOf(config.country_code) }
    var confirmTemplate by remember(config.whatsapp_confirm_template) { mutableStateOf(config.whatsapp_confirm_template) }
    var reminderTemplate by remember(config.whatsapp_reminder_template) { mutableStateOf(config.whatsapp_reminder_template) }

    var showTransferDialog by remember { mutableStateOf(false) }
    var newOwnerEmail by remember { mutableStateOf("") }
    var confirmOwnerEmail by remember { mutableStateOf("") }
    var transferError by remember { mutableStateOf<String?>(null) }

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
                    title = "إعدادات صالون الحلاقة",
                    subtitle = "تظهر هذه البيانات للعملاء في أعلى صفحة الحجز",
                    icon = Icons.Default.Settings,
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
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("اسم صالون الحلاقة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = businessDesc,
                            onValueChange = { businessDesc = it },
                            label = { Text("وصف مختصر") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = businessPhone,
                                onValueChange = { businessPhone = it },
                                label = { Text("هاتف الإدارة") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(1.2f),
                            )
                            OutlinedTextField(
                                value = currency,
                                onValueChange = { currency = it },
                                label = { Text("العملة") },
                                singleLine = true,
                                modifier = Modifier.weight(0.7f),
                            )
                            OutlinedTextField(
                                value = countryCode,
                                onValueChange = { countryCode = it },
                                label = { Text("مفتاح الدولة") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                        OutlinedTextField(
                            value = businessAddress,
                            onValueChange = { businessAddress = it },
                            label = { Text("العنوان أو رابط الخريطة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = confirmTemplate,
                            onValueChange = { confirmTemplate = it },
                            label = { Text("قالب رسالة تأكيد واتساب") },
                            supportingText = { Text("المتغيرات: {customer_name} {service_name} {date} {start_time} {booking_code} {business_name}") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = reminderTemplate,
                            onValueChange = { reminderTemplate = it },
                            label = { Text("قالب رسالة التذكير") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            enabled = !isBusy,
                            onClick = {
                                onSaveConfig(
                                    config.copy(
                                        business_name = businessName.trim(),
                                        business_description = businessDesc.trim(),
                                        business_phone = businessPhone.trim(),
                                        business_address = businessAddress.trim(),
                                        currency = currency.trim().ifEmpty { "د.ع" },
                                        country_code = countryCode.trim().ifEmpty { "+964" },
                                        whatsapp_confirm_template = confirmTemplate.trim(),
                                        whatsapp_reminder_template = reminderTemplate.trim(),
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_settings_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NasaqEmerald),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("حفظ الإعدادات")
                        }
                    }
                }
            }

            item {
                NasaqSectionHeader(
                    title = "الملكية وإدارة الحساب",
                    subtitle = "المالك الحالي: ${config.owner_email}",
                    icon = Icons.Default.AdminPanelSettings,
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
                        if (!config.previous_owner_email.isNullOrBlank()) {
                            Text(
                                text = "المالك السابق: ${config.previous_owner_email}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                newOwnerEmail = ""
                                confirmOwnerEmail = ""
                                transferError = null
                                showTransferDialog = true
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("transfer_ownership_button"),
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("نقل ملكية التطبيق إلى حساب جوجل آخر")
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("تسجيل الخروج")
                        }
                    }
                }
            }
        }
    }

    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("نقل ملكية التطبيق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "تحذير: عند نقل الملكية ستفقد صلاحيات المالك فوراً وتنتقل بالكامل إلى البريد الإلكتروني الجديد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedTextField(
                        value = newOwnerEmail,
                        onValueChange = { newOwnerEmail = it; transferError = null },
                        label = { Text("البريد الإلكتروني للمالك الجديد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirmOwnerEmail,
                        onValueChange = { confirmOwnerEmail = it; transferError = null },
                        label = { Text("تأكيد البريد الإلكتروني") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (transferError != null) {
                        Text(transferError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val a = newOwnerEmail.trim().lowercase()
                        val b = confirmOwnerEmail.trim().lowercase()
                        when {
                            !a.contains("@") || a.length < 4 -> transferError = "أدخل بريداً إلكترونياً صحيحاً"
                            a != b -> transferError = "البريدان غير متطابقين"
                            a == config.owner_email.lowercase() -> transferError = "هذا البريد هو المالك الحالي بالفعل"
                            else -> onTransferOwnership(a) { showTransferDialog = false }
                        }
                    },
                ) {
                    Text("تأكيد النقل النهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) { Text("إلغاء") }
            },
        )
    }
}
