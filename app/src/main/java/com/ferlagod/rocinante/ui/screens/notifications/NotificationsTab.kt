package com.ferlagod.rocinante.ui.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Delete
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.model.NotificationUiItem

/**
 * Pestaña principal de notificaciones para la pantalla de inicio.
 * Muestra una lista de notificaciones con soporte para Pull-to-refresh y maneja
 * los diferentes estados de carga (Loading, Error, Success, Empty).
 *
 * @param api Cliente de BookWyrm configurado para la sesión actual.
 * @param instanceUrl URL base de la instancia.
 * @param onUrlClicked Callback ejecutado al hacer clic en un enlace (permalink) de notificación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTab(
    api: BookWyrmApi,
    instanceUrl: String,
    onUrlClicked: (String) -> Unit
) {
    val viewModel: NotificationsViewModel = viewModel(
        factory = NotificationsViewModelFactory(api, instanceUrl)
    )
    val state by viewModel.state.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val context = LocalContext.current
    var isClearing by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (state is NotificationsState.Success && (state as NotificationsState.Success).notifications.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        isClearing = true
                        viewModel.clearAllNotifications {
                            isClearing = false
                            Toast.makeText(context, R.string.notifications_cleared, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.clear_notifications)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            when (val s = state) {
                is NotificationsState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is NotificationsState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.error_generic, s.message), color = MaterialTheme.colorScheme.error)
                    }
                }
                is NotificationsState.Success -> {
                    if (s.notifications.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.notifications_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.notifications_empty_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(s.notifications, key = { it.id }) { notif ->
                                NotificationItemCard(item = notif, onUrlClicked = onUrlClicked)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Componente visual que representa una única tarjeta de notificación en la lista.
 * Interpreta el HTML y muestra el avatar del usuario, la fecha, y aplica un fondo
 * ligeramente tintado si la notificación no ha sido leída.
 *
 * @param item Datos de la notificación extraídos por scraping.
 * @param onUrlClicked Callback para abrir el permalink de la notificación en el navegador.
 */
@Composable
fun NotificationItemCard(
    item: NotificationUiItem,
    onUrlClicked: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                item.permalink?.let { onUrlClicked(it) }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (item.actorAvatarUrl != null) {
                AsyncImage(
                    model = item.actorAvatarUrl,
                    contentDescription = item.actorName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = null)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.actorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                val parsedContent = HtmlCompat.fromHtml(item.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                Text(
                    text = parsedContent,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }
        }
    }
}
