package fr.geoking.gaston.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.gaston.R
import fr.geoking.gaston.ui.dashboard.GastonTheme

/** Feedback after Settings → Check for updates when already up to date or on error. */
@Composable
fun UpdateCheckFeedbackDialog(
    isError: Boolean,
    errorMessage: String = "",
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isError) {
                    stringResource(R.string.update_check_error_title)
                } else {
                    stringResource(R.string.update_check_up_to_date)
                },
            )
        },
        text = if (isError && errorMessage.isNotEmpty()) {
            { Text(errorMessage) }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_check_ok))
            }
        },
    )
}

@Preview
@Composable
private fun UpdateCheckFeedbackUpToDatePreview() {
    GastonTheme {
        UpdateCheckFeedbackDialog(isError = false, onDismiss = {})
    }
}

@Preview
@Composable
private fun UpdateCheckFeedbackErrorPreview() {
    GastonTheme {
        UpdateCheckFeedbackDialog(
            isError = true,
            errorMessage = "API not available",
            onDismiss = {},
        )
    }
}
