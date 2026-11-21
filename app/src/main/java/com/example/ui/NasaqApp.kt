package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOut
import com.example.data.NasaqRepository
import com.example.data.SystemConfig
import com.example.data.UiState
import com.example.ui.components.NasaqBrandLogo
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CustomerBookingScreen
import com.example.ui.screens.CustomerBookingsScreen
import com.example.ui.screens.OwnerCustomersScreen
import com.example.ui.screens.OwnerDashboardScreen
import com.example.ui.screens.OwnerScheduleScreen
import com.example.ui.screens.OwnerServicesScreen
import com.example.ui.screens.OwnerSettingsScreen
import com.example.util.BookingUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private enum class CustomerTab(val label: String, val icon: ImageVector) {
    BOOK("حجز موعد", Icons.Default.CalendarMonth),
    MY_BOOKINGS("حجوزاتي", Icons.AutoMirrored.Filled.EventNote),
}

private enum class OwnerTab(val label: String, val icon: ImageVector) {
    DASHBOARD("الطلبات", Icons.Default.Dashboard),
    SERVICES("الخدمات", Icons.Default.ContentCut),
    SCHEDULE("الجدول", Icons.Default.Schedule),
    CUSTOMERS("العملاء", Icons.Default.Group),
    SETTINGS("الإعدادات", Icons.Default.Settings),
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun NasaqApp(auth: FirebaseAuth = Firebase.auth) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val currentUser by auth.authStateFlow()
            .collectAsStateWithLifecycle(initialValue = auth.currentUser)
        val user = currentUser

        if (user == null) {
            AuthScreen(onAuthSuccess = { })
        } else {
            key(user.uid) {
                AuthenticatedNasaqContent(user = user)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedNasaqContent(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: NasaqViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NasaqViewModel(
                    repository = NasaqRepository(db),
                    currentUserId = user.uid,
                    currentUserEmail = user.email ?: "",
                )
            }
        },
    )

    LaunchedEffect(user.uid) {
        viewModel.syncSignedInUser(user)
    }

