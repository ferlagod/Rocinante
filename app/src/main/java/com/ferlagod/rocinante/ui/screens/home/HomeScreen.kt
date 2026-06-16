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
@file:SuppressLint("LocalContextGetResourceValueCall")

package com.ferlagod.rocinante.ui.screens.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
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
import com.ferlagod.rocinante.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ferlagod.rocinante.ui.components.AppTopBar
import com.ferlagod.rocinante.ui.components.FollowListSheet
import com.ferlagod.rocinante.utils.HtmlUtils
import com.ferlagod.rocinante.ui.screens.shelves.MyBooksScreen
import com.ferlagod.rocinante.ui.screens.search.SearchScreen
import com.ferlagod.rocinante.data.api.BookWyrmProfile
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.api.editProfile
import com.ferlagod.rocinante.data.model.TimelineUiItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Pantalla principal (Home) de la aplicación.
 *
 * Implementa la estructura principal de navegación y muestra el contenido 
 * correspondiente a la pestaña activa (Actividad, Mis Libros, Búsqueda o Perfil).
 *
 * @param cookie Token de sesión para autenticación en la API.
 * @param instanceUrl URL base de la instancia del servidor a la que está conectado el usuario.
 * @param username Nombre de usuario autenticado localmente.
 * @param onSettingsClick Callback invocado al seleccionar el icono de configuración.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cookie: String,
    instanceUrl: String,
    username: String,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var showPostDialog by remember { mutableStateOf(false) }
    var selectedActivity by remember { mutableStateOf<TimelineUiItem?>(null) }
    var dialogBookDetails by remember { mutableStateOf<com.ferlagod.rocinante.data.api.BookWyrmBookDetails?>(null) }
    var dialogBookReviews by remember { mutableStateOf<List<com.ferlagod.rocinante.data.api.ActivityPubActivity>>(emptyList()) }
    var dialogBookKey by remember { mutableStateOf("") }
    var dialogCoverUrl by remember { mutableStateOf("") }

    val api = remember(instanceUrl, cookie) {
        NetworkClient.createAuthenticatedApi(instanceUrl, cookie)
    }

    val timelineCache = remember(context) {
        com.ferlagod.rocinante.data.local.TimelineCache(context)
    }

    // El factory se crea con una función lambda que crea el repositorio usando el profileCache
    // del ViewModel ya instanciado, de modo que el caché sobrevive a las recomposiciones.
    val factory = remember(api, timelineCache) {
        HomeViewModelFactory(api, timelineCache)
    }

    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(instanceUrl, username, cookie) {
        viewModel.load(instanceUrl, username, cookie)
    }

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab,
        pageCount = { 4 }
    )

    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(
                page = uiState.selectedTab,
                animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != uiState.selectedTab) {
            viewModel.selectTab(pagerState.currentPage)
        }
    }

    dialogBookDetails?.let { details ->
        com.ferlagod.rocinante.ui.components.BookDetailsDialog(
            bookDetails = details,
            reviews = dialogBookReviews,
            activeBookKey = dialogBookKey,
            fallbackCoverUrl = dialogCoverUrl,
            currentShelf = "reading",
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = {
                dialogBookDetails = null
                dialogBookReviews = emptyList()
            },
            onShelved = { viewModel.load(instanceUrl, username, cookie, forceRefresh = true) }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when (uiState.selectedTab) {
                    0 -> stringResource(R.string.nav_activity)
                    1 -> stringResource(R.string.nav_my_books)
                    2 -> stringResource(R.string.nav_search)
                    else -> stringResource(R.string.nav_profile)
                },
                onSettingsClick = onSettingsClick
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nav_activity)) },
                    label = { Text(stringResource(R.string.nav_activity)) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.nav_my_books)) }, // ICONO CORREGIDO
                    label = { Text(stringResource(R.string.nav_my_books)) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search)) },
                    label = { Text(stringResource(R.string.nav_search)) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.nav_profile)) },
                    label = { Text(stringResource(R.string.nav_profile)) }
                )
            }
        },
        floatingActionButton = {
            if (uiState.selectedTab == 0) {
                FloatingActionButton(onClick = { showPostDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.post_fab_desc))
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> ActivityTab(
                    modifier = Modifier.padding(paddingValues),
                    timeline = uiState.visibleTimeline,
                    profile = uiState.profile,
                    likedStatusIds = uiState.likedStatusIds,
                    isLoading = uiState.isLoading,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.load(instanceUrl, username, cookie, forceRefresh = true) },
                    onLikeClick = { item -> viewModel.toggleLike(item.objectId, instanceUrl) },
                    onLoadMore = { viewModel.loadMoreActivities() },
                    onItemClick = { selectedActivity = it },
                    api = api
                )

                1 -> Box(modifier = Modifier.padding(paddingValues)) {
                    MyBooksScreen(
                        instanceUrl = instanceUrl,
                        username = username,
                        cookie = cookie,
                        api = api,
                        onNavigateToSettings = onSettingsClick
                    )
                }

                2 -> SearchScreen(
                    instanceUrl = instanceUrl,
                    cookie = cookie,
                    api = api,
                    modifier = Modifier.padding(paddingValues)
                )

                3 -> ProfileTab(
                    modifier = Modifier.padding(paddingValues),
                    profile = uiState.profile,
                    username = username,
                    instanceUrl = instanceUrl,
                    cookie = cookie,
                    api = api,
                    onProfileUpdated = { newName, newSummary -> 
                        viewModel.updateProfileOptimistically(newName, newSummary)
                        viewModel.load(instanceUrl, username, cookie, forceRefresh = true) 
                    },
                    onFollowingIncremented = {
                        viewModel.incrementFollowingCount()
                    },
                    onFollowingDecremented = {
                        viewModel.decrementFollowingCount()
                    }
                )
            }
        }

        // Lógica del cuadro de diálogo para publicaciones
        if (showPostDialog) {
            var statusText by remember { mutableStateOf("") }
            var isSubmitting by remember { mutableStateOf(false) }

            val postSuccessMsg = stringResource(R.string.post_success)
            val postErrorFormat = stringResource(R.string.post_error, "%s")
            val serverErrorFormat = stringResource(R.string.profile_server_error, "%s")
            val sessionErrorFormat = stringResource(R.string.error_session_verification, "%s")

            AlertDialog(
                onDismissRequest = { if (!isSubmitting) showPostDialog = false },
                title = { Text(stringResource(R.string.post_dialog_title), fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = statusText,
                        onValueChange = { statusText = it },
                        label = { Text(stringResource(R.string.post_dialog_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        enabled = !isSubmitting,
                        maxLines = 10
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (statusText.isNotBlank()) {
                                isSubmitting = true
                                coroutineScope.launch {
                                    try {
                                        // Obtener el ID de usuario desde el HTML de la home
                                        val profileUrl = uiState.profile?.id
                                        val shelfUrl = if (profileUrl != null) "$profileUrl/books/to-read" else "${instanceUrl.trimEnd('/')}/"
                                        val htmlResponse = api.getRawHtmlResponse(shelfUrl)
                                        if (htmlResponse.isSuccessful) {
                                            val html = htmlResponse.body()?.string() ?: ""
                                            val userMatch = "name=[\"']user[\"'][^>]*?value=[\"'](\\d+)[\"']|value=[\"'](\\d+)[\"'][^>]*?name=[\"']user[\"']".toRegex(RegexOption.IGNORE_CASE).find(html)
                                            val fallbackMatch = "data-user-id=[\"'](\\d+)[\"']".toRegex(RegexOption.IGNORE_CASE).find(html)
                                            val userId = userMatch?.let { it.groups[1]?.value ?: it.groups[2]?.value } ?: fallbackMatch?.groups?.get(1)?.value
                                            if (userId != null) {
                                                val cookieJar = NetworkClient.lastOkHttpClient?.cookieJar as? com.ferlagod.rocinante.data.api.SessionCookieJar
                                                val csrfToken = cookieJar?.currentCsrfToken() ?: ""
                                                val response = api.createStatus(userId = userId, content = statusText, csrfToken = csrfToken)
                                                if (response.isSuccessful || response.code() == 302) {
                                                    showPostDialog = false
                                                    Toast.makeText(context, postSuccessMsg, Toast.LENGTH_SHORT).show()
                                                    viewModel.load(instanceUrl, username, cookie, forceRefresh = true)
                                                } else {
                                                    Toast.makeText(context, serverErrorFormat.format(response.code().toString()), Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, postErrorFormat.format("User ID missing from HTML"), Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, sessionErrorFormat.format(htmlResponse.code().toString()), Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        Toast.makeText(context, postErrorFormat.format(e.localizedMessage ?: "Network error"), Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        Text(if (isSubmitting) stringResource(R.string.post_btn_sending) else stringResource(R.string.post_btn_publish))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPostDialog = false },
                        enabled = !isSubmitting
                    ) {
                        Text(stringResource(R.string.post_btn_cancel))
                    }
                }
            )
        }
        
        selectedActivity?.let { activity ->
            val isLiked = uiState.likedStatusIds.contains(activity.objectId)
            ActivityDetailsDialog(
                item = activity,
                currentUserProfile = uiState.profile,
                username = username,
                instanceUrl = instanceUrl,
                api = api,
                isLiked = isLiked,
                onLikeClick = { viewModel.toggleLike(activity.objectId, instanceUrl) },
                onReplySubmit = { replyText, onResult ->
                    viewModel.replyToStatus(
                        statusUrl = activity.objectId,
                        instanceUrl = instanceUrl,
                        content = replyText,
                        onResult = { success ->
                            onResult(success)
                            if (success) {
                                selectedActivity = null
                                viewModel.load(instanceUrl, username, cookie, forceRefresh = true)
                            }
                        }
                    )
                },
                onDismiss = { selectedActivity = null },
                onBookClick = { bookUrl, coverUrl ->
                    if (bookUrl.isNotEmpty()) {
                        dialogBookKey = bookUrl
                        dialogCoverUrl = coverUrl ?: ""
                        coroutineScope.launch {
                            try {
                                val localUrl = com.ferlagod.rocinante.data.api.NetworkClient.resolveLocalBookUrl(api, bookUrl) ?: bookUrl
                                val bookId = com.ferlagod.rocinante.utils.BookWyrmUtils.extractBookId(localUrl)
                                val baseUrl = localUrl.substringBefore("/book/")
                                val detailsUrl = "$baseUrl/book/$bookId.json"
                                dialogBookDetails = api.getBookDetails(detailsUrl)
                                val baseBookUrl = detailsUrl.removeSuffix(".json").trimEnd('/')
                                try {
                                    dialogBookReviews = NetworkClient.scrapeBookReviews(api, baseBookUrl)
                                } catch (_: Exception) {
                                    dialogBookReviews = emptyList()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                android.widget.Toast.makeText(context, context.getString(R.string.error_details_load, e.message), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Diálogo para visualizar información detallada de una sugerencia de usuario
 * y facilitar la acción de seguirlo.
 *
 * @param suggestedUser Información preliminar del usuario sugerido.
 * @param api Cliente REST para obtener el perfil completo y efectuar el seguimiento.
 * @param instanceUrl URL de la instancia actual para resolver el handle completo del usuario.
 * @param onDismiss Callback invocado al descartar o cerrar el diálogo.
 * @param onFollowSuccess Callback invocado cuando el seguimiento se completa exitosamente.
 */
@Composable
fun SuggestedUserDialog(
    suggestedUser: com.ferlagod.rocinante.data.api.SuggestedUser,
    api: com.ferlagod.rocinante.data.api.BookWyrmApi,
    instanceUrl: String,
    onDismiss: () -> Unit,
    onFollowSuccess: () -> Unit
) {
    val context = LocalContext.current
    var fullProfile by remember { mutableStateOf<BookWyrmProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(suggestedUser.profileUrl) {
        isLoading = true
        try {
            val jsonUrl = if (suggestedUser.profileUrl.endsWith(".json")) suggestedUser.profileUrl else "${suggestedUser.profileUrl}.json"
            fullProfile = api.getFullUserProfile(jsonUrl)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.profile_who_to_follow), fontWeight = FontWeight.Bold)
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val profile = fullProfile
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val avatarToUse = profile?.icon?.url ?: suggestedUser.avatarUrl
                    if (avatarToUse.isNotEmpty()) {
                        AsyncImage(
                            model = avatarToUse,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                        )
                    }
                    
                    Text(
                        text = profile?.name ?: suggestedUser.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    val handleToUse = if (suggestedUser.handle.isNotEmpty()) suggestedUser.handle else "@${profile?.preferredUsername}"
                    Text(
                        text = handleToUse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    val bio = HtmlUtils.stripHtml(profile?.summary ?: "")
                    if (bio.isNotBlank()) {
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 5,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isFollowing = true
                        try {
                            val handleToFollow = if (suggestedUser.handle.isNotEmpty()) suggestedUser.handle else {
                                val domain = java.net.URL(instanceUrl).host
                                "@${fullProfile?.preferredUsername}@$domain"
                            }
                            val response = api.followUser(handleToFollow.removePrefix("@"))
                            if (response.isSuccessful || response.code() == 302) {
                                Toast.makeText(context, context.getString(R.string.follow_success), Toast.LENGTH_SHORT).show()
                                onFollowSuccess()
                            } else {
                                Toast.makeText(context, context.getString(R.string.profile_server_error, response.code().toString()), Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Toast.makeText(context, context.getString(R.string.profile_network_error, e.message), Toast.LENGTH_LONG).show()
                        } finally {
                            isFollowing = false
                        }
                    }
                },
                enabled = !isLoading && !isFollowing
            ) {
                if (isFollowing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.follow_btn_follow))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isFollowing) {
                Text(stringResource(R.string.post_btn_cancel))
            }
        }
    )
}

/**
 * Renderiza la pestaña de actividad (Timeline) mostrando la lista de publicaciones.
 *
 * Soporta refresco manual (Pull-to-refresh) y carga infinita. Muestra estados vacíos y
 * de carga correspondientes.
 *
 * @param modifier Modificador base para el contenedor.
 * @param timeline Lista de elementos de actividad para renderizar.
 * @param profile Perfil del usuario autenticado (utilizado para referencias y avatares por defecto).
 * @param likedStatusIds Conjunto de identificadores de estados marcados como favoritos.
 * @param isLoading Indica si se está realizando la carga inicial de datos.
 * @param isRefreshing Indica si se está ejecutando un refresco manual.
 * @param onRefresh Callback para iniciar un refresco manual.
 * @param onLikeClick Callback al presionar el botón de favorito de un elemento.
 * @param onLoadMore Callback invocado cuando la lista se aproxima al final para paginación.
 * @param onItemClick Callback invocado al seleccionar el cuerpo de un elemento de la lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTab(
    modifier: Modifier = Modifier,
    timeline: List<TimelineUiItem>,
    profile: BookWyrmProfile?,
    likedStatusIds: Set<String>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLikeClick: (TimelineUiItem) -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (TimelineUiItem) -> Unit,
    api: com.ferlagod.rocinante.data.api.BookWyrmApi
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedBookDetails by remember { mutableStateOf<com.ferlagod.rocinante.data.api.BookWyrmBookDetails?>(null) }
    var selectedBookReviews by remember { mutableStateOf<List<com.ferlagod.rocinante.data.api.ActivityPubActivity>>(emptyList()) }
    var activeBookKey by remember { mutableStateOf("") }
    var fallbackCoverUrl by remember { mutableStateOf("") }

    selectedBookDetails?.let { details ->
        com.ferlagod.rocinante.ui.components.BookDetailsDialog(
            bookDetails = details,
            reviews = selectedBookReviews,
            activeBookKey = activeBookKey,
            fallbackCoverUrl = fallbackCoverUrl,
            currentShelf = "reading",
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = {
                selectedBookDetails = null
                selectedBookReviews = emptyList()
            },
            onShelved = { onRefresh() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading && timeline.isEmpty()) {
            // Skeleton loading: muestra tarjetas de carga animadas en la primera carga
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(5) {
                    ActivitySkeletonCard()
                }
            }
        } else if (timeline.isEmpty() && !isRefreshing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.activity_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.activity_empty_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            val shouldLoadMore = remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    // Umbral ampliado a -5 para anticipar mejor la carga del siguiente lote
                    lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
                }
            }
            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value) {
                    onLoadMore()
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(timeline) { item ->
                        val isLiked = likedStatusIds.contains(item.objectId)
                        ActivityItemCard(
                            item = item, 
                            currentUserProfile = profile,
                            isLiked = isLiked,
                            onLikeClick = { onLikeClick(item) },
                            onReplyClick = { onItemClick(item) },
                            onClick = { onItemClick(item) },
                            onBookClick = { bookUrl, coverUrl ->
                                if (bookUrl.isNotEmpty()) {
                                    activeBookKey = bookUrl
                                    fallbackCoverUrl = coverUrl ?: ""
                                    coroutineScope.launch {
                                        try {
                                            val localUrl = com.ferlagod.rocinante.data.api.NetworkClient.resolveLocalBookUrl(api, bookUrl) ?: bookUrl
                                            val bookId = com.ferlagod.rocinante.utils.BookWyrmUtils.extractBookId(localUrl)
                                            val baseUrl = localUrl.substringBefore("/book/")
                                            val detailsUrl = "$baseUrl/book/$bookId.json"
                                            selectedBookDetails = api.getBookDetails(detailsUrl)
                                            val baseBookUrl = detailsUrl.removeSuffix(".json").trimEnd('/')
                                            try {
                                                selectedBookReviews = com.ferlagod.rocinante.data.api.NetworkClient.scrapeBookReviews(api, baseBookUrl)
                                            } catch (_: Exception) {
                                                selectedBookReviews = emptyList()
                                            }
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            android.widget.Toast.makeText(context, context.getString(R.string.error_details_load, e.message), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de carga "skeleton" que muestra rectángulos grises animados como placeholder
 * mientras se carga el timeline por primera vez.
 */
@Composable
private fun ActivitySkeletonCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(shimmerColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(shimmerColor)
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(shimmerColor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(shimmerColor)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(14.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(shimmerColor)
            )
        }
    }
}

@Composable
private fun ActivityItemCard(
    item: TimelineUiItem,
    currentUserProfile: BookWyrmProfile?,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    onClick: () -> Unit,
    onBookClick: (String, String?) -> Unit = { _, _ -> }
) {
    val displayAvatarUrl = item.actorAvatarUrl?.takeIf { it.isNotEmpty() }
        ?: currentUserProfile?.icon?.url
    val displayName = item.actorName.takeIf { it.isNotEmpty() }
        ?: currentUserProfile?.name
        ?: stringResource(R.string.activity_unknown_user)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: Avatar, Name, Type/Action, Date
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = displayAvatarUrl,
                    contentDescription = stringResource(R.string.profile_avatar_desc),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (actionText, actionIcon) = getActivityContext(item.type)
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = HtmlUtils.formatRelativeDate(item.published) ?: stringResource(R.string.date_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body: Content & Book cover
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (item.content.isNotBlank() && item.content != "Sin contenido") {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else if (!item.bookCoverUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (!item.bookCoverUrl.isNullOrEmpty()) {
                    coil.compose.AsyncImage(
                        model = item.bookCoverUrl,
                        contentDescription = stringResource(R.string.book_cover_desc),
                        modifier = Modifier
                            .width(64.dp)
                            .height(96.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                item.bookUrl?.let { url ->
                                    onBookClick(url, item.bookCoverUrl)
                                }
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            
            // Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onReplyClick() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = stringResource(R.string.activity_reply),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.activity_reply), style = MaterialTheme.typography.labelMedium)
                }

                TextButton(
                    onClick = { onLikeClick() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.activity_like),
                        tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.activity_like),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current
                    )
                }
            }
        }
    }
}

/**
 * Devuelve el texto descriptivo y el icono correspondiente a cada tipo de actividad
 * de ActivityPub/BookWyrm para mostrar en el header de las tarjetas del timeline.
 *
 * Los tipos aquí listados incluyen tanto los tipos de actividad wrapper (Announce, Add)
 * como los tipos de objeto reales extraídos de actividades Create (Review, Comment, etc.).
 *
 * @param type Tipo de actividad o de objeto.
 * @return Par de (texto localizado, icono Material).
 */
@Composable
private fun getActivityContext(type: String): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> {
    return when (type) {
        "Review" -> Pair(stringResource(R.string.activity_type_review), Icons.Default.Star)
        "Rating" -> Pair(stringResource(R.string.activity_type_rating), Icons.Default.Star)
        "Announce" -> Pair(stringResource(R.string.activity_type_announce), Icons.Default.Share)
        "Note", "Comment" -> Pair(stringResource(R.string.activity_type_comment), Icons.Default.Edit)
        "Quotation" -> Pair("Cita", Icons.AutoMirrored.Filled.Reply)
        "Add" -> Pair(stringResource(R.string.activity_type_add), Icons.Default.Add)
        "Create" -> Pair(stringResource(R.string.activity_type_add), Icons.Default.Add)
        else -> Pair(type, Icons.Default.Edit)
    }
}

@Composable
fun ProfileTab(
    modifier: Modifier = Modifier,
    profile: BookWyrmProfile?,
    username: String,
    instanceUrl: String,
    @Suppress("UNUSED_PARAMETER") cookie: String,
    api: com.ferlagod.rocinante.data.api.BookWyrmApi,
    onProfileUpdated: (String, String) -> Unit,
    onFollowingIncremented: () -> Unit = {},
    onFollowingDecremented: () -> Unit = {}
) {
    val cleanSummary = HtmlUtils.stripHtml(profile?.summary)
    val avatarUrl = profile?.icon?.url
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cookieJar = remember(api) {
        (NetworkClient.lastOkHttpClient?.cookieJar as? com.ferlagod.rocinante.data.api.SessionCookieJar)
    }

    var readingBooks by remember { mutableStateOf<List<com.ferlagod.rocinante.data.api.ShelfBookItem>>(emptyList()) }
    var suggestedUsers by remember { mutableStateOf<List<com.ferlagod.rocinante.data.api.SuggestedUser>>(emptyList()) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editSummary by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var refreshTrigger by remember { mutableStateOf(0) }

    var summary by remember { mutableStateOf("") }
    var rawFollowersCount by remember { mutableStateOf(0) }
    var rawFollowingCount by remember { mutableStateOf(0) }
    var fallbackCoverUrl by remember { mutableStateOf("") }


    val followCache = remember(context) { com.ferlagod.rocinante.data.local.FollowListCache(context) }
    val followFactory = remember(api, followCache) { com.ferlagod.rocinante.ui.screens.home.FollowListViewModelFactory(api, followCache) }
    
    val followersViewModel: com.ferlagod.rocinante.ui.screens.home.FollowListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "follow_list_FOLLOWERS",
        factory = followFactory
    )
    val followingViewModel: com.ferlagod.rocinante.ui.screens.home.FollowListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "follow_list_FOLLOWING",
        factory = followFactory
    )

    LaunchedEffect(username, instanceUrl) {
        val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
        val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
        val cleanUser = username.removePrefix("@").trim()

        followersViewModel.load(baseUrl, cleanUser, FollowListDirection.FOLLOWERS)
        followingViewModel.load(baseUrl, cleanUser, FollowListDirection.FOLLOWING)
    }

    var selectedBookDetails by remember { mutableStateOf<com.ferlagod.rocinante.data.api.BookWyrmBookDetails?>(null) }
    var selectedBookReviews by remember { mutableStateOf<List<com.ferlagod.rocinante.data.api.ActivityPubActivity>>(emptyList()) }
    var activeBookKey by remember { mutableStateOf("") }

    var followSheetDirection by remember { mutableStateOf<FollowListDirection?>(null) }
    var selectedSuggestedUser by remember { mutableStateOf<com.ferlagod.rocinante.data.api.SuggestedUser?>(null) }

    followSheetDirection?.let { dir ->
        FollowListSheet(
            direction = dir,
            instanceUrl = instanceUrl,
            username = username,
            api = api,
            onDismiss = { followSheetDirection = null },
            onFollowToggled = { isFollowing ->
                if (isFollowing) {
                    onFollowingIncremented()
                } else {
                    onFollowingDecremented()
                }
                
                val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
                val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
                val cleanUser = username.removePrefix("@").trim()
                
                if (dir == FollowListDirection.FOLLOWERS) {
                    followingViewModel.load(baseUrl, cleanUser, FollowListDirection.FOLLOWING, forceRefresh = true)
                }
            }
        )
    }

    selectedBookDetails?.let { details ->
        com.ferlagod.rocinante.ui.components.BookDetailsDialog(
            bookDetails = details,
            reviews = selectedBookReviews,
            activeBookKey = activeBookKey,
            fallbackCoverUrl = fallbackCoverUrl,
            currentShelf = "reading",
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

    selectedSuggestedUser?.let { user ->
        SuggestedUserDialog(
            suggestedUser = user,
            api = api,
            instanceUrl = instanceUrl,
            onDismiss = { selectedSuggestedUser = null },
            onFollowSuccess = { 
                selectedSuggestedUser = null
                refreshTrigger++
                onFollowingIncremented()
                
                // Refresh the following list in the background
                val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
                val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
                val cleanUser = username.removePrefix("@").trim()
                followingViewModel.load(baseUrl, cleanUser, FollowListDirection.FOLLOWING, forceRefresh = true)
            }
        )
    }

    val dataCache = remember(context) { com.ferlagod.rocinante.data.local.TimelineCache(context) }

    LaunchedEffect(Unit) {
        val cachedReading = dataCache.loadShelfBooks("reading")
        if (cachedReading != null && readingBooks.isEmpty()) {
            readingBooks = cachedReading
        }
        val cachedUsers = dataCache.loadSuggestedUsers()
        if (cachedUsers != null && suggestedUsers.isEmpty()) {
            suggestedUsers = cachedUsers
        }
    }

    LaunchedEffect(username, refreshTrigger) {
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val cleanUser = username.removePrefix("@").trim()
            val shelfJsonUrl = "${baseUrl}user/$cleanUser/shelf/reading.json?page=1"
            val response = api.getShelfData(shelfJsonUrl)
            val fetchedItems = response.orderedItems ?: emptyList()
            readingBooks = fetchedItems
            dataCache.saveShelfBooks("reading", fetchedItems)
        } catch (_: Exception) {
        }
        try {
            val fetchedUsers = com.ferlagod.rocinante.data.api.NetworkClient.getSuggestedUsers(api, instanceUrl)
            suggestedUsers = fetchedUsers
            dataCache.saveSuggestedUsers(fetchedUsers)
        } catch (_: Exception) {
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = stringResource(R.string.profile_avatar_desc),
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.profile_default_avatar_desc),
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                            )
                        }

                        Column {
                            Text(
                                text = profile?.name ?: username,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = username,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { followSheetDirection = FollowListDirection.FOLLOWERS }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = profile?.followersCountLocal?.toString() ?: "0",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.profile_followers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { followSheetDirection = FollowListDirection.FOLLOWING }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = profile?.followingCountLocal?.toString() ?: "0",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.profile_following),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (readingBooks.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.profile_currently_reading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(readingBooks) { book ->
                        val coverUrl = book.cover?.url
                        if (!coverUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = stringResource(R.string.book_cover_desc),
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(135.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        if (!book.id.isNullOrEmpty()) {
                                            activeBookKey = book.id
                                            fallbackCoverUrl = book.cover?.url ?: ""
                                            coroutineScope.launch {
                                                try {
                                                    val detailsUrl = com.ferlagod.rocinante.utils.BookWyrmUtils.ensureJsonUrl(book.id)
                                                    selectedBookDetails = api.getBookDetails(detailsUrl)
                                                    val baseBookUrl = detailsUrl.removeSuffix(".json").trimEnd('/')
                                                    try {
                                                        selectedBookReviews = com.ferlagod.rocinante.data.api.NetworkClient.scrapeBookReviews(api, baseBookUrl)
                                                    } catch (_: Exception) {
                                                        selectedBookReviews = emptyList()
                                                    }
                                                } catch (e: Exception) {
                                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                                    Toast.makeText(context, context.getString(R.string.error_details_load, e.message), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.profile_bio_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = cleanSummary.ifBlank {
                            stringResource(R.string.profile_bio_empty)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (suggestedUsers.isNotEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.profile_who_to_follow),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(suggestedUsers) { user ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable { selectedSuggestedUser = user }
                                        .padding(4.dp)
                                ) {
                                    if (user.avatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = user.avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp).clip(CircleShape)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = user.handle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showEditDialog = false },
            title = { Text(stringResource(R.string.profile_edit_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.profile_edit_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editSummary,
                        onValueChange = { editSummary = it },
                        label = { Text(stringResource(R.string.profile_edit_bio_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        enabled = !isSubmitting,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitting = true
                        coroutineScope.launch {
                            try {
                                val csrfToken = cookieJar?.currentCsrfToken() ?: ""
                                val response = api.editProfile(editName, editSummary, csrfToken)
                                if (response.code() == 302 || response.code() == 201) {
                                    Toast.makeText(context, context.getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                                    showEditDialog = false
                                    refreshTrigger++
                                    onProfileUpdated(editName, editSummary)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.profile_server_error, response.code().toString()), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Toast.makeText(context, context.getString(R.string.profile_network_error, e.message), Toast.LENGTH_LONG).show()
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting
                ) {
                    Text(if (isSubmitting) stringResource(R.string.profile_edit_saving) else stringResource(R.string.profile_edit_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text(stringResource(R.string.profile_edit_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailsDialog(
    item: TimelineUiItem,
    currentUserProfile: BookWyrmProfile?,
    username: String,
    instanceUrl: String,
    api: com.ferlagod.rocinante.data.api.BookWyrmApi,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onReplySubmit: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onBookClick: (String, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    val displayAvatarUrl = item.actorAvatarUrl?.takeIf { it.isNotEmpty() } ?: currentUserProfile?.icon?.url
    val displayName = item.actorName.takeIf { it.isNotEmpty() } ?: currentUserProfile?.name ?: stringResource(R.string.activity_unknown_user)

    AlertDialog(
        onDismissRequest = { onDismiss() },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = displayAvatarUrl,
                        contentDescription = stringResource(R.string.profile_avatar_desc),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = HtmlUtils.formatPublishedDate(item.published) ?: stringResource(R.string.date_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                
                Text(text = item.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (!item.bookCoverUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = item.bookCoverUrl,
                            contentDescription = stringResource(R.string.book_cover_desc),
                            modifier = Modifier
                                .width(70.dp)
                                .height(105.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    item.bookUrl?.let { url ->
                                        onBookClick(url, item.bookCoverUrl)
                                    }
                                },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onLikeClick) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.activity_like),
                            tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isLiked) stringResource(R.string.activity_liked_by_me) else stringResource(R.string.activity_like_action),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                var replyText by remember { mutableStateOf("") }
                var isReplying by remember { mutableStateOf(false) }

                Text(
                    text = stringResource(R.string.activity_reply),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text(stringResource(R.string.activity_reply_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    enabled = !isReplying
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                isReplying = true
                                onReplySubmit(replyText) { success ->
                                    isReplying = false
                                    if (success) {
                                        replyText = ""
                                        Toast.makeText(context, context.getString(R.string.activity_reply_success), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.activity_reply_error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = replyText.isNotBlank() && !isReplying
                    ) {
                        Text(if (isReplying) stringResource(R.string.post_btn_sending) else stringResource(R.string.activity_reply))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.activity_close_panel))
            }
        }
    )
}
