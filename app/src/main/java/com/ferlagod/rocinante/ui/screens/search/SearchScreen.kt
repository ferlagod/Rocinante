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
package com.ferlagod.rocinante.ui.screens.search

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.ActivityPubActivity
import com.ferlagod.rocinante.data.api.BookSearchResult
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmBookDetails
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.utils.BookWyrmUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * Pantalla interactiva que permite a los usuarios buscar obras en su instancia de BookWyrm,
 * facilitando tanto la búsqueda por texto convencional como mediante escáner de códigos de barras (ISBN).
 *
 * @param instanceUrl Dirección del servidor (instancia) a consultar de forma prioritaria.
 * @param cookie Credencial de sesión para autenticar búsquedas.
 * @param api Cliente de red pre-configurado opcional.
 * @param modifier Modificador de diseño para ajustar la disposición.
 */
enum class SearchMode { BOOKS, USERS }

/**
 * Pantalla principal de búsqueda, permite buscar libros y usuarios en la instancia local
 * o en el ecosistema federado mediante el cliente de BookWyrm.
 *
 * @param instanceUrl URL base de la instancia.
 * @param cookie Cookie de sesión autenticada.
 * @param api Cliente API para peticiones a BookWyrm.
 * @param modifier Modificador visual para el layout.
 */
