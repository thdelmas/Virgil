package com.virgil.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.virgil.app.R
import com.virgil.app.data.EmergencyContact

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.settings_contact_add),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
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

            item {
                Spacer(modifier = Modifier.height(8.dp))
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

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, phone, languageCode ->
                val isPrimary = editableContacts.isEmpty()
                editableContacts.add(
                    EmergencyContact(
                        name = name,
                        phone = phone,
                        isPrimary = isPrimary,
                        languageCode = languageCode,
                    )
                )
                onSaveContacts(editableContacts.toList())
                showAddDialog = false
            },
        )
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
