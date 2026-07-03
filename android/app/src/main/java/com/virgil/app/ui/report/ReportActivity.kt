package com.virgil.app.ui.report

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virgil.app.BuildConfig
import com.virgil.app.R
import com.virgil.app.data.AppLocale
import com.virgil.app.data.FallCandidateTrace
import com.virgil.app.data.FalseAlarmLog
import com.virgil.app.data.FalseAlarmSnapshot
import com.virgil.app.service.FalseAlarmReportPrompt
import com.virgil.app.ui.theme.VirgilTheme
import java.util.Locale
import java.util.TimeZone

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
        val trace = if (type == Type.FALSE_ALARM) {
            FallCandidateTrace.readTail(this, TRACE_LINES_IN_REPORT)
        } else {
            null
        }

        setContent {
            VirgilTheme {
                ReportScreen(
                    type = type,
                    snapshot = snapshot,
                    trace = trace,
                    onSend = { description, when_, includeDiagnostics ->
                        launchMail(type, description, when_, includeDiagnostics, snapshot, trace)
                    },
                    onCopy = { description, when_, includeDiagnostics ->
                        copyReport(type, description, when_, includeDiagnostics, snapshot, trace)
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
        trace: String?,
    ) {
        val recipient = getString(R.string.report_email_recipient)
        val subject = getString(subjectRes(type))
        val body = buildReportBody(this, type, description, whenLabel, includeDiagnostics, snapshot, trace)
        // Encode subject + body into the mailto URI query. Some mail apps
        // (Gmail, most notably) ignore EXTRA_SUBJECT / EXTRA_TEXT and only
        // parse the URI; embedding the fields directly guarantees the
        // compose screen opens with every field populated, so the user
        // only has to tap Send.
        val mailto = "mailto:" + recipient +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body)
        val sendTo = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(mailto)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(sendTo, getString(R.string.report_email_chooser_title))
        try {
            startActivity(chooser)
            if (type == Type.FALSE_ALARM) FalseAlarmLog.clear(this)
            finish()
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.report_email_no_mail_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyReport(
        type: Type,
        description: String,
        whenLabel: String?,
        includeDiagnostics: Boolean,
        snapshot: FalseAlarmSnapshot?,
        trace: String?,
    ) {
        val recipient = getString(R.string.report_email_recipient)
        val subject = getString(subjectRes(type))
        val body = buildReportBody(this, type, description, whenLabel, includeDiagnostics, snapshot, trace)
        // Prepend subject + recipient so the user can paste the whole
        // thing into any chat, note, or webmail compose window without
        // losing context about where it's meant to go.
        val clipText = buildString {
            appendLine("To: $recipient")
            appendLine("Subject: $subject")
            appendLine()
            append(body)
        }
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Virgil report", clipText))
        Toast.makeText(this, R.string.report_copied_toast, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_TYPE = "report_type"
        const val TRACE_LINES_IN_REPORT = 30

        fun intent(context: Context, type: Type): Intent =
            Intent(context, ReportActivity::class.java)
                .putExtra(EXTRA_TYPE, type.name)
    }
}

private fun subjectRes(type: ReportActivity.Type): Int = when (type) {
    ReportActivity.Type.FALSE_ALARM -> R.string.report_email_subject_false_alarm
    ReportActivity.Type.MISSED_FALL -> R.string.report_email_subject_missed_fall
}

private fun prefixRes(type: ReportActivity.Type): Int = when (type) {
    ReportActivity.Type.FALSE_ALARM -> R.string.report_email_prefix_false_alarm
    ReportActivity.Type.MISSED_FALL -> R.string.report_email_prefix_missed_fall
}

private fun deviceOnlyDiagnostics(): String = buildString {
    appendLine("Report schema: v2")
    appendLine("App version: ${BuildConfig.VERSION_NAME}")
    appendLine("Device: ${Build.MANUFACTURER ?: "unknown"} ${Build.MODEL ?: "unknown"}")
    appendLine("Android API: ${Build.VERSION.SDK_INT}")
    appendLine("App locale: ${Locale.getDefault().toLanguageTag()}")
    appendLine("Timezone: ${TimeZone.getDefault().id}")
}

/**
 * Builds the exact email body that gets either mailed or copied. Kept as
 * a pure function of its inputs so the on-screen preview is byte-for-byte
 * identical to what leaves the device.
 */
internal fun buildReportBody(
    context: Context,
    type: ReportActivity.Type,
    description: String,
    whenLabel: String?,
    includeDiagnostics: Boolean,
    snapshot: FalseAlarmSnapshot?,
    trace: String? = null,
): String = buildString {
    append(context.getString(prefixRes(type)))
    append("\n\n")
    if (!whenLabel.isNullOrBlank()) {
        append(context.getString(R.string.report_when_label, whenLabel))
        append("\n\n")
    }
    if (description.isNotBlank()) {
        append(description.trim())
        append("\n\n")
    }
    if (includeDiagnostics) {
        append(context.getString(R.string.report_email_diagnostics_header))
        append("\n")
        append(snapshot?.toReportText() ?: deviceOnlyDiagnostics())
        if (!trace.isNullOrBlank()) {
            append("\n")
            append(trace)
            append("\n")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScreen(
    type: ReportActivity.Type,
    snapshot: FalseAlarmSnapshot?,
    trace: String?,
    onSend: (description: String, whenLabel: String?, includeDiagnostics: Boolean) -> Unit,
    onCopy: (description: String, whenLabel: String?, includeDiagnostics: Boolean) -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    // Default to checked — the snapshot carries no PII (no location, no
    // contacts, no identifiers). A pre-ticked consent box is still
    // explicit consent: the checkbox is labelled, visible, and the user
    // can uncheck it before sending if they'd rather keep the data local.
    var includeDiagnostics by remember { mutableStateOf(true) }
    var whenChoice by remember { mutableStateOf<Int?>(null) }
    var previewExpanded by remember { mutableStateOf(false) }

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

            ReportPreview(
                expanded = previewExpanded,
                onToggle = { previewExpanded = !previewExpanded },
                body = buildReportBody(
                    context, type, description, whenLabel, includeDiagnostics, snapshot, trace,
                ),
            )

            // Missed-fall reports need the user's description to be useful
            // (there is no snapshot to carry signal). False-alarm reports
            // carry a snapshot, so diagnostics-only sends are allowed.
            val canSend = when (type) {
                ReportActivity.Type.FALSE_ALARM ->
                    description.isNotBlank() || includeDiagnostics
                ReportActivity.Type.MISSED_FALL ->
                    description.isNotBlank() && whenChoice != null
            }

            Button(
                onClick = { onSend(description, whenLabel, includeDiagnostics) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSend,
            ) {
                Text(stringResource(R.string.report_send_button))
            }
            OutlinedButton(
                onClick = { onCopy(description, whenLabel, includeDiagnostics) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSend,
            ) {
                Text(stringResource(R.string.report_copy_button))
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
private fun ReportPreview(expanded: Boolean, onToggle: () -> Unit, body: String) {
    Column {
        TextButton(onClick = onToggle) {
            Text(
                stringResource(
                    if (expanded) R.string.report_preview_hide
                    else R.string.report_preview_show,
                ),
            )
        }
        if (expanded) {
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = body,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
