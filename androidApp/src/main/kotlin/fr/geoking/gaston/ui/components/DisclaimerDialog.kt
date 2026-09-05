package fr.geoking.gaston.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import fr.geoking.gaston.R

@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Not dismissible by clicking outside */ },
        title = {
            Text(text = stringResource(id = R.string.disclaimer_title))
        },
        text = {
            Text(text = stringResource(id = R.string.disclaimer_content))
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                modifier = Modifier.testTag("disclaimer_accept_btn")
            ) {
                Text(text = stringResource(id = R.string.disclaimer_accept))
            }
        }
    )
}
