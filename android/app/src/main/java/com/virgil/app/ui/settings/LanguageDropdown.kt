package com.virgil.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.virgil.app.data.SupportedLocale

/**
 * Reusable language picker. [selectedCode] is the currently chosen IETF code or
 * null to mean "follow the app / system default", labelled with [systemLabel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    selectedCode: String?,
    systemLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLocale = SupportedLocale.fromCode(selectedCode)
    val currentLabel = currentLocale?.let { stringResource(it.displayNameRes) } ?: systemLabel

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(systemLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            SupportedLocale.entries.forEach { locale ->
                DropdownMenuItem(
                    text = { Text(stringResource(locale.displayNameRes)) },
                    onClick = {
                        onSelect(locale.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
