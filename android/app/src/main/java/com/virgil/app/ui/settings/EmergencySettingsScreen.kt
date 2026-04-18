package com.virgil.app.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.virgil.app.R
import com.virgil.app.data.EmergencyContact
import com.virgil.app.service.EmergencyDispatcher
import com.virgil.app.ui.permissions.SoundPreviewRow
import com.virgil.app.ui.permissions.permissionsSection
import com.virgil.app.ui.permissions.rememberPermissionSectionState
import com.virgil.app.ui.permissions.rememberSoundPreviewController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySettingsScreen(
    contacts: List<EmergencyContact>,
    smsMessageOverride: String?,
    appLanguageCode: String?,
    sleepStartHour: Int,
    sleepEndHour: Int,
    onSaveContacts: (List<EmergencyContact>) -> Unit,
    onSaveMessage: (String?) -> Unit,
    onSaveAppLanguage: (String?) -> Unit,
    onSaveSleepHours: (Int, Int) -> Unit,
) {
    val editableContacts = remember(contacts) { mutableStateListOf(*contacts.toTypedArray()) }
    var editingMessage by remember(smsMessageOverride) {
        mutableStateOf(smsMessageOverride.orEmpty())
    }
    var editingSleepStart by remember(sleepStartHour) { mutableStateOf(sleepStartHour.toString()) }
    var editingSleepEnd by remember(sleepEndHour) { mutableStateOf(sleepEndHour.toString()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    val permissionState = rememberPermissionSectionState()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_contacts_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_contacts_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(editableContacts) { index, contact ->
                ContactCard(
                    contact = contact,
                    onTogglePrimary = {
                        for (i in editableContacts.indices) {
                            editableContacts[i] = editableContacts[i].copy(
                                isPrimary = i == index
                            )
                        }
                        onSaveContacts(editableContacts.toList())
                    },
                    onLanguageChange = { newCode ->
                        editableContacts[index] = contact.copy(languageCode = newCode)
                        onSaveContacts(editableContacts.toList())
                    },
                    onDelete = {
                        editableContacts.removeAt(index)
                        onSaveContacts(editableContacts.toList())
                    },
                )
            }

            item {
                if (editableContacts.isEmpty()) {
                    EmptyContactsHint()
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_contact_add),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.testing_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.testing_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showTestDialog = true },
                    enabled = editableContacts.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        stringResource(R.string.home_send_test_button),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.testing_sounds_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val soundController = rememberSoundPreviewController()
                SoundPreviewRow(
                    title = stringResource(R.string.sound_ring_title),
                    description = stringResource(R.string.sound_ring_desc),
                    soundKey = "ring",
                    playing = soundController.playing,
                    onStart = soundController.start,
                    onStop = soundController.stop,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SoundPreviewRow(
                    title = stringResource(R.string.sound_siren_title),
                    description = stringResource(R.string.sound_siren_desc),
                    soundKey = "siren",
                    playing = soundController.playing,
                    onStart = soundController.start,
                    onStop = soundController.stop,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SoundPreviewRow(
                    title = stringResource(R.string.sound_dismiss_title),
                    description = stringResource(R.string.sound_dismiss_desc),
                    soundKey = "dismiss",
                    playing = soundController.playing,
                    onStart = soundController.start,
                    onStop = soundController.stop,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SoundPreviewRow(
                    title = stringResource(R.string.sound_sent_title),
                    description = stringResource(R.string.sound_sent_desc),
                    soundKey = "sent",
                    playing = soundController.playing,
                    onStart = soundController.start,
                    onStop = soundController.stop,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SoundPreviewRow(
                    title = stringResource(R.string.sound_failure_title),
                    description = stringResource(R.string.sound_failure_desc),
                    soundKey = "failure",
                    playing = soundController.playing,
                    onStart = soundController.start,
                    onStop = soundController.stop,
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.testing_fall_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_fall_test_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_quiet_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_quiet_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = editingSleepStart,
                        onValueChange = { input ->
                            editingSleepStart = input.filter(Char::isDigit).take(2)
                        },
                        label = { Text(stringResource(R.string.settings_quiet_start)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editingSleepEnd,
                        onValueChange = { input ->
                            editingSleepEnd = input.filter(Char::isDigit).take(2)
                        },
                        label = { Text(stringResource(R.string.settings_quiet_end)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    val s = editingSleepStart.toIntOrNull()
                        ?.coerceIn(0, 23) ?: sleepStartHour
                    val e = editingSleepEnd.toIntOrNull()
                        ?.coerceIn(0, 23) ?: sleepEndHour
                    onSaveSleepHours(s, e)
                }) {
                    Text(stringResource(R.string.settings_quiet_save))
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_message_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_message_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editingMessage,
                    onValueChange = { editingMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onSaveMessage(editingMessage.takeIf { it.isNotBlank() }) }) {
                    Text(stringResource(R.string.settings_message_save))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
            permissionsSection(state = permissionState, activity = activity)

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_language_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LanguageDropdown(
                    selectedCode = appLanguageCode,
                    systemLabel = stringResource(R.string.settings_language_system),
                    onSelect = onSaveAppLanguage,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, phone, languageCode, sendHeadsUp ->
                val isPrimary = editableContacts.isEmpty()
                val newContact = EmergencyContact(
                    name = name,
                    phone = phone,
                    isPrimary = isPrimary,
                    languageCode = languageCode,
                )
                editableContacts.add(newContact)
                onSaveContacts(editableContacts.toList())
                showAddDialog = false
                if (sendHeadsUp) launchIntroSms(context, newContact)
            },
        )
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text(stringResource(R.string.test_confirm_title)) },
            text = {
                Text(stringResource(R.string.test_confirm_body, editableContacts.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    showTestDialog = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.test_sent_toast),
                        Toast.LENGTH_SHORT,
                    ).show()
                    EmergencyDispatcher(context).dispatch(
                        contacts = editableContacts.toList(),
                        customTemplate = null,
                        isTest = true,
                    ) { result ->
                        val resId = if (result.locationAvailable) {
                            R.string.test_sent_with_location
                        } else {
                            R.string.test_sent_no_location
                        }
                        Toast.makeText(
                            context,
                            context.getString(resId),
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

@Composable
private fun EmptyContactsHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_contacts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContactCard(
    contact: EmergencyContact,
    onTogglePrimary: () -> Unit,
    onLanguageChange: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePrimary) {
                    Icon(
                        imageVector = if (contact.isPrimary) Icons.Filled.Star
                            else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(
                            if (contact.isPrimary) R.string.settings_contact_primary
                            else R.string.settings_contact_set_primary
                        ),
                        tint = if (contact.isPrimary) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(0.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        contact.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.settings_contact_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_contact_language_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            LanguageDropdown(
                selectedCode = contact.languageCode,
                systemLabel = stringResource(R.string.settings_contact_language_system),
                onSelect = onLanguageChange,
            )
        }
    }
}