@Composable
fun SearchScreen(
    instanceUrl: String,
    cookie: String,
    api: BookWyrmApi? = null,
    modifier: Modifier = Modifier
) {
    val resolvedApi = remember(instanceUrl, cookie) {
        api ?: NetworkClient.createAuthenticatedApi(instanceUrl, cookie)
    }
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<BookSearchResult>>(emptyList()) }
    var userSearchResults by remember { mutableStateOf<List<com.ferlagod.rocinante.data.api.SuggestedUser>>(emptyList()) }
    var searchMode by remember { mutableStateOf(SearchMode.BOOKS) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    var selectedSuggestedUser by remember { mutableStateOf<com.ferlagod.rocinante.data.api.SuggestedUser?>(null) }
    var selectedUserProfile by remember { mutableStateOf<com.ferlagod.rocinante.data.api.BookWyrmProfile?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedBookDetails by remember { mutableStateOf<BookWyrmBookDetails?>(null) }
    var selectedBookReviews by remember { mutableStateOf<List<ActivityPubActivity>>(emptyList()) }
    var activeBookKey by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val performSearch: () -> Unit = {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            keyboardController?.hide()
            coroutineScope.launch {
                try {
                    val repo = com.ferlagod.rocinante.data.repository.BookWyrmRepository(resolvedApi)
                    if (searchMode == SearchMode.BOOKS) {
                        searchResults = repo.searchBooksScraped(searchQuery, instanceUrl)
                        userSearchResults = emptyList()
                        errorMessage = null
                        if (searchResults.isEmpty()) {
                            errorMessage = context.getString(R.string.search_books_empty)
                        }
                    } else {
                        userSearchResults = repo.searchUsersScraped(searchQuery, instanceUrl)
                        searchResults = emptyList()
                        errorMessage = null
                        if (userSearchResults.isEmpty()) {
                            errorMessage = context.getString(R.string.search_users_empty)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errorMessage = context.getString(R.string.search_error, e.message)
                } finally {
                    isSearching = false
                }
            }
        }
    }

    val barcodeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            searchQuery = result.contents
            searchMode = SearchMode.BOOKS
            performSearch()
        }
    }

    val settingsPreferences = remember { com.ferlagod.rocinante.data.local.SettingsPreferences(context) }
    val settingsState by settingsPreferences.settingsFlow.collectAsState(initial = com.ferlagod.rocinante.data.local.SettingsData())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorMessage != null) {
            Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (errorMessage == context.getString(R.string.search_books_empty)) {
                OutlinedButton(
                    onClick = {
                        val cleanInstance = instanceUrl.removePrefix("http://").removePrefix("https://").trimEnd('/')
                        val url = "https://$cleanInstance/create-book"
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_book_manually_btn))
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(if (searchMode == SearchMode.BOOKS) stringResource(R.string.search_hint) else stringResource(R.string.search_hint_users)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { performSearch() }
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(selectedTabIndex = searchMode.ordinal, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = searchMode == SearchMode.BOOKS,
                onClick = { 
                    searchMode = SearchMode.BOOKS
                    if (searchQuery.isNotBlank() && searchResults.isEmpty()) performSearch()
                },
                text = { Text(stringResource(R.string.search_tab_books)) }
            )
            Tab(
                selected = searchMode == SearchMode.USERS,
                onClick = { 
                    searchMode = SearchMode.USERS
                    if (searchQuery.isNotBlank() && userSearchResults.isEmpty()) performSearch()
                },
                text = { Text(stringResource(R.string.search_tab_users)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { performSearch() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (searchMode == SearchMode.BOOKS) stringResource(R.string.search_btn) else stringResource(R.string.search_btn_users))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (searchMode == SearchMode.BOOKS) {
            OutlinedButton(
                onClick = {
                    val options = ScanOptions()
                    options.setPrompt(context.getString(R.string.search_barcode_prompt))
                    options.setBeepEnabled(false)
                    options.setOrientationLocked(false)
                    barcodeLauncher.launch(options)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.QrCodeScanner,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.search_barcode_btn))
            }
        } else {
            Text(
                text = stringResource(R.string.search_users_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoadingDetails) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isSearching) {
            SearchSkeletonLoader()
        } else if (searchMode == SearchMode.BOOKS && searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { book ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isLoadingDetails) return@clickable
                                val bookKey = book.key
                                if (!bookKey.isNullOrEmpty()) {
                                    if (settingsState.openLinksExternally) {
                                        val url = if (bookKey.startsWith("http")) bookKey else "https://$instanceUrl/book/$bookKey"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    } else {
                                        isLoadingDetails = true
                                        coroutineScope.launch {
                                            try {
                                                var finalBookKey = bookKey
                                                if (book.isRemote && !book.remoteId.isNullOrEmpty()) {
                                                    val localUrl = NetworkClient.resolveLocalBookUrl(resolvedApi, book.remoteId)
                                                    if (localUrl != null) {
                                                        val potentialKey = localUrl.substringAfter("/book/").substringBefore("/")
                                                        if (potentialKey.isNotBlank()) finalBookKey = potentialKey
                                                    }
                                                }
                                                
                                                activeBookKey = finalBookKey
                                                val detailsUrl = if (finalBookKey.startsWith("http")) {
                                                    BookWyrmUtils.ensureJsonUrl(finalBookKey)
                                                } else {
                                                    BookWyrmUtils.ensureJsonUrl("https://$instanceUrl/book/$finalBookKey")
                                                }
                                                selectedBookDetails = resolvedApi.getBookDetails(detailsUrl)

                                                val baseBookUrl = detailsUrl.removeSuffix(".json").trimEnd('/')
                                                try {
                                                    selectedBookReviews = NetworkClient.scrapeBookReviews(resolvedApi, baseBookUrl)
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
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!book.cover.isNullOrEmpty()) {
                                AsyncImage(
                                    model = book.cover,
                                    contentDescription = stringResource(R.string.book_cover_desc),
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(100.dp),
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
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.search_author, book.author ?: stringResource(R.string.search_author_unknown)),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (book.year != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.search_year, book.year),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (searchMode == SearchMode.USERS && userSearchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userSearchResults) { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isLoadingDetails) return@clickable
                                isLoadingDetails = true
                                coroutineScope.launch {
                                    try {
                                        val profileUrlJson = com.ferlagod.rocinante.utils.BookWyrmUtils.ensureJsonUrl(user.profileUrl)
                                        val profile = resolvedApi.getFullUserProfile(profileUrlJson)
                                        selectedSuggestedUser = user
                                        selectedUserProfile = profile
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        errorMessage = context.getString(R.string.error_details_load, e.message)
                                    } finally {
                                        isLoadingDetails = false
                                    }
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (user.avatarUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.name.takeIf { it.isNotBlank() }?.firstOrNull()?.toString()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.name.ifBlank { "Sin nombre" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = user.handle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
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
            activeBookKey = activeBookKey,
            api = resolvedApi,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = {
                selectedBookDetails = null
                selectedBookReviews = emptyList()
            }
        )
    }

    if (selectedUserProfile != null && selectedSuggestedUser != null) {
        UserProfileDialog(
            profile = selectedUserProfile!!,
            handle = selectedSuggestedUser!!.handle,
            api = resolvedApi,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = {
                selectedUserProfile = null
                selectedSuggestedUser = null
            }
        )
    }
}

/**
 * Diálogo emergente que muestra un resumen del perfil de un usuario cuando se selecciona en los resultados de búsqueda.
 * Proporciona información básica y acciones rápidas como Seguir/Dejar de seguir.
 *
 * @param user Perfil de usuario simplificado.
 * @param instanceUrl URL de la instancia activa.
 * @param api Cliente de la API para enviar acciones (follow/unfollow).
 * @param onDismiss Callback ejecutado para cerrar el diálogo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDialog(
    profile: com.ferlagod.rocinante.data.api.BookWyrmProfile,
    handle: String,
    api: BookWyrmApi,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    var isFollowing by remember { mutableStateOf(false) }
    var isFollowPending by remember { mutableStateOf(false) }

    val cleanHandle = handle.removePrefix("@")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.user_profile_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Avatar + User + Handle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val avatarUrl = profile.icon?.url
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = stringResource(R.string.profile_avatar_desc),
                            modifier = Modifier
                                .size(64.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Person,
                                    contentDescription = stringResource(R.string.profile_default_avatar_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name ?: stringResource(R.string.user_profile_no_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Botón para seguir / dejar de seguir al usuario
                val buttonModifier = Modifier.fillMaxWidth()
                if (isFollowPending) {
                    Box(modifier = buttonModifier, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (isFollowing) {
                    OutlinedButton(
                        onClick = {
                            isFollowPending = true
                            coroutineScope.launch {
                                try {
                                    val response = api.unfollowUser(cleanHandle)
                                    if (response.isSuccessful || response.code() in 300..399) {
                                        isFollowing = false
                                        android.widget.Toast.makeText(context, context.getString(R.string.unfollow_success), android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, context.getString(R.string.error_server, response.code().toString()), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    android.widget.Toast.makeText(context, context.getString(R.string.error_network, e.message), android.widget.Toast.LENGTH_SHORT).show()
                                } finally {
                                    isFollowPending = false
                                }
                            }
                        },
                        modifier = buttonModifier
                    ) {
                        Text(stringResource(R.string.follow_btn_unfollow))
                    }
                } else {
                    Button(
                        onClick = {
                            isFollowPending = true
                            coroutineScope.launch {
                                try {
                                    val response = api.followUser(cleanHandle)
                                    if (response.isSuccessful || response.code() in 300..399) {
                                        isFollowing = true
                                        android.widget.Toast.makeText(context, context.getString(R.string.follow_success), android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, context.getString(R.string.error_server, response.code().toString()), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    android.widget.Toast.makeText(context, context.getString(R.string.error_network, e.message), android.widget.Toast.LENGTH_SHORT).show()
                                } finally {
                                    isFollowPending = false
                                }
                            }
                        },
                        modifier = buttonModifier
                    ) {
                        Text(stringResource(R.string.follow_btn_follow))
                    }
                }

                HorizontalDivider()

                val cleanSummary = com.ferlagod.rocinante.utils.HtmlUtils.stripHtml(profile.summary ?: "").trim()
                if (cleanSummary.isNotBlank()) {
                    Text(
                        text = cleanSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.user_profile_no_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.book_close))
            }
        }
    )
}

/**
 * Animación de esqueleto (Skeleton Loader) empleada mientras se cargan los resultados
 * de la búsqueda, proporcionando un feedback visual inmediato al usuario.
 */
@Composable
fun SearchSkeletonLoader() {
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
                                .fillMaxWidth(0.8f)
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(LocalContentColor.current.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(16.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(LocalContentColor.current.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(12.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(LocalContentColor.current.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}