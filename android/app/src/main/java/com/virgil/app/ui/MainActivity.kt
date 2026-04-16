package com.virgil.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.service.DeadManSwitchService
import com.virgil.app.service.FallDetectionService
import com.virgil.app.ui.settings.EmergencySettingsScreen
import com.virgil.app.ui.theme.VirgilTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: EmergencyPreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled — services check individually */ }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = EmergencyPreferences(this)

        requestPermissions()

        setContent {
            VirgilTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        val fallEnabled by prefs.fallDetectionEnabled
                            .collectAsState(initial = false)
                        val dmsEnabled by prefs.deadManSwitchEnabled
                            .collectAsState(initial = false)
                        val contacts by prefs.contacts
                            .collectAsState(initial = emptyList())
                        val intervalHours by prefs.dmsIntervalHours
                            .collectAsState(initial = 6L)

                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Virgil") },
                                    actions = {
                                        IconButton(onClick = {
                                            navController.navigate("settings")
                                        }) {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = "Settings",
                                            )
                                        }
                                    },
                                )
                            },
                        ) { padding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = 24.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(modifier = Modifier.height(24.dp))

                                // --- Fall Detection ---
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.height(64.dp),
                                    tint = if (fallEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = if (fallEnabled) "Fall Detection Active"
                                        else "Fall Detection Off",
                                    style = MaterialTheme.typography.headlineSmall,
                                )

                                if (contacts.isEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add emergency contacts in Settings to enable",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Enable", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.padding(8.dp))
                                    Switch(
                                        checked = fallEnabled,
                                        onCheckedChange = { newValue ->
                                            scope.launch {
                                                prefs.setFallDetectionEnabled(newValue)
                                            }
                                            if (newValue) startFallDetection()
                                            else stopFallDetection()
                                        },
                                        enabled = contacts.isNotEmpty(),
                                    )
                                }

                                Spacer(modifier = Modifier.height(48.dp))

                                // --- Dead Man's Switch ---
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.height(64.dp),
                                    tint = if (dmsEnabled) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = if (dmsEnabled) "Check-In Active"
                                        else "Check-In Off",
                                    style = MaterialTheme.typography.headlineSmall,
                                )

                                if (dmsEnabled) {
                                    Text(
                                        text = "Every ${intervalHours}h during waking hours",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                            .copy(alpha = 0.6f),
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Enable", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.padding(8.dp))
                                    Switch(
                                        checked = dmsEnabled,
                                        onCheckedChange = { newValue ->
                                            scope.launch {
                                                prefs.setDeadManSwitchEnabled(newValue)
                                            }
                                            if (newValue) startDeadManSwitch()
                                            else stopDeadManSwitch()
                                        },
                                        enabled = contacts.isNotEmpty(),
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "${contacts.size} emergency contact(s) configured",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }

                    composable("settings") {
                        val contacts by prefs.contacts.collectAsState(initial = emptyList())
                        val message by prefs.smsMessage.collectAsState(initial = "")

                        EmergencySettingsScreen(
                            contacts = contacts,
                            smsMessage = message,
                            onSaveContacts = { scope.launch { prefs.saveContacts(it) } },
                            onSaveMessage = { scope.launch { prefs.saveSmsMessage(it) } },
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
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

    private fun startDeadManSwitch() {
        startForegroundService(Intent(this, DeadManSwitchService::class.java))
    }

    private fun stopDeadManSwitch() {
        startService(
            Intent(this, DeadManSwitchService::class.java).apply {
                action = DeadManSwitchService.ACTION_STOP
            }
        )
    }
}