    LaunchedEffect(Unit) {
        viewModel.actionMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val configState by viewModel.systemConfigState.collectAsStateWithLifecycle()
    val profileState by viewModel.currentUserProfileState.collectAsStateWithLifecycle()
    val servicesState by viewModel.servicesState.collectAsStateWithLifecycle()
    val myAppointmentsState by viewModel.myAppointmentsState.collectAsStateWithLifecycle()
    val allAppointmentsState by viewModel.allAppointmentsState.collectAsStateWithLifecycle()
    val allUsersState by viewModel.allUsersState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val slotLocks by viewModel.slotLocksForSelectedDate.collectAsStateWithLifecycle()
    val isOwner by viewModel.isOwner.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val config = (configState as? UiState.Success)?.data ?: SystemConfig()
    val userProfile = (profileState as? UiState.Success)?.data
    val servicesList = (servicesState as? UiState.Success)?.data ?: emptyList()

    var ownerPreviewAsCustomer by remember { mutableStateOf(false) }
    var customerTab by remember { mutableStateOf(CustomerTab.BOOK) }
    var ownerTab by remember { mutableStateOf(OwnerTab.DASHBOARD) }

    val activeOwnerMode = isOwner && !ownerPreviewAsCustomer

    BackHandler(
        enabled = (activeOwnerMode && ownerTab != OwnerTab.DASHBOARD) ||
            (!activeOwnerMode && customerTab != CustomerTab.BOOK)
    ) {
        if (activeOwnerMode) {
            ownerTab = OwnerTab.DASHBOARD
        } else {
            customerTab = CustomerTab.BOOK
        }
    }

    val onSignOutClick = {
        signOut(credentialManager = credentialManager, scope = scope)
    }

    if (userProfile?.blocked == true && !isOwner) {
        BlockedAccountScreen(
            businessPhone = config.business_phone,
            onSignOut = onSignOutClick,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NasaqBrandLogo(size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = if (activeOwnerMode) "لوحة الإدارة" else stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if (isOwner) {
                            FilterChip(
                                selected = ownerPreviewAsCustomer,
                                onClick = { ownerPreviewAsCustomer = !ownerPreviewAsCustomer },
                                label = {
                                    Text(
                                        text = if (ownerPreviewAsCustomer) "لوحة الإدارة" else "معاينة الحجز",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (ownerPreviewAsCustomer) Icons.Default.Dashboard else Icons.Default.Preview,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        IconButton(
                            onClick = onSignOutClick,
                            modifier = Modifier.testTag("sign_out_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.sign_out),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        if (activeOwnerMode) {
                            OwnerTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = ownerTab == tab,
                                    onClick = { ownerTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_owner_${tab.name.lowercase()}"),
                                )
                            }
                        } else {
                            CustomerTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = customerTab == tab,
                                    onClick = { customerTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_customer_${tab.name.lowercase()}"),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Spacer(Modifier.height(12.dp))
                        if (activeOwnerMode) {
                            OwnerTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = ownerTab == tab,
                                    onClick = { ownerTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                        } else {
                            CustomerTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = customerTab == tab,
                                    onClick = { customerTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (activeOwnerMode) {
                        when (ownerTab) {
                            OwnerTab.DASHBOARD -> OwnerDashboardScreen(
                                config = config,
                                appointmentsState = allAppointmentsState,
                                services = servicesList,
                                isBusy = isBusy,
                                onUpdateStatus = viewModel::updateAppointmentStatus,
                                onSaveAdminNotes = viewModel::updateAppointmentAdminNotes,
                                onMarkReminderSent = viewModel::markReminderSent,
                                onDeleteAppointment = viewModel::deleteAppointment,
                                onCreateManualBooking = viewModel::createOwnerManualBooking,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                            )

                            OwnerTab.SERVICES -> OwnerServicesScreen(
                                servicesState = servicesState,
                                currency = config.currency,
                                isBusy = isBusy,
                                onSaveService = viewModel::saveService,
                                onDeleteService = viewModel::deleteService,
                            )

                            OwnerTab.SCHEDULE -> OwnerScheduleScreen(
                                config = config,
                                isBusy = isBusy,
                                onSaveConfig = viewModel::updateSystemConfig,
                            )

                            OwnerTab.CUSTOMERS -> OwnerCustomersScreen(
                                usersState = allUsersState,
                                businessName = config.business_name,
                                currency = config.currency,
                                countryCode = config.country_code,
                                isBusy = isBusy,
                                onUpdateCustomer = viewModel::updateCustomerAdmin,
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                },
                            )

                            OwnerTab.SETTINGS -> OwnerSettingsScreen(
                                config = config,
                                isBusy = isBusy,
                                onSaveConfig = { updated -> viewModel.updateSystemConfig(updated) },
                                onTransferOwnership = { newEmail, onDone ->
                                    viewModel.transferOwnership(config, newEmail, onDone)
                                },
                                onSignOut = onSignOutClick,
                            )
                        }
                    } else {
                        when (customerTab) {
                            CustomerTab.BOOK -> CustomerBookingScreen(
                                config = config,
                                userProfile = userProfile,
                                servicesState = servicesState,
                                selectedDate = selectedDate,
                                slotLocks = slotLocks,
                                isBusy = isBusy,
                                onSelectDate = viewModel::selectDate,
                                onCreateBooking = viewModel::createCustomerBooking,
                                onNavigateToMyBookings = { customerTab = CustomerTab.MY_BOOKINGS },
                            )

                            CustomerTab.MY_BOOKINGS -> CustomerBookingsScreen(
                                appointmentsState = myAppointmentsState,
                                currency = config.currency,
                                isBusy = isBusy,
                                onCancelAppointment = viewModel::cancelMyAppointment,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedAccountScreen(
    businessPhone: String,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(R.string.blocked_account_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.blocked_account_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (businessPhone.isNotBlank()) {
                    Button(
                        onClick = { BookingUtils.openDialer(context, businessPhone) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("الاتصال بالحلاق ($businessPhone)")
                    }
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
    }
}
