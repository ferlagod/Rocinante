/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil, pero
 * SIN GARANTÍA ALGUNA; ni siquiera la garantía implícita
 * MERCANTIL o de APTITUD PARA UN PROPÓSITO DETERMINADO.
 * Consulte los detalles de la Licencia Pública General GNU para obtener
 * una información más detallada.
 *
 * Debería haber recibido una copia de la Licencia Pública General GNU
 * junto a este programa.
 * En caso contrario, consulte <https://www.gnu.org/licenses/>.
 */
package com.ferlagod.rocinante.ui.screens.notifications

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
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
    onItemClicked: (NotificationUiItem) -> Unit
) {
    val viewModel: NotificationsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
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
                        viewModel.clearAllNotifications(api) { result ->
                            isClearing = false
                            val message = if (result.success) {
                                context.getString(R.string.notifications_cleared)
                            } else {
                                context.getString(R.string.error_generic, result.detail)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                                NotificationItemCard(item = notif, onItemClicked = onItemClicked)
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
    onItemClicked: (NotificationUiItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onItemClicked(item)
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

/**
 * Diálogo que muestra el detalle completo de una notificación.
 * Permite leer todo el contenido y ofrece un botón para abrir el enlace original en el navegador.
 */
@Composable
fun NotificationDetailDialog(
    item: NotificationUiItem,
    onDismiss: () -> Unit,
    onUrlClicked: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.actorAvatarUrl != null) {
                    AsyncImage(
                        model = item.actorAvatarUrl,
                        contentDescription = item.actorName,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                }
                Column {
                    Text(text = item.actorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = item.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val parsedContent = HtmlCompat.fromHtml(item.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                Text(
                    text = parsedContent,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.book_close)) // "Cerrar"
            }
        }
    )
}
