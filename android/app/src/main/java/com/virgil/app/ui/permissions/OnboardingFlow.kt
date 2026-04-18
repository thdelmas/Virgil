package com.virgil.app.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.Context
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.virgil.app.R
import com.virgil.app.permissions.PermissionChecker
import com.virgil.app.permissions.PermissionMonitor
import com.virgil.app.permissions.VirgilPermission

private sealed interface OnboardingStep {
    data class Permission(val permission: VirgilPermission) : OnboardingStep
    object Contact : OnboardingStep
    object FallInfo : OnboardingStep
    object CheckInInfo : OnboardingStep
    object Sounds : OnboardingStep
}

@Composable
fun OnboardingFlow(
    hasContacts: Boolean,
    onSaveContact: (name: String, phone: String) -> Unit,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val steps: List<OnboardingStep> = remember {
        buildList {
            VirgilPermission.applicable().filter { it.mandatory }
                .forEach { add(OnboardingStep.Permission(it)) }
            if (!hasContacts) {
                add(OnboardingStep.Contact)
                add(OnboardingStep.FallInfo)
                add(OnboardingStep.CheckInInfo)
                add(OnboardingStep.Sounds)
            }
        }
    }

    val hasPermissionSteps = remember {
        steps.any { it is OnboardingStep.Permission }
    }

    var index by remember { mutableIntStateOf(if (hasPermissionSteps) -1 else 0) }

    fun advance() {
        var next = index + 1
        while (next in steps.indices) {
            val s = steps[next]
            if (s is OnboardingStep.Permission &&
                PermissionChecker.isGranted(context, s.permission)
            ) next++ else break
        }
        index = next
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PermissionMonitor.check(context)
                val current = steps.getOrNull(index)
                if (current is OnboardingStep.Permission &&
                    PermissionChecker.isGranted(context, current.permission)
                ) advance()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PermissionMonitor.check(context)
        if (granted) advance()
    }

    AnimatedContent(
        targetState = index,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding-step",
    ) { currentIndex ->
        when {
            currentIndex == -1 -> WelcomeStep(onBegin = { advance() })
            currentIndex >= steps.size -> DoneStep(onFinish = onComplete)
            else -> when (val step = steps[currentIndex]) {
                is OnboardingStep.Contact -> ContactStep(
                    stepNumber = currentIndex + 1,
                    totalSteps = steps.size,
                    onSave = { name, phone ->
                        onSaveContact(name, phone)
                        advance()
                    },
                    onSkip = { advance() },
                )
                is OnboardingStep.Permission -> PermissionStep(
                    permission = step.permission,
                    stepNumber = currentIndex + 1,
                    totalSteps = steps.size,
                    onTurnOn = {
                        when (step.permission.kind) {
                            VirgilPermission.Kind.Runtime ->
                                step.permission.androidName?.let { runtimeLauncher.launch(it) }
                            VirgilPermission.Kind.ExactAlarm ->
                                activity?.let(::openExactAlarmSettings)
                            VirgilPermission.Kind.FullScreenIntent ->
                                activity?.let(::openFullScreenIntentSettings)
                        }
                    },
                    onSkip = { advance() },
                )
                is OnboardingStep.FallInfo -> FallInfoStep(
                    stepNumber = currentIndex + 1,
                    totalSteps = steps.size,
                    onContinue = { advance() },
                )
                is OnboardingStep.CheckInInfo -> CheckInInfoStep(
                    stepNumber = currentIndex + 1,
                    totalSteps = steps.size,
                    onContinue = { advance() },
                )
                is OnboardingStep.Sounds -> SoundsStep(
                    stepNumber = currentIndex + 1,
                    totalSteps = steps.size,
                    onContinue = { advance() },
                )
            }
        }
    }

    LaunchedEffect(Unit) { PermissionMonitor.check(context) }
}

@Composable
private fun WelcomeStep(onBegin: () -> Unit) {
    OnboardingScaffold(
        progress = null,
        content = {
            BigIcon(Icons.Filled.Shield)
            Spacer(Modifier.height(40.dp))
            Text(
                stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        },
        primaryAction = { PrimaryButton(stringResource(R.string.onboarding_begin), onBegin) },
    )
}

@Composable
private fun PermissionStep(
    permission: VirgilPermission,
    stepNumber: Int,
    totalSteps: Int,
    onTurnOn: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingScaffold(
        progress = stepNumber to totalSteps,
        content = {
            BigIcon(iconFor(permission))
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(permission.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(permission.rationaleRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        },
        primaryAction = {
            PrimaryButton(stringResource(R.string.onboarding_turn_on), onTurnOn)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onSkip) {
                Text(
                    stringResource(R.string.onboarding_not_now),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
    )
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    OnboardingScaffold(
        progress = null,
        content = {
            BigIcon(Icons.Filled.CheckCircle)
            Spacer(Modifier.height(40.dp))
            Text(
                stringResource(R.string.onboarding_done_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.onboarding_done_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        },
        primaryAction = { PrimaryButton(stringResource(R.string.onboarding_continue), onFinish) },
    )
}

@Composable
private fun ContactStep(
    stepNumber: Int,
    totalSteps: Int,
    onSave: (String, String) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        readPhoneContact(context, uri)?.let { (n, p) ->
            name = n
            phone = p
        }
    }
    val canSave = name.isNotBlank() && phone.isNotBlank()

    OnboardingScaffold(
        progress = stepNumber to totalSteps,
        content = {
            BigIcon(Icons.Filled.Favorite)
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.onboarding_contact_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onboarding_contact_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    picker.launch(
                        Intent(Intent.ACTION_PICK).apply {
                            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Filled.Contacts, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.onboarding_pick_contacts))
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.dialog_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.dialog_phone)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        primaryAction = {
            Button(
                onClick = { if (canSave) onSave(name.trim(), phone.trim()) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(
                    stringResource(R.string.onboarding_save),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onSkip) {
                Text(
                    stringResource(R.string.onboarding_not_now),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
    )
}

private fun readPhoneContact(context: Context, uri: Uri): Pair<String, String>? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null
    }
}

private fun iconFor(permission: VirgilPermission): ImageVector = when (permission) {
    VirgilPermission.SMS -> Icons.Filled.Sms
    VirgilPermission.LOCATION -> Icons.Filled.LocationOn
    VirgilPermission.NOTIFICATIONS -> Icons.Filled.Notifications
    VirgilPermission.EXACT_ALARM -> Icons.Filled.Alarm
    VirgilPermission.FULL_SCREEN_INTENT -> Icons.Filled.ScreenLockPortrait
    VirgilPermission.CALL -> Icons.Filled.Phone
}

private fun openExactAlarmSettings(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    activity.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
    )
}

private fun openFullScreenIntentSettings(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    activity.startActivity(
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
    )
}
