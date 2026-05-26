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
package com.ferlagod.rocinante.ui.screens.search

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.QrCodeScanner
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedBookDetails by remember { mutableStateOf<BookWyrmBookDetails?>(null) }
    var selectedBookReviews by remember { mutableStateOf<List<ActivityPubActivity>>(emptyList()) }
    var activeBookKey by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val barcodeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            searchQuery = result.contents
            isSearching = true
            keyboardController?.hide()
            // Lanzar búsqueda automáticamente
            coroutineScope.launch {
                try {
                    searchResults = resolvedApi.searchBooks(searchQuery)
                    errorMessage = null
                    if (searchResults.isEmpty()) {
                        errorMessage = context.getString(R.string.shelf_empty)
                    }
                } catch (e: Exception) {
                    if (e is com.google.gson.JsonSyntaxException || e.message?.contains("html") == true) {
                        errorMessage = context.getString(R.string.search_error, context.getString(R.string.search_api_unsupported))
                    } else {
                        errorMessage = context.getString(R.string.search_error, e.message)
                    }
                } finally {
                    isSearching = false
                }
            }
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
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        isSearching = true
                        keyboardController?.hide()
                        coroutineScope.launch {
                            try {
                                searchResults = resolvedApi.searchBooks(searchQuery)
                                errorMessage = null
                                if (searchResults.isEmpty()) {
                                    errorMessage = context.getString(R.string.shelf_empty)
                                }
                            } catch (e: Exception) {
                                if (e is com.google.gson.JsonSyntaxException || e.message?.contains("html") == true) {
                                    errorMessage = context.getString(R.string.search_error, context.getString(R.string.search_api_unsupported))
                                } else {
                                    errorMessage = context.getString(R.string.search_error, e.message)
                                }
                            } finally {
                                isSearching = false
                            }
                        }
                    }
                }
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (searchQuery.isNotBlank()) {
                    isSearching = true
                    keyboardController?.hide()
                    coroutineScope.launch {
                        try {
                            searchResults = resolvedApi.searchBooks(searchQuery)
                            errorMessage = null
                            if (searchResults.isEmpty()) {
                                errorMessage = context.getString(R.string.shelf_empty)
                            }
                        } catch (e: Exception) {
                            if (e is com.google.gson.JsonSyntaxException || e.message?.contains("html") == true) {
                                errorMessage = context.getString(R.string.search_error, context.getString(R.string.search_api_unsupported))
                            } else {
                                errorMessage = context.getString(R.string.search_error, e.message)
                            }
                        } finally {
                            isSearching = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.search_btn))
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoadingDetails) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isSearching) {
            SearchSkeletonLoader()
        } else if (searchResults.isNotEmpty()) {
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
                                        activeBookKey = bookKey
                                        isLoadingDetails = true
                                        coroutineScope.launch {
                                            try {
                                                val detailsUrl = BookWyrmUtils.ensureJsonUrl(bookKey)
                                                selectedBookDetails = resolvedApi.getBookDetails(detailsUrl)

                                                val baseBookUrl = detailsUrl.removeSuffix(".json").trimEnd('/')
                                                try {
                                                    selectedBookReviews = NetworkClient.scrapeBookReviews(resolvedApi, baseBookUrl)
                                                } catch (_: Exception) {
                                                    selectedBookReviews = emptyList()
                                                }
                                            } catch (e: Exception) {
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
        }
    }

    if (selectedBookDetails != null) {
        com.ferlagod.rocinante.ui.components.BookDetailsDialog(
            bookDetails = selectedBookDetails!!,
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
}

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