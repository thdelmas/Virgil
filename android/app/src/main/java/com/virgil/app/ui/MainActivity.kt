package com.virgil.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.virgil.app.data.AppLocale
import com.virgil.app.data.EmergencyContact
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.data.InteractionTracker
import com.virgil.app.permissions.PermissionChecker
import com.virgil.app.permissions.PermissionMonitor
import com.virgil.app.R
import com.virgil.app.service.CheckInService
import com.virgil.app.service.EmergencyDispatcher
import com.virgil.app.service.FallDetectionService
import com.virgil.app.ui.permissions.OnboardingFlow
import com.virgil.app.ui.permissions.PermissionsScreen
import com.virgil.app.ui.settings.EmergencySettingsScreen
import com.virgil.app.ui.theme.VirgilTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var prefs: EmergencyPreferences

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        InteractionTracker.record(this)
        PermissionMonitor.check(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = EmergencyPreferences(this)

        val openPermissionsOnStart =
            intent?.getBooleanExtra(EXTRA_OPEN_PERMISSIONS, false) ?: false
        val startHasContacts = runBlocking { prefs.contacts.first().isNotEmpty() }
        val needsOnboarding = !openPermissionsOnStart &&
            (PermissionChecker.missingMandatory(this).isNotEmpty() || !startHasContacts)

        setContent {
            VirgilTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    when {
                        openPermissionsOnStart -> navController.navigate("permissions")
                        needsOnboarding -> navController.navigate("onboarding")
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        val fallEnabled by prefs.fallDetectionEnabled
                            .collectAsState(initial = false)
                        val checkInEnabled by prefs.checkInEnabled
                            .collectAsState(initial = false)
                        val contacts by prefs.contacts
                            .collectAsState(initial = emptyList())
                        val intervalHours by prefs.checkInIntervalHours
                            .collectAsState(initial = 6L)

                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text(stringResource(R.string.app_name)) },
                                    actions = {
                                        IconButton(onClick = {
                                            navController.navigate("permissions")
                                        }) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = stringResource(
                                                    R.string.home_top_permissions
                                                ),
                                            )
                                        }
                                        IconButton(onClick = {
                                            navController.navigate("settings")
                                        }) {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = stringResource(
                                                    R.string.home_top_settings
                                                ),
                                            )
                                        }
                                    },
                                )
                            },
                        ) { padding ->
                            if (contacts.isEmpty()) {
                                EmptyHome(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(padding),
                                    onAddContact = { navController.navigate("onboarding") },
                                )
                                return@Scaffold
                            }

                            val ctx = LocalContext.current
                            var showTestDialog by remember { mutableStateOf(false) }

                            HomeScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding),
                                fallEnabled = fallEnabled,
                                checkInEnabled = checkInEnabled,
                                contacts = contacts,
                                intervalHours = intervalHours,
                                onToggleFall = { newValue ->
                                    scope.launch { prefs.setFallDetectionEnabled(newValue) }
                                    if (newValue) startFallDetection() else stopFallDetection()
                                },
                                onToggleCheckIn = { newValue ->
                                    scope.launch { prefs.setCheckInEnabled(newValue) }
                                    if (newValue) startCheckIn() else stopCheckIn()
                                },
                                onTest = { showTestDialog = true },
                            )

                            if (showTestDialog) {
                                AlertDialog(
                                    onDismissRequest = { showTestDialog = false },
                                    title = {
                                        Text(stringResource(R.string.test_confirm_title))
                                    },
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.test_confirm_body,
                                                contacts.size,
                                            )
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showTestDialog = false
                                            Toast.makeText(
                                                ctx,
                                                ctx.getString(R.string.test_sent_toast),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            EmergencyDispatcher(ctx).dispatch(
                                                contacts = contacts,
                                                customTemplate = null,
                                                isTest = true,
                                            ) { locationAvailable ->
                                                val resId = if (locationAvailable) {
                                                    R.string.test_sent_with_location
                                                } else {
                                                    R.string.test_sent_no_location
                                                }
                                                Toast.makeText(
                                                    ctx,
                                                    ctx.getString(resId),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }) {
                                            Text(stringResource(R.string.test_confirm_send))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showTestDialog = false }) {
                                            Text(stringResource(R.string.test_confirm_cancel))
                                        }
                                    },
                                )
                            }
                        }
                    }

                    composable("settings") {
                        val contacts by prefs.contacts.collectAsState(initial = emptyList())
                        val customMessage by prefs.smsMessageOverride
                            .collectAsState(initial = null)
                        val sleepStart by prefs.checkInSleepStartHour
                            .collectAsState(initial = 23)
                        val sleepEnd by prefs.checkInSleepEndHour
                            .collectAsState(initial = 7)
                        val ctx = LocalContext.current
                        val currentLanguage = remember { AppLocale.read(ctx) }

                        EmergencySettingsScreen(
                            contacts = contacts,
                            smsMessageOverride = customMessage,
                            appLanguageCode = currentLanguage,
                            sleepStartHour = sleepStart,
                            sleepEndHour = sleepEnd,
                            onSaveContacts = { scope.launch { prefs.saveContacts(it) } },
                            onSaveMessage = { scope.launch { prefs.saveSmsMessage(it) } },
                            onSaveAppLanguage = { newCode ->
                                AppLocale.write(ctx, newCode)
                                recreate()
                            },
                            onSaveSleepHours = { s, e ->
                                scope.launch { prefs.setCheckInSleepHours(s, e) }
                            },
                        )
                    }

                    composable("permissions") {
                        PermissionsScreen(
                            onBack = if (navController.previousBackStackEntry != null) {
                                { navController.popBackStack() }
                            } else null,
                        )
                    }

                    composable("onboarding") {
                        OnboardingFlow(
                            hasContacts = startHasContacts,
                            onSaveContact = { name, phone ->
                                scope.launch {
                                    val existing = prefs.contacts.first()
                                    val newContact = EmergencyContact(
                                        name = name,
                                        phone = phone,
                                        isPrimary = existing.isEmpty(),
                                    )
                                    prefs.saveContacts(existing + newContact)
                                }
                            },
                            onComplete = {
                                navController.popBackStack("home", inclusive = false)
                            },
                        )
                    }
                }
            }
        }
    }


    private fun startFallDetection() {
        startForegroundService(Intent(this, FallDetectionService::class.java))
    }

    private fun stopFallDetection() {
        startService(
            Intent(this, FallDetectionService::class.java).apply {
                action = FallDetectionService.ACTION_STOP
            }
        )
    }

    private fun startCheckIn() {
        startForegroundService(Intent(this, CheckInService::class.java))
    }

    private fun stopCheckIn() {
        startService(
            Intent(this, CheckInService::class.java).apply {
                action = CheckInService.ACTION_STOP
            }
        )
    }

    companion object {
        const val EXTRA_OPEN_PERMISSIONS = "open_permissions"
    }
}

@androidx.compose.runtime.Composable
private fun EmptyHome(modifier: Modifier, onAddContact: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.height(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.empty_home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.empty_home_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.weight(1f))
        androidx.compose.material3.Button(
            onClick = onAddContact,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                stringResource(R.string.empty_home_add),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
