/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 Fernando Lago (ferlagod)
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU Affero General Public License (AGPLv3).
 * * AVISO DE DOBLE LICENCIA: Para uso comercial o propietario sin 
 * las obligaciones de liberación de código de la AGPLv3, 
 * es necesario adquirir una licencia comercial previa.
 */
package com.ferlagod.rocinante.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.ferlagod.rocinante.data.repository.BookWyrmRepository
import kotlinx.coroutines.launch

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

    val api = remember(instanceUrl, cookie) {
        NetworkClient.createAuthenticatedApi(instanceUrl, cookie)
    }

    val repository = remember(api) {
        BookWyrmRepository(api)
    }

    val timelineCache = remember(context) {
        com.ferlagod.rocinante.data.local.TimelineCache(context)
    }

    val factory = remember(repository, timelineCache) {
        HomeViewModelFactory(repository, timelineCache)
    }

    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(instanceUrl, username, cookie) {
        viewModel.load(instanceUrl, username, cookie)
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
        when (uiState.selectedTab) {
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
                onItemClick = { selectedActivity = it }
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
                }
            )
        }

        // Lógica del cuadro de diálogo para publicaciones
        if (showPostDialog) {
            var statusText by remember { mutableStateOf("") }
            var isSubmitting by remember { mutableStateOf(false) }

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
                                            val userId = userMatch?.let { it.groups[1]?.value ?: it.groups[2]?.value }
                                            if (userId != null) {
                                                val cookieJar = NetworkClient.lastOkHttpClient?.cookieJar as? com.ferlagod.rocinante.data.api.SessionCookieJar
                                                val csrfToken = cookieJar?.currentCsrfToken() ?: ""
                                                val response = api.createStatus(userId = userId, content = statusText, csrfToken = csrfToken)
                                                if (response.isSuccessful || response.code() == 302) {
                                                    showPostDialog = false
                                                    Toast.makeText(context, context.getString(R.string.post_success), Toast.LENGTH_SHORT).show()
                                                    viewModel.load(instanceUrl, username, cookie, forceRefresh = true)
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.profile_server_error, response.code().toString()), Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.error_id_html, html.take(100)), Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.error_session_verification, htmlResponse.code().toString()), Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.post_error, e.message), Toast.LENGTH_LONG).show()
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
        
        if (selectedActivity != null) {
            val isLiked = uiState.likedStatusIds.contains(selectedActivity!!.objectId)
            ActivityDetailsDialog(
                item = selectedActivity!!,
                currentUserProfile = uiState.profile,
                username = username,
                instanceUrl = instanceUrl,
                api = api,
                isLiked = isLiked,
                onLikeClick = { viewModel.toggleLike(selectedActivity!!.objectId, instanceUrl) },
                onReplySubmit = { replyText, onResult ->
                    viewModel.replyToStatus(
                        statusUrl = selectedActivity!!.objectId,
                        instanceUrl = instanceUrl,
                        content = replyText,
                        onResult = { success ->
                            onResult(success)
                            if (success) {
                                viewModel.load(instanceUrl, username, cookie, forceRefresh = true)
                            }
                        }
                    )
                },
                onDismiss = { selectedActivity = null }
            )
        }
    }
}

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
    onItemClick: (TimelineUiItem) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (timeline.isEmpty()) {
            if (isLoading || isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        Text(text = stringResource(R.string.activity_timeline_loading), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
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
            }
        } else {
            val listState = rememberLazyListState()
            val shouldLoadMore = remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                    lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
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
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
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
    onClick: () -> Unit
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
                            text = HtmlUtils.formatPublishedDate(item.published) ?: stringResource(R.string.date_unknown),
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
                    AsyncImage(
                        model = item.bookCoverUrl,
                        contentDescription = stringResource(R.string.book_cover_desc),
                        modifier = Modifier
                            .width(64.dp)
                            .height(96.dp)
                            .clip(MaterialTheme.shapes.small),
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

@Composable
private fun getActivityContext(type: String): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> {
    return when (type) {
        "Review" -> Pair(stringResource(R.string.activity_type_review), Icons.Default.Edit)
        "Rating" -> Pair(stringResource(R.string.activity_type_rating), Icons.Default.Star)
        "Announce" -> Pair(stringResource(R.string.activity_type_announce), Icons.Default.Share)
        "Note", "Comment" -> Pair(stringResource(R.string.activity_type_comment), Icons.Default.Edit)
        "Add", "Create" -> Pair(stringResource(R.string.activity_type_add), Icons.Default.Add)
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
    onProfileUpdated: (String, String) -> Unit
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
    var activeBookKey by remember { mutableStateOf("") }

    var followSheetDirection by remember { mutableStateOf<FollowListDirection?>(null) }
    var selectedSuggestedUser by remember { mutableStateOf<com.ferlagod.rocinante.data.api.SuggestedUser?>(null) }

    followSheetDirection?.let { dir ->
        FollowListSheet(
            direction = dir,
            instanceUrl = instanceUrl,
            username = username,
            api = api,
            onDismiss = { followSheetDirection = null }
        )
    }

    if (selectedBookDetails != null) {
        com.ferlagod.rocinante.ui.components.BookDetailsDialog(
            bookDetails = selectedBookDetails!!,
            reviews = emptyList(),
            activeBookKey = activeBookKey,
            fallbackCoverUrl = fallbackCoverUrl,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { selectedBookDetails = null },
            onShelved = { refreshTrigger++ }
        )
    }

    if (selectedSuggestedUser != null) {
        SuggestedUserDialog(
            suggestedUser = selectedSuggestedUser!!,
            api = api,
            instanceUrl = instanceUrl,
            onDismiss = { selectedSuggestedUser = null },
            onFollowSuccess = { 
                selectedSuggestedUser = null
                refreshTrigger++
            }
        )
    }

    LaunchedEffect(username, refreshTrigger) {
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val cleanUser = username.removePrefix("@").trim()
            val shelfJsonUrl = "${baseUrl}user/$cleanUser/shelf/reading.json?page=1"
            val response = api.getShelfData(shelfJsonUrl)
            readingBooks = response.orderedItems ?: emptyList()
        } catch (_: Exception) {
        }
        try {
            suggestedUsers = com.ferlagod.rocinante.data.api.NetworkClient.getSuggestedUsers(api, instanceUrl)
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
                                                } catch (e: Exception) {
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
    onDismiss: () -> Unit
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
                        AsyncImage(
                            model = item.bookCoverUrl,
                            contentDescription = stringResource(R.string.book_cover_desc),
                            modifier = Modifier
                                .width(70.dp)
                                .height(105.dp)
                                .clip(MaterialTheme.shapes.small),
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
