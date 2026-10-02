package fr.geoking.gaston.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.notification.InAppNotification
import fr.geoking.gaston.feature.notification.InAppNotificationCenter
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@Composable
fun NotificationBellAction(
    center: InAppNotificationCenter = koinInject(),
) {
    val context = LocalContext.current
    val notifications by center.notifications.collectAsState()
    val unreadCount by center.unreadCount.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = {
                expanded = true
                center.markAllRead()
            },
            modifier = Modifier.testTag("dashboard_notifications_btn")
        ) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge { Text(if (unreadCount > 9) "9+" else unreadCount.toString()) }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_notifications),
                    contentDescription = stringResource(R.string.cd_notifications),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
        ) {
            Text(
                text = stringResource(R.string.notification_center_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (notifications.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.notification_center_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { expanded = false },
                    enabled = false,
                )
            } else {
                notifications.take(20).forEach { item ->
                    DropdownMenuItem(
                        text = { NotificationMenuRow(item) },
                        onClick = {
                            expanded = false
                            center.markRead(item.id)
                            val url = item.actionUrl
                            if (!url.isNullOrBlank()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        },
                    )
                }
                TextButton(
                    onClick = {
                        center.clear()
                        expanded = false
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Text(stringResource(R.string.notification_center_clear))
                }
            }
        }
    }
}

@Composable
private fun NotificationMenuRow(item: InAppNotification) {
    val time = remember(item.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.timestampMs))
    }
    Column {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
