package com.virgil.app.ui.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virgil.app.BuildConfig
import com.virgil.app.R
import com.virgil.app.data.AppLocale
import com.virgil.app.data.FalseAlarmLog
import com.virgil.app.data.FalseAlarmSnapshot
import com.virgil.app.service.FalseAlarmReportPrompt
import com.virgil.app.ui.theme.VirgilTheme

/**
 * Unified report flow — two types:
 *
 * - [Type.FALSE_ALARM]: Virgil tripped when it shouldn't have. Includes
 *   the on-device snapshot taken at cancel time (phase reached, peak
 *   acceleration, trigger type, timestamps).
 * - [Type.MISSED_FALL]: Virgil didn't trip when it should have. No
 *   snapshot exists, but the user can describe what happened and
 *   optionally attach device info so we can investigate hardware or
 *   algorithm gaps.
 *
 * Both hand the report to the user's own mail app via `mailto:` — we
 * never transmit anything ourselves. Description text lives in memory
 * only; the snapshot file is cleared after send.
 */
class ReportActivity : ComponentActivity() {

    enum class Type { FALSE_ALARM, MISSED_FALL }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FalseAlarmReportPrompt.cancel(this)

        val type = runCatching {
            Type.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: Type.FALSE_ALARM.name)
        }.getOrElse { Type.FALSE_ALARM }

        val snapshot = if (type == Type.FALSE_ALARM) FalseAlarmLog.read(this) else null

        setContent {
            VirgilTheme {
                ReportScreen(
                    type = type,
                    snapshot = snapshot,
                    onSend = { description, when_, includeDiagnostics ->
                        launchMail(type, description, when_, includeDiagnostics, snapshot)
                    },
                    onDiscard = {
                        if (type == Type.FALSE_ALARM) FalseAlarmLog.clear(this)
                        finish()
                    },
                )
            }
        }
    }

    private fun launchMail(
        type: Type,
        description: String,
        whenLabel: String?,
        includeDiagnostics: Boolean,
        snapshot: FalseAlarmSnapshot?,
    ) {
        val subjectRes = when (type) {
            Type.FALSE_ALARM -> R.string.report_email_subject_false_alarm
            Type.MISSED_FALL -> R.string.report_email_subject_missed_fall
        }
        val prefixRes = when (type) {
            Type.FALSE_ALARM -> R.string.report_email_prefix_false_alarm
            Type.MISSED_FALL -> R.string.report_email_prefix_missed_fall
        }

        val body = buildString {
            append(getString(prefixRes))
            append("\n\n")
            if (!whenLabel.isNullOrBlank()) {
                append(getString(R.string.report_when_label, whenLabel))
                append("\n\n")
            }
            if (description.isNotBlank()) {
                append(description.trim())
                append("\n\n")
            }
            if (includeDiagnostics) {
                append(getString(R.string.report_email_diagnostics_header))
                append("\n")
                append(snapshot?.toReportText() ?: deviceOnlyDiagnostics())
            }
        }
        val recipient = getString(R.string.report_email_recipient)
        val subject = getString(subjectRes)
        // Encode subject + body into the mailto URI query. Some mail apps
        // (Gmail, most notably) ignore EXTRA_SUBJECT / EXTRA_TEXT and only
        // parse the URI; embedding the fields directly guarantees the
        // compose screen opens with every field populated, so the user
        // only has to tap Send.
        val mailto = "mailto:" + recipient +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(mailto)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            startActivity(intent)
            if (type == Type.FALSE_ALARM) FalseAlarmLog.clear(this)
            finish()
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.report_email_no_mail_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun deviceOnlyDiagnostics(): String = buildString {
        appendLine("App version: ${BuildConfig.VERSION_NAME}")
        appendLine("Device: ${Build.MANUFACTURER ?: "unknown"} ${Build.MODEL ?: "unknown"}")
        appendLine("Android API: ${Build.VERSION.SDK_INT}")
    }

    companion object {
        const val EXTRA_TYPE = "report_type"

        fun intent(context: Context, type: Type): Intent =
            Intent(context, ReportActivity::class.java)
                .putExtra(EXTRA_TYPE, type.name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScreen(
    type: ReportActivity.Type,
    snapshot: FalseAlarmSnapshot?,
    onSend: (description: String, whenLabel: String?, includeDiagnostics: Boolean) -> Unit,
    onDiscard: () -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(false) }
    var whenChoice by remember { mutableStateOf<Int?>(null) }

    val titleRes = when (type) {
        ReportActivity.Type.FALSE_ALARM -> R.string.report_screen_title_false_alarm
        ReportActivity.Type.MISSED_FALL -> R.string.report_screen_title_missed_fall
    }
    val introRes = when (type) {
        ReportActivity.Type.FALSE_ALARM -> R.string.report_intro_false_alarm
        ReportActivity.Type.MISSED_FALL -> R.string.report_intro_missed_fall
    }
    val descriptionHintRes = when (type) {
        ReportActivity.Type.FALSE_ALARM -> R.string.report_description_hint_false_alarm
        ReportActivity.Type.MISSED_FALL -> R.string.report_description_hint_missed_fall
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(titleRes)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(introRes), fontSize = 16.sp)

            if (type == ReportActivity.Type.MISSED_FALL) {
                WhenSelector(selected = whenChoice, onSelect = { whenChoice = it })
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.report_description_label)) },
                placeholder = { Text(stringResource(descriptionHintRes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                minLines = 6,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.report_consent_heading),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (type) {
                    ReportActivity.Type.FALSE_ALARM -> stringResource(R.string.report_consent_body_false_alarm)
                    ReportActivity.Type.MISSED_FALL -> stringResource(R.string.report_consent_body_missed_fall)
                },
                fontSize = 14.sp,
            )

            if (snapshot != null) DiagnosticsPreview(snapshot)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = includeDiagnostics,
                    onCheckedChange = { includeDiagnostics = it },
                )
                Text(
                    text = stringResource(R.string.report_consent_checkbox),
                    fontSize = 15.sp,
                )
            }

            val whenLabel = whenChoice?.let { stringResource(it) }
            val canSend = description.isNotBlank() &&
                (type == ReportActivity.Type.FALSE_ALARM || whenChoice != null)

            Button(
                onClick = { onSend(description, whenLabel, includeDiagnostics) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSend,
            ) {
                Text(stringResource(R.string.report_send_button))
            }
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.report_discard_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenSelector(selected: Int?, onSelect: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.report_when_heading),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val options = listOf(
        R.string.report_when_just_now,
        R.string.report_when_today,
        R.string.report_when_yesterday,
        R.string.report_when_older,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { res ->
            FilterChip(
                selected = selected == res,
                onClick = { onSelect(res) },
                label = { Text(stringResource(res)) },
            )
        }
    }
}

@Composable
private fun DiagnosticsPreview(snapshot: FalseAlarmSnapshot) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = snapshot.toReportText(),
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp),
        )
    }
}
