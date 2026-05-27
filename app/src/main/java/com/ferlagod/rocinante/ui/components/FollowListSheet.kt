/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU General Public License versión 3 (GPLv3).
 */
package com.ferlagod.rocinante.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.model.FollowUserItem
import com.ferlagod.rocinante.ui.screens.home.FollowListDirection
import com.ferlagod.rocinante.ui.screens.home.FollowListViewModel
import com.ferlagod.rocinante.ui.screens.home.FollowListViewModelFactory

/**
 * ModalBottomSheet que muestra la lista de seguidores o siguiendo
 * con la opción de seguir / dejar de seguir a cada usuario.
 *
 * @param direction si es la lista de seguidores o de seguidos
 * @param instanceUrl URL base de la instancia (con o sin trailing slash)
 * @param username username limpio del usuario actual
 * @param api instancia autenticada de BookWyrmApi
 * @param onDismiss callback al cerrar el sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListSheet(
    direction: FollowListDirection,
    instanceUrl: String,
    username: String,
    api: BookWyrmApi,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cache = remember(context) { com.ferlagod.rocinante.data.local.FollowListCache(context) }
    val factory = remember(api, cache) { FollowListViewModelFactory(api, cache) }
    val viewModel: FollowListViewModel = viewModel(
        key = "follow_list_${direction.name}",
        factory = factory
    )
    val uiState by viewModel.uiState.collectAsState()

    val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
    val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
    val cleanUser = username.removePrefix("@").trim()

    var selectedActorUrl by remember { mutableStateOf<String?>(null) }
    val selectedUser = uiState.users.find { it.actorUrl == selectedActorUrl }

    LaunchedEffect(direction) {
        viewModel.load(baseUrl, cleanUser, direction)
    }

    // Mostrar error en Toast sin cerrar el sheet
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    val title = stringResource(
        if (direction == FollowListDirection.FOLLOWERS) R.string.follow_list_followers_title
        else R.string.follow_list_following_title
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // ── Título ──────────────────────────────────────────────────────
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when {
                uiState.isLoading -> {
                    FollowListSkeletonLoader()
                }

                uiState.users.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.follow_list_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.load(baseUrl, cleanUser, direction, forceRefresh = true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                        items(uiState.users, key = { it.actorUrl }) { user ->
                            FollowUserRow(
                                user = user,
                                isPending = user.handle in uiState.pendingHandles,
                                onRowClick = { selectedActorUrl = user.actorUrl },
                                onFollowClick = {
                                    if (user.isFollowedByMe) {
                                        viewModel.unfollow(user.actorUrl, user.handle)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.unfollow_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        viewModel.follow(user.actorUrl, user.handle)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.follow_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            }
        }
    }

    selectedUser?.let { user ->
        AlertDialog(
            onDismissRequest = { selectedActorUrl = null },
            confirmButton = {
                TextButton(onClick = { selectedActorUrl = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Column {
                        Text(text = user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = user.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            },
            text = {
                Column {
                    val summaryText = com.ferlagod.rocinante.utils.HtmlUtils.stripHtml(user.summary)
                    if (summaryText.isNotBlank()) {
                        Text(text = summaryText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    val isPending = user.handle in uiState.pendingHandles
                    if (isPending) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        if (user.isFollowedByMe) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.unfollow(user.actorUrl, user.handle)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.follow_btn_unfollow))
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.follow(user.actorUrl, user.handle)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.follow_btn_follow))
                            }
                        }
                    }
                }
            }
        )
    }
}

/**
 * Fila individual que representa a un usuario dentro de la lista de seguimiento.
 * Permite visualizar el perfil básico y ofrece un botón de acción rápida para seguir o dejar de seguir.
 *
 * @param user Datos del perfil del usuario a renderizar.
 * @param isPending Indica si hay una petición de red en curso para este usuario en particular.
 * @param onFollowClick Acción ejecutada al pulsar el botón principal (seguir/unfollow).
 * @param onRowClick Acción ejecutada al pulsar en cualquier otra zona de la fila para ver detalles.
 */
@Composable
private fun FollowUserRow(
    user: FollowUserItem,
    isPending: Boolean,
    onFollowClick: () -> Unit,
    onRowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = stringResource(R.string.profile_avatar_desc),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // Nombre + handle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = user.handle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1
            )
        }

        // Botón Follow / Siguiendo
        if (isPending) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            if (user.isFollowedByMe) {
                OutlinedButton(
                    onClick = onFollowClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.follow_btn_unfollow))
                }
            } else {
                Button(
                    onClick = onFollowClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.follow_btn_follow))
                }
            }
        }
    }
}

@Composable
fun FollowListSkeletonLoader() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(LocalContentColor.current.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(LocalContentColor.current.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(LocalContentColor.current.copy(alpha = alpha))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(LocalContentColor.current.copy(alpha = alpha))
                )
            }
        }
    }
}
