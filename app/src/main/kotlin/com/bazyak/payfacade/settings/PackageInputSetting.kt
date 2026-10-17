package com.bazyak.payfacade.settings

import android.app.RemoteInput
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.input.RemoteInputIntentHelper
import com.bazyak.payfacade.R
import com.bazyak.payfacade.common.OwnActivityLaunchTracker

private const val INPUT_KEY = "package_name"

/** A Java-style package name: dot-separated identifiers, at least two segments. */
private val PACKAGE_NAME = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

/**
 * Lets the user type an app's package name, via the system text input screen - the watch's own
 * keyboard, handwriting and voice, rather than an in-app text field that would be cramped on a
 * round screen.
 *
 * The value is checked before it is saved: a malformed name would simply never match, and this
 * app's own package would be ignored by the service anyway, so both are rejected with a message
 * instead of being stored silently.
 *
 * Once a package is set the row is highlighted, like a switch that is on. Without the
 * accessibility service it is unavailable altogether.
 */
@Composable
fun PackageInputSetting(
    title: String,
    value: String,
    iconRes: Int,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val inputLabel = stringResource(R.string.input_package_label)
    val invalidMessage = stringResource(R.string.toast_invalid_package)
    val ownMessage = stringResource(R.string.toast_own_package)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(INPUT_KEY)?.toString()?.trim()
            ?: return@rememberLauncherForActivityResult

        when {
            text.isEmpty() -> onValueChange("")
            text == context.packageName ->
                Toast.makeText(context, ownMessage, Toast.LENGTH_SHORT).show()
            !PACKAGE_NAME.matches(text) ->
                Toast.makeText(context, invalidMessage, Toast.LENGTH_SHORT).show()
            else -> onValueChange(text)
        }
    }

    ActionSetting(
        title = title,
        subtitle = value.ifEmpty { stringResource(R.string.state_not_set) },
        iconRes = iconRes,
        enabled = enabled,
        highlighted = value.isNotEmpty(),
        onClick = {
            // Leaving for the system input screen must not count as leaving the app.
            OwnActivityLaunchTracker.isLaunchingOwnActivity = true
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(
                intent, listOf(RemoteInput.Builder(INPUT_KEY).setLabel(inputLabel).build())
            )
            launcher.launch(intent)
        },
    )
}
