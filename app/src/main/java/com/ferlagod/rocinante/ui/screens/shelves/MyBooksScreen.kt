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
package com.ferlagod.rocinante.ui.screens.shelves

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import coil.compose.AsyncImage
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.api.ShelfBookItem
import com.ferlagod.rocinante.utils.BookWyrmUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip

import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons

data class ShelfUiItem(
    val slug: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * Pantalla que muestra y permite interactuar con los estantes personales del usuario 
 * (por leer, leyendo, leídos). Actúa como punto de entrada antes de ver el detalle de cada estante.
 *
 * @param instanceUrl URL base de la instancia de BookWyrm.
 * @param username Nombre del usuario autenticado.
 * @param cookie Token de sesión para autenticar llamadas a la API.
 * @param api Instancia opcional de [BookWyrmApi]. Si se provee, se reutiliza para eficiencia.
 * @param onNavigateToSettings Acción a ejecutar cuando se solicita navegar a los ajustes desde la pantalla de estantes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBooksScreen(
    instanceUrl: String,
    username: String,
    cookie: String,
    api: BookWyrmApi? = null,
    onNavigateToSettings: () -> Unit
) {
    val shelves = listOf(
        ShelfUiItem("to-read", stringResource(R.string.shelf_to_read_title), stringResource(R.string.shelf_to_read_desc), Icons.Default.BookmarkBorder),
        ShelfUiItem("reading", stringResource(R.string.shelf_reading_title), stringResource(R.string.shelf_reading_desc), Icons.AutoMirrored.Filled.MenuBook),
        ShelfUiItem("read", stringResource(R.string.shelf_read_title), stringResource(R.string.shelf_read_desc), Icons.Default.CheckCircle)
    )

    var selectedShelf by remember { mutableStateOf<ShelfUiItem?>(null) }

    if (selectedShelf == null) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.shelf_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.shelf_screen_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(shelves) { shelf ->
                OutlinedCard(
                    onClick = { selectedShelf = shelf },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = shelf.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = shelf.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = shelf.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else {
        selectedShelf?.let { shelf ->
            ShelfNativeDetailScreen(
                instanceUrl = instanceUrl,
                username = username,
                cookie = cookie,
                sharedApi = api,
                shelf = shelf,
                onBack = { selectedShelf = null },
                onNavigateToSettings = onNavigateToSettings
            )
        }
    }
}

@Composable
fun ShelfNativeDetailScreen(
    instanceUrl: String,
    username: String,
    cookie: String,
    sharedApi: BookWyrmApi? = null,
    shelf: ShelfUiItem,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val api = remember(instanceUrl, cookie) {
        sharedApi ?: NetworkClient.createAuthenticatedApi(instanceUrl, cookie)
    }

    var books by remember { mutableStateOf<List<ShelfBookItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPaginating by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(1) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isNetworkRefreshed by remember { mutableStateOf(false) }

    var selectedBookDetails by remember { mutableStateOf<com.ferlagod.rocinante.data.api.BookWyrmBookDetails?>(null) }
    var selectedBookReviews by remember { mutableStateOf<List<com.ferlagod.rocinante.data.api.ActivityPubActivity>>(emptyList()) }
    var fallbackCoverUrl by remember { mutableStateOf("") }
    var activeBookUrl by remember { mutableStateOf("") }
    var isLoadingDetails by remember { mutableStateOf(false) }

    var showTimePicker by remember { mutableStateOf(false) }

    val settingsPreferences = remember { com.ferlagod.rocinante.data.local.SettingsPreferences(context) }
    val settingsState by settingsPreferences.settingsFlow.collectAsState(initial = com.ferlagod.rocinante.data.local.SettingsData())

    val dataCache = remember(context) { com.ferlagod.rocinante.data.local.TimelineCache(context) }

    LaunchedEffect(shelf.slug, refreshTrigger, currentPage) {
        if (currentPage == 1) {
            isNetworkRefreshed = false
            val cachedBooks = dataCache.loadShelfBooks(shelf.slug)
            if (cachedBooks != null && books.isEmpty()) {
                books = cachedBooks
                isLoading = false
            } else if (books.isEmpty()) {
                isLoading = true
            }
        } else {
            isPaginating = true
        }
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val cleanUser = username.removePrefix("@").trim()
            val shelfJsonUrl = "${baseUrl}user/$cleanUser/shelf/${shelf.slug}.json?page=$currentPage"

            val response = api.getShelfData(shelfJsonUrl)
            val fetchedItems = response.orderedItems ?: emptyList()
            if (fetchedItems.isEmpty() || fetchedItems.size < 10) {
                hasMorePages = false
            } else {
                hasMorePages = true
            }
            
            if (currentPage == 1) {
                books = fetchedItems
                dataCache.saveShelfBooks(shelf.slug, fetchedItems)
                isNetworkRefreshed = true
            } else {
                books = books + fetchedItems
            }
            errorMessage = null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (books.isEmpty()) {
                errorMessage = context.getString(R.string.error_network, e.message)
            }
            hasMorePages = false
        } finally {
            isLoading = false
            isPaginating = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.shelf_back))
            }
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else if (isLoading && currentPage == 1) {
            ShelfSkeletonLoader()
        } else if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.shelf_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoadingDetails) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                }

                if (shelf.slug == "reading") {
                    item {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.reminder_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (settingsState.reminderEnabled) {
                                    Text(
                                        text = stringResource(R.string.reminder_active_at, String.format("%02d:%02d", settingsState.reminderHour, settingsState.reminderMinute)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = { showTimePicker = true }) {
                                        Text(stringResource(R.string.reminder_modify_time))
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.reminder_inactive_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(onClick = onNavigateToSettings) {
                                        Text(stringResource(R.string.reminder_go_to_settings))
                                    }
                                }
                            }
                        }
                    }
                }

                items(books.size) { index ->
                    val book = books[index]
                    if (index >= books.size - 5 && !isLoading && !isPaginating && hasMorePages && isNetworkRefreshed) {
                        LaunchedEffect(index) {
                            currentPage++
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isLoadingDetails) return@clickable
                                val bookUrl = book.id
                                if (!bookUrl.isNullOrEmpty()) {
                                    if (settingsState.openLinksExternally) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(bookUrl))
                                        context.startActivity(intent)
                                    } else {
                                        activeBookUrl = bookUrl
                                        isLoadingDetails = true
                                        coroutineScope.launch {
                                            try {
                                                val detailsUrl = BookWyrmUtils.ensureJsonUrl(bookUrl)
                                                selectedBookDetails = api.getBookDetails(detailsUrl)

                                                fallbackCoverUrl = book.cover?.url ?: ""

                                                val baseBookUrl = detailsUrl.removeSuffix(".json").trimEnd('/')
                                                try {
                                                    selectedBookReviews = NetworkClient.scrapeBookReviews(api, baseBookUrl)
                                                } catch (_: Exception) {
                                                    selectedBookReviews = emptyList()
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                errorMessage = context.getString(R.string.error_details_load, e.message)
                                            } finally {
                                                isLoadingDetails = false
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val coverUrl = book.cover?.url
                            if (!coverUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = stringResource(R.string.book_cover_desc),
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(105.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title ?: stringResource(R.string.book_no_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedBookDetails?.let { details ->
        com.ferlagod.rocinante.ui.components.BookDetailsDialog(
            bookDetails = details,
            reviews = selectedBookReviews,
            activeBookKey = activeBookUrl,
            fallbackCoverUrl = fallbackCoverUrl,
            currentShelf = shelf.slug,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = {
                selectedBookDetails = null
                selectedBookReviews = emptyList()
            },
            onShelved = { refreshTrigger++ }
        )
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            initialHour = settingsState.reminderHour,
            initialMinute = settingsState.reminderMinute,
            onConfirm = { hour, minute ->
                coroutineScope.launch {
                    settingsPreferences.setReminderTime(hour, minute)
                    com.ferlagod.rocinante.workers.ReminderManager.schedule(context, hour, minute)
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.settings_logout_confirm_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_logout_cancel))
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reminder_dialog_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                TimePicker(state = state)
            }
        }
    )
}

@Composable
fun ShelfSkeletonLoader() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(100.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(LocalContentColor.current.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(LocalContentColor.current.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(16.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(LocalContentColor.current.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(24.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(LocalContentColor.current.copy(alpha = alpha))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(24.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(LocalContentColor.current.copy(alpha = alpha))
                            )
                        }
                    }
                }
            }
        }
    }
}