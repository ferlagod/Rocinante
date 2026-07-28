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
package com.ferlagod.rocinante.ui.components


import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.model.ActivityPubActivity
import com.ferlagod.rocinante.data.model.BookWyrmBookDetails
import com.ferlagod.rocinante.utils.BookWyrmUtils
import com.ferlagod.rocinante.utils.HtmlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Diálogo interactivo principal que muestra la información detallada de una obra, 
 * incluyendo portada, descripción extendida, la opción para gestionar estantes y las reseñas públicas.
 *
 * @param bookDetails Datos estructurados devueltos por la API del libro.
 * @param reviews Listado de reseñas y progreso de lectura asociados a la obra.
 * @param activeBookKey URL remota o ID local clave de este libro para llamadas a red.
 * @param fallbackCoverUrl Imagen auxiliar a emplear en caso de que [bookDetails] no provea portada.
 * @param currentShelf El estante (ej. 'to-read', 'reading') en el que se ubica el libro.
 * @param api Instancia autenticada del cliente de la red para operar.
 * @param context Contexto de la interfaz de usuario para emitir mensajes y toasts.
 * @param coroutineScope Entorno asíncrono asignado a esta vista.
 * @param onDismiss Ejecuta la lógica para destruir o cerrar la pantalla modal actual.
 * @param onShelved Callback opcional invocado al clasificar el libro en un estante exitosamente.
 */
/** Fila de 5 estrellas (llena / media / vacía) para una valoración de 0.5 a 5.0. */
@Composable
private fun BookRatingStars(rating: Double) {
    val starColor = Color(0xFFF5A623)
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Filled.Star
                rating >= i - 0.5 -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Formatea una fecha ISO (yyyy-MM-dd) al formato medio del idioma del dispositivo. */
private fun formatDetailDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val parsed = parser.parse(iso) ?: return iso
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(parsed)
    } catch (e: Exception) {
        iso
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsDialog(
    bookDetails: BookWyrmBookDetails,
    reviews: List<ActivityPubActivity>,
    activeBookKey: String,
    fallbackCoverUrl: String = "",
    currentShelf: String? = null,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onShelved: (() -> Unit)? = null,
    // Datos enriquecidos ya conocidos por quien abre la ficha (p. ej. la caché de la
    // estantería), para mostrar las estrellas al instante mientras se refresca en segundo plano.
    initialEnrichment: com.ferlagod.rocinante.data.model.BookEnrichment? = null
) {
    var showProgressDialog by remember { mutableStateOf(false) }
    var showReadingActionsDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showQuotationDialog by remember { mutableStateOf(false) }
    var showMyActivityDialog by remember { mutableStateOf(false) }
    var selectedReviewForDetail by remember { mutableStateOf<ActivityPubActivity?>(null) }
    var isFinishing by remember { mutableStateOf(false) }

    // Progreso de lectura actual (solo relevante mientras el libro está en lectura).
    // Se recarga cada vez que cambia el libro o tras una actualización correcta.
    var readingProgress by remember { mutableStateOf<BookWyrmScraper.ReadingProgressInfo?>(null) }
    var isLoadingProgress by remember { mutableStateOf(false) }
    var progressRefreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(activeBookKey, progressRefreshKey) {
        if (currentShelf == "reading") {
            isLoadingProgress = true
            readingProgress = runCatching { BookWyrmScraper.getReadingProgress(api, activeBookKey) }.getOrNull()
            isLoadingProgress = false
        } else {
            readingProgress = null
        }
    }

    // Datos enriquecidos del libro (autor, valoración, fechas, idioma) leídos de su página
    // HTML — no vienen en el .json. Se cargan una vez al abrir la ficha.
    var enrichment by remember { mutableStateOf(initialEnrichment) }
    LaunchedEffect(activeBookKey) {
        val fresh = runCatching { BookWyrmScraper.scrapeBookEnrichment(api, activeBookKey) }.getOrNull()
        if (fresh != null) enrichment = fresh
    }

    // Menú de tres puntos (⋮) de la barra + confirmación para cambiar de estante.
    var overflowExpanded by remember { mutableStateOf(false) }
    // Estante pendiente de confirmar: (slug, etiqueta, etiqueta de aviso).
    var pendingShelf by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    // Ejecuta el cambio de estante (llamado tras confirmar en el diálogo).
    fun moveToShelf(slug: String, toastLabel: String) {
        coroutineScope.launch {
            try {
                val localUrl = BookWyrmScraper.resolveLocalBookUrl(api, activeBookKey) ?: activeBookKey
                val bookId = BookWyrmUtils.extractBookId(localUrl)
                if (bookId.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.error_book_not_identified), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val mappedStatus = mapOf("to-read" to "want", "reading" to "start", "read" to "finish")[slug]
                val response = if (mappedStatus != null) {
                    api.updateReadingStatus(mappedStatus, bookId)
                } else {
                    api.shelveBook(bookId, slug)
                }
                if (response.isSuccessful || response.code() == 302) {
                    Toast.makeText(context, context.getString(R.string.error_shelve_added, toastLabel), Toast.LENGTH_SHORT).show()
                    onShelved?.invoke()
                    if (slug == "read") {
                        showReviewDialog = true
                    } else {
                        onDismiss()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.error_server, response.code().toString()), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, context.getString(R.string.error_network, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Termina de leer: cierra el readthrough, mueve a «Leídos» y suma al objetivo anual
    // (lógica de v1.1.8, reubicada aquí para reutilizarla desde el diálogo de acciones).
    fun finishReading() {
        coroutineScope.launch {
            isFinishing = true
            try {
                val contextData = BookWyrmScraper.getProgressContext(api, activeBookKey)
                if (contextData != null) {
                    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val today = formatter.format(java.util.Date())
                    val response = api.finishReadingDetailed(
                        bookIdPath = contextData.localBookId,
                        readthroughId = contextData.readthroughId,
                        startDate = contextData.startDate,
                        finishDate = today,
                        csrfToken = contextData.csrfToken
                    )
                    if (response.isSuccessful || response.code() == 302) {
                        Toast.makeText(context, context.getString(R.string.shelf_toast_read), Toast.LENGTH_SHORT).show()
                        onShelved?.invoke()
                        showReviewDialog = true
                    } else {
                        Toast.makeText(context, context.getString(R.string.error_server, response.code().toString()), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.error_book_not_identified), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, context.getString(R.string.error_network, e.message), Toast.LENGTH_SHORT).show()
            } finally {
                isFinishing = false
            }
        }
    }

    // Reseñas ordenadas de más reciente a más antigua. BookWyrm renderiza la fecha en
    // formato legible (p. ej. "Aug. 22, 2024"), así que se parsea a una fecha comparable.
    // Las reseñas con fecha no reconocida se colocan al final.
    val sortedReviews = remember(reviews) {
        reviews.sortedByDescending {
            BookWyrmUtils.parseReviewDate(it.published) ?: java.time.LocalDate.MIN
        }
    }

    // Desde el estante "Leyendo" arrancamos en la pestaña Diverse (progreso); si no, en Resumen.
    var selectedTab by remember { mutableStateOf(if (currentShelf == "reading") 2 else 0) }
    val cleanDesc = HtmlUtils.stripHtml(bookDetails.description ?: stringResource(R.string.book_no_description))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = bookDetails.title ?: stringResource(R.string.book_details_fallback),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.book_close)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.book_more_actions)
                                )
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false }
                            ) {
                                // Cambiar de estante (etiqueta + opciones, con confirmación)
                                DropdownMenuItem(
                                    enabled = false,
                                    text = { Text(stringResource(R.string.book_change_shelf), style = MaterialTheme.typography.labelSmall) },
                                    onClick = {}
                                )
                                listOf(
                                    Triple("to-read", stringResource(R.string.shelf_chip_to_read), stringResource(R.string.shelf_toast_pending)),
                                    Triple("reading", stringResource(R.string.shelf_chip_reading), stringResource(R.string.shelf_toast_reading)),
                                    Triple("read", stringResource(R.string.shelf_chip_read), stringResource(R.string.shelf_toast_read))
                                ).forEach { target ->
                                    if (target.first != currentShelf) {
                                        DropdownMenuItem(
                                            text = { Text("     " + target.second) },
                                            onClick = {
                                                overflowExpanded = false
                                                pendingShelf = target
                                            }
                                        )
                                    }
                                }
                                HorizontalDivider()
                                if (currentShelf == "reading") {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.book_update_progress)) },
                                        onClick = { overflowExpanded = false; showProgressDialog = true }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_add_quote)) },
                                    onClick = { overflowExpanded = false; showQuotationDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_write_review)) },
                                    onClick = { overflowExpanded = false; showReviewDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_my_quotes_reviews)) },
                                    onClick = { overflowExpanded = false; showMyActivityDialog = true }
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    // ── Cabecera: portada con las estrellas debajo ──
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val detailCoverUrl = bookDetails.cover?.url ?: fallbackCoverUrl
                        if (detailCoverUrl.isNotBlank()) {
                            AsyncImage(
                                model = detailCoverUrl,
                                contentDescription = stringResource(R.string.book_cover_detail_desc),
                                modifier = Modifier.width(120.dp).height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        enrichment?.rating?.let { r ->
                            Spacer(modifier = Modifier.height(8.dp))
                            BookRatingStars(r)
                        }
                    }

                    // ── Pestañas: Resumen / Reseñas / Diverse ──
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.book_tab_synopsis)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.book_tab_reviews)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text(stringResource(R.string.book_tab_misc)) }
                        )
                    }

                    when (selectedTab) {
                        // ── Resumen (sinopsis) ──
                        0 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp)
                        ) {
                            Text(text = cleanDesc, style = MaterialTheme.typography.bodyMedium)
                        }

                        // ── Reseñas de la comunidad ──
                        1 -> if (sortedReviews.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.book_no_reviews),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                FilledTonalButton(onClick = { showReviewDialog = true }) {
                                    Text(stringResource(R.string.book_write_review))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(sortedReviews) { review ->
                                    val rawContent = review.objectData?.content ?: review.content ?: ""
                                    val cleanReview = HtmlUtils.stripHtml(rawContent).trim()
                                    val rating = review.objectData?.rating
                                    if (cleanReview.isNotBlank() || rating != null) {
                                        ElevatedCard(
                                            onClick = { selectedReviewForDetail = review },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                val avatarUrl = review.actorAvatarUrl
                                                if (!avatarUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = avatarUrl,
                                                        contentDescription = stringResource(R.string.profile_avatar_desc),
                                                        modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Surface(
                                                        modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                                        color = MaterialTheme.colorScheme.surfaceVariant
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = stringResource(R.string.profile_default_avatar_desc),
                                                                modifier = Modifier.size(18.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val reviewerName = review.name ?: stringResource(R.string.progress_privacy_private)
                                                        Text(
                                                            text = reviewerName,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (rating != null && rating in 1..5) {
                                                            Text(
                                                                text = "★".repeat(rating) + "☆".repeat(5 - rating),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.tertiary
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    val dateStr = review.published ?: ""
                                                    val shortDate = when {
                                                        dateStr.isBlank() -> stringResource(R.string.book_review_date_unknown)
                                                        dateStr.contains("T") -> dateStr.substringBefore("T")
                                                        else -> dateStr
                                                    }
                                                    Text(
                                                        text = shortDate,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    FilledTonalButton(
                                        onClick = { showReviewDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.book_write_review))
                                    }
                                }
                            }
                        }

                        // ── Diverse: datos del libro + funciones ──
                        else -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            enrichment?.authorName?.takeIf { it.isNotBlank() }?.let {
                                Text(text = "👤 $it", style = MaterialTheme.typography.bodyMedium)
                            }
                            bookDetails.pages?.let {
                                Text(
                                    text = "📖 " + stringResource(R.string.book_pages, it.toString()),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            enrichment?.language?.let { lang ->
                                val flag = com.ferlagod.rocinante.utils.LanguageFlags.flagFor(lang)
                                Text(
                                    text = (flag?.plus("  ") ?: "") + lang,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            enrichment?.finished?.let { iso ->
                                Text(
                                    text = "✅ " + stringResource(R.string.shelf_read_title) + ": " + (formatDetailDate(iso) ?: iso),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (bookDetails.publishedDate != null) {
                                Text(
                                    text = "📅 " + stringResource(R.string.book_published_date, bookDetails.publishedDate),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Acción contextual según el estante:
                            // - «Pendiente»: empezar a leer.
                            // - «Leyendo» sin barra de progreso todavía: actualizar progreso.
                            if (currentShelf == "to-read") {
                                Button(
                                    onClick = {
                                        moveToShelf("reading", context.getString(R.string.shelf_chip_reading))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.book_start_reading))
                                }
                            } else if (currentShelf == "reading" && readingProgress == null && !isLoadingProgress) {
                                Button(
                                    onClick = { showProgressDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.book_update_progress))
                                }
                            }

                            // Progreso de lectura (si está leyendo)
                            if (isLoadingProgress && readingProgress == null) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            readingProgress?.let { rp ->
                                val totalPages = bookDetails.pages
                                val fraction = when {
                                    rp.mode == "PCT" -> rp.progress / 100f
                                    totalPages != null && totalPages > 0 -> rp.progress.toFloat() / totalPages
                                    else -> null
                                }?.coerceIn(0f, 1f)
                                val progressLabel = when {
                                    rp.mode == "PCT" -> stringResource(R.string.book_progress_percent, rp.progress)
                                    totalPages != null && totalPages > 0 -> stringResource(
                                        R.string.book_progress_pages,
                                        rp.progress,
                                        totalPages,
                                        (rp.progress * 100 / totalPages)
                                    )
                                    else -> stringResource(R.string.book_progress_pages_no_total, rp.progress)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.book_current_progress),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    // Progreso (etiqueta + barra) con un botón de lápiz alto a la
                                    // derecha que abarca ambas líneas.
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = progressLabel, style = MaterialTheme.typography.bodyMedium)
                                            if (fraction != null) {
                                                LinearProgressIndicator(
                                                    progress = { fraction },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        FilledTonalButton(
                                            onClick = { showReadingActionsDialog = true },
                                            enabled = !isFinishing,
                                            modifier = Modifier.fillMaxHeight()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.book_reading_actions)
                                            )
                                        }
                                    }
                                }
                            }

                            // Las funciones (cambiar de estante, progreso, citar, reseñar,
                            // mis citas/reseñas) están en el menú de tres puntos (⋮) de la barra.
                        }
                    }
                }
            }
        }
    }

    // ── Confirmación antes de cambiar de estante ──
    pendingShelf?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingShelf = null },
            title = { Text(stringResource(R.string.book_change_shelf)) },
            text = { Text(stringResource(R.string.book_move_confirm, target.second)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingShelf = null
                    moveToShelf(target.first, target.third)
                }) {
                    Text(stringResource(R.string.book_move_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingShelf = null }) {
                    Text(stringResource(R.string.progress_btn_cancel))
                }
            }
        )
    }

    // ── Diálogo de acciones de lectura (actualizar progreso / terminar) ──
    if (showReadingActionsDialog) {
        AlertDialog(
            onDismissRequest = { showReadingActionsDialog = false },
            title = { Text(stringResource(R.string.book_reading_actions)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showReadingActionsDialog = false
                            showProgressDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.book_update_progress))
                    }
                    Button(
                        onClick = {
                            showReadingActionsDialog = false
                            finishReading()
                        },
                        enabled = !isFinishing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.book_finish_reading))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReadingActionsDialog = false }) {
                    Text(stringResource(R.string.progress_btn_cancel))
                }
            }
        )
    }

    // ── Diálogo de progreso de lectura (rediseñado según la UI de BookWyrm) ──
    if (showProgressDialog) {
        ReadingProgressDialog(
            bookDetails = bookDetails,
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showProgressDialog = false },
            onSuccess = { newProgress ->
                showProgressDialog = false
                // Reflejar el nuevo progreso al instante con el valor enviado.
                // Si por algún motivo no llegó el valor, recargar desde la red como respaldo.
                if (newProgress != null) {
                    readingProgress = newProgress
                } else {
                    progressRefreshKey++
                }
                onShelved?.invoke()
            }
        )
    }

    if (showReviewDialog) {
        ReviewDialog(
            bookDetails = bookDetails,
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showReviewDialog = false },
            onSuccess = {
                showReviewDialog = false
                onDismiss() // Cerrar el diálogo entero
            }
        )
    }

    if (showQuotationDialog) {
        QuotationDialog(
            bookDetails = bookDetails,
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showQuotationDialog = false },
            onSuccess = {
                showQuotationDialog = false
                onDismiss()
            }
        )
    }

    if (showMyActivityDialog) {
        MyBookActivityDialog(
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showMyActivityDialog = false }
        )
    }

    selectedReviewForDetail?.let { review ->
        // Host de la instancia (ej. "https://bookwyrm.social"), usado para resolver
        // los enlaces de autor relativos a un handle de seguimiento.
        val instanceHostUrl = remember(activeBookKey) {
            try {
                java.net.URL(activeBookKey).let { "${it.protocol}://${it.host}" }
            } catch (_: Exception) { "" }
        }
        ReviewDetailDialog(
            review = review,
            instanceHostUrl = instanceHostUrl,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { selectedReviewForDetail = null }
        )
    }
}

/**
 * Diálogo completo de actualización de progreso de lectura.
 * Replica la interfaz del modal de BookWyrm web:
 *  - Campo de progreso + selector páginas/porcentaje
 *  - Checkbox "Publicar en el feed"
 *  - Campo de comentario
 *  - Toggle de alerta de spoiler
 *  - Selector de privacidad
 *  - Botón "Compartir"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingProgressDialog(
    bookDetails: BookWyrmBookDetails,
    activeBookKey: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onSuccess: (BookWyrmScraper.ReadingProgressInfo?) -> Unit
) {
    // Estado del formulario
    var progressInput by remember { mutableStateOf("") }
    var isPages by remember { mutableStateOf(true) }  // true=páginas, false=porcentaje
    var postToFeed by remember { mutableStateOf(true) }
    var commentText by remember { mutableStateOf("") }
    var includeSpoiler by remember { mutableStateOf(false) }
    var spoilerText by remember { mutableStateOf("") }
    var selectedPrivacy by remember { mutableStateOf("public") }
    var isSending by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }

    // Textos de privacidad
    val privacyOptions = listOf(
        "public" to stringResource(R.string.progress_privacy_public),
        "followers" to stringResource(R.string.progress_privacy_followers),
        "direct" to stringResource(R.string.progress_privacy_private)
    )
    val currentPrivacyLabel = privacyOptions.firstOrNull { it.first == selectedPrivacy }?.second
        ?: stringResource(R.string.progress_privacy_public)

    val pagesLabel = stringResource(R.string.progress_pages)
    val percentLabel = stringResource(R.string.progress_percent)

    // Usar ModalBottomSheet para una experiencia nativa de estilo bottom-sheet
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Título + botón cerrar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.progress_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.progress_btn_cancel)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Progreso: campo + selector de modo ──
            Text(
                text = stringResource(R.string.progress_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = progressInput,
                    onValueChange = { progressInput = it },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Selector de modo (páginas / porcentaje)
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (isPages) pagesLabel else percentLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .width(140.dp)
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(pagesLabel) },
                            onClick = { isPages = true; modeExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(percentLabel) },
                            onClick = { isPages = false; modeExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Checkbox: Publicar en el feed ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = postToFeed,
                    onCheckedChange = { postToFeed = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = stringResource(R.string.progress_post_to_feed),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Comentario ──
            Text(
                text = stringResource(R.string.progress_comment_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                placeholder = { Text(stringResource(R.string.progress_comment_hint)) },
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Alerta de spoiler ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.progress_spoiler_alert),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = { includeSpoiler = !includeSpoiler }) {
                    Icon(
                        if (includeSpoiler) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.progress_spoiler_alert),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (includeSpoiler) {
                OutlinedTextField(
                    value = spoilerText,
                    onValueChange = { spoilerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.progress_spoiler_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Privacidad + Botón Compartir ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dropdown de privacidad
                ExposedDropdownMenuBox(
                    expanded = privacyExpanded,
                    onExpandedChange = { privacyExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = currentPrivacyLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = privacyExpanded) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = privacyExpanded,
                        onDismissRequest = { privacyExpanded = false }
                    ) {
                        privacyOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedPrivacy = value
                                    privacyExpanded = false
                                }
                            )
                        }
                    }
                }

                // Botón Compartir
                Button(
                    onClick = {
                        if (progressInput.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.progress_dialog_hint), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSending = true
                        coroutineScope.launch {
                            try {
                                // 1. Obtener el context (readthrough ID + user ID + localBookId)
                                // getProgressContext automáticamente resuelve la URL local si es federada
                                val progressContext = BookWyrmScraper.getProgressContext(api, activeBookKey)
                                if (progressContext == null) {
                                    Toast.makeText(context, context.getString(R.string.progress_readthrough_not_found), Toast.LENGTH_SHORT).show()
                                    isSending = false
                                    return@launch
                                }
                                
                                val bookId = progressContext.localBookId
                                if (bookId.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.error_book_not_identified), Toast.LENGTH_SHORT).show()
                                    isSending = false
                                    return@launch
                                }

                                // 2. Enviar actualización detallada.
                                // Se usa el valor de la cookie csrftoken (no el token enmascarado
                                // del HTML) para garantizar que coincida con la cookie enviada.
                                val csrfForForm = com.ferlagod.rocinante.data.api.NetworkClient
                                    .currentCsrfToken() ?: progressContext.csrfToken
                                val response = api.updateProgressDetailed(
                                    bookIdPath = bookId,
                                    readthroughId = progressContext.readthroughId,
                                    userId = progressContext.userId,
                                    book = bookId,
                                    progress = progressInput,
                                    progressMode = if (isPages) "PG" else "PCT",
                                    postStatus = if (postToFeed) "on" else "",
                                    privacy = selectedPrivacy,
                                    content = commentText,
                                    contentWarning = if (includeSpoiler) spoilerText else "",
                                    csrfToken = csrfForForm
                                )

                                val isRedirectToLogin = response.code() in 300..399 && response.headers()["Location"]?.contains("login") == true
                                if ((response.isSuccessful || response.code() == 302) && !isRedirectToLogin) {
                                    Toast.makeText(context, context.getString(R.string.progress_success), Toast.LENGTH_SHORT).show()
                                    // Actualización optimista: informar al diálogo del nuevo progreso
                                    // con el valor recién enviado, sin volver a consultar la red.
                                    val newProgress = progressInput.toIntOrNull()
                                    onSuccess(
                                        newProgress?.let {
                                            BookWyrmScraper.ReadingProgressInfo(it, if (isPages) "PG" else "PCT")
                                        }
                                    )
                                } else if (isRedirectToLogin) {
                                    Toast.makeText(context, context.getString(R.string.auth_login_required), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.progress_error, response.code().toString()), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Toast.makeText(context, context.getString(R.string.progress_network_error, e.message), Toast.LENGTH_LONG).show()
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isSending) stringResource(R.string.post_btn_sending)
                               else stringResource(R.string.progress_btn_share),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDialog(
    bookDetails: BookWyrmBookDetails,
    activeBookKey: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var rating by remember { mutableStateOf("") }
    var reviewName by remember { mutableStateOf("") }
    var reviewContent by remember { mutableStateOf("") }
    var includeSpoiler by remember { mutableStateOf(false) }
    var spoilerText by remember { mutableStateOf("") }
    var isSensitive by remember { mutableStateOf(false) }
    var selectedPrivacy by remember { mutableStateOf("public") }
    var isSending by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }
    var ratingExpanded by remember { mutableStateOf(false) }

    val privacyOptions = listOf(
        "public" to stringResource(R.string.progress_privacy_public),
        "followers" to stringResource(R.string.progress_privacy_followers),
        "unlisted" to stringResource(R.string.privacy_unlisted),
        "direct" to stringResource(R.string.progress_privacy_private)
    )
    val currentPrivacyLabel = privacyOptions.firstOrNull { it.first == selectedPrivacy }?.second ?: stringResource(R.string.progress_privacy_public)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.review_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.book_close))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rating
            Text(
                text = stringResource(R.string.review_rating_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = ratingExpanded,
                onExpandedChange = { ratingExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (rating.isBlank()) stringResource(R.string.rating_none) else stringResource(R.string.rating_stars, rating),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ratingExpanded) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                ExposedDropdownMenu(
                    expanded = ratingExpanded,
                    onDismissRequest = { ratingExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rating_none)) }, onClick = { rating = ""; ratingExpanded = false })
                    (1..5).forEach { stars ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rating_stars, stars.toString())) },
                            onClick = { rating = stars.toString(); ratingExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Review Name
            OutlinedTextField(
                value = reviewName,
                onValueChange = { reviewName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.review_title_label)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content
            Text(
                text = stringResource(R.string.review_content_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = reviewContent,
                onValueChange = { reviewContent = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sensitive toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isSensitive,
                    onCheckedChange = { isSensitive = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = stringResource(R.string.review_sensitive_label),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Spoiler alert
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.progress_spoiler_alert),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = { includeSpoiler = !includeSpoiler }) {
                    Icon(
                        if (includeSpoiler) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.progress_spoiler_alert),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (includeSpoiler) {
                OutlinedTextField(
                    value = spoilerText,
                    onValueChange = { spoilerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.progress_spoiler_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Privacy & Submit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = privacyExpanded,
                    onExpandedChange = { privacyExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = currentPrivacyLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = privacyExpanded) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = privacyExpanded,
                        onDismissRequest = { privacyExpanded = false }
                    ) {
                        privacyOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedPrivacy = value
                                    privacyExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        isSending = true
                        coroutineScope.launch {
                            try {
                                val reviewContext = BookWyrmScraper.getReviewContext(api, activeBookKey)
                                if (reviewContext == null) {
                                    Toast.makeText(context, context.getString(R.string.review_missing_data), Toast.LENGTH_SHORT).show()
                                    isSending = false
                                    return@launch
                                }

                                val response = if (reviewContent.isBlank()) {
                                    api.postReviewRating(
                                        book = reviewContext.bookId,
                                        user = reviewContext.userId,
                                        rating = rating.takeIf { it.isNotBlank() },
                                        privacy = selectedPrivacy
                                    )
                                } else {
                                    api.postReview(
                                        book = reviewContext.bookId,
                                        user = reviewContext.userId,
                                        name = reviewName.takeIf { it.isNotBlank() },
                                        content = reviewContent,
                                        rating = rating.takeIf { it.isNotBlank() },
                                        privacy = selectedPrivacy,
                                        contentWarning = spoilerText.takeIf { includeSpoiler && it.isNotBlank() },
                                        sensitive = if (isSensitive) "on" else null
                                    )
                                }

                                if (response.isSuccessful) {
                                    val bodyString = response.body()?.string() ?: ""
                                    if (bodyString.contains("class=\"errorlist\"") || bodyString.contains("error_1_id_")) {
                                        Toast.makeText(context, context.getString(R.string.post_validation_error), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.review_success), Toast.LENGTH_SHORT).show()
                                        onSuccess()
                                    }
                                } else if (response.code() == 302) {
                                    val location = response.headers()["Location"]
                                    if (location?.contains("/login") == true) {
                                        Toast.makeText(context, context.getString(R.string.session_expired), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.review_success), Toast.LENGTH_SHORT).show()
                                        onSuccess()
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.review_error, response.code().toString()), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Toast.makeText(context, context.getString(R.string.error_network, e.message), Toast.LENGTH_LONG).show()
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (isSending) stringResource(R.string.post_btn_sending) else stringResource(R.string.review_btn_publish),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotationDialog(
    bookDetails: BookWyrmBookDetails,
    activeBookKey: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pageText by remember { mutableStateOf("") }
    var quoteText by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var includeSpoiler by remember { mutableStateOf(false) }
    var spoilerText by remember { mutableStateOf("") }
    var isSensitive by remember { mutableStateOf(false) }
    var selectedPrivacy by remember { mutableStateOf("public") }
    var isSending by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }

    val privacyOptions = listOf(
        "public" to stringResource(R.string.progress_privacy_public),
        "followers" to stringResource(R.string.progress_privacy_followers),
        "unlisted" to stringResource(R.string.privacy_unlisted),
        "direct" to stringResource(R.string.progress_privacy_private)
    )
    val currentPrivacyLabel = privacyOptions.firstOrNull { it.first == selectedPrivacy }?.second ?: stringResource(R.string.progress_privacy_public)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quotation_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.book_close))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Page text
            Text(
                text = stringResource(R.string.quotation_page_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pageText,
                onValueChange = { pageText = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quote text
            Text(
                text = stringResource(R.string.quotation_quote_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = quoteText,
                onValueChange = { quoteText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Content text
            Text(
                text = stringResource(R.string.quotation_content_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = contentText,
                onValueChange = { contentText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sensitive toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isSensitive,
                    onCheckedChange = { isSensitive = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = stringResource(R.string.review_sensitive_label),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Spoiler alert
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.progress_spoiler_alert),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = { includeSpoiler = !includeSpoiler }) {
                    Icon(
                        if (includeSpoiler) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = stringResource(R.string.progress_spoiler_alert),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (includeSpoiler) {
                OutlinedTextField(
                    value = spoilerText,
                    onValueChange = { spoilerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.progress_spoiler_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Privacy & Submit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = privacyExpanded,
                    onExpandedChange = { privacyExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = currentPrivacyLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = privacyExpanded) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = privacyExpanded,
                        onDismissRequest = { privacyExpanded = false }
                    ) {
                        privacyOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedPrivacy = value
                                    privacyExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        isSending = true
                        coroutineScope.launch {
                            try {
                                val reviewContext = BookWyrmScraper.getReviewContext(api, activeBookKey)
                                if (reviewContext == null) {
                                    Toast.makeText(context, context.getString(R.string.quotation_missing_data), Toast.LENGTH_SHORT).show()
                                    isSending = false
                                    return@launch
                                }

                                val finalContent = buildString {
                                    if (pageText.isNotBlank()) {
                                        append(context.getString(R.string.quotation_page_format, pageText))
                                        if (contentText.isNotBlank()) append("\n\n")
                                    }
                                    if (contentText.isNotBlank()) {
                                        if (pageText.isNotBlank()) append(context.getString(R.string.quotation_quote_prefix))
                                        append(contentText)
                                    }
                                }

                                val response = api.postQuotation(
                                    book = reviewContext.bookId,
                                    user = reviewContext.userId,
                                    quote = quoteText,
                                    content = finalContent,
                                    privacy = selectedPrivacy,
                                    contentWarning = spoilerText.takeIf { includeSpoiler && it.isNotBlank() },
                                    sensitive = if (isSensitive) "on" else null
                                )

                                if (response.isSuccessful) {
                                    // Comprobar si devolvió 200 pero es el HTML del formulario con errores (el HTML tiene <form y no es JSON)
                                    val bodyString = response.body()?.string() ?: ""
                                    if (bodyString.contains("class=\"errorlist\"") || bodyString.contains("error_1_id_")) {
                                        Toast.makeText(context, context.getString(R.string.post_validation_error), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.quotation_success), Toast.LENGTH_SHORT).show()
                                        onSuccess()
                                    }
                                } else if (response.code() == 302) {
                                    val location = response.headers()["Location"]
                                    if (location?.contains("/login") == true) {
                                        Toast.makeText(context, context.getString(R.string.session_expired), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.quotation_success), Toast.LENGTH_SHORT).show()
                                        onSuccess()
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.quotation_error, response.code().toString()), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Toast.makeText(context, context.getString(R.string.error_network, e.message), Toast.LENGTH_LONG).show()
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    enabled = !isSending && (quoteText.isNotBlank() || contentText.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (isSending) stringResource(R.string.post_btn_sending) else stringResource(R.string.quotation_btn_publish),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Diálogo para visualizar una reseña completa (review) de un libro en detalle,
 * mostrando su texto íntegro, calificación, y autor.
 *
 * @param review Objeto de actividad que contiene la reseña.
 * @param instanceHostUrl URL base de la instancia.
 * @param api Cliente API para acciones adicionales.
 * @param context Contexto de Android (para intents o toasts).
 * @param coroutineScope Scope de corrutinas para llamadas asíncronas desde la UI.
 * @param onDismiss Callback ejecutado al cerrar el diálogo.
 */
@Composable
fun ReviewDetailDialog(
    review: ActivityPubActivity,
    instanceHostUrl: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val rawContent = review.objectData?.content ?: review.content ?: ""
    val cleanReview = HtmlUtils.stripHtml(rawContent).trim()
    val rating = review.objectData?.rating
    val dateStr = review.published ?: ""
    val shortDate = when {
        dateStr.isBlank() -> stringResource(R.string.book_review_date_unknown)
        dateStr.contains("T") -> dateStr.substringBefore("T")
        else -> dateStr
    }
    val reviewerName = review.name ?: stringResource(R.string.progress_privacy_private)

    // ── Estado de seguimiento del autor de la reseña ──
    val actorUrl = review.actor.orEmpty()
    // Handle resuelto del autor (@usuario@instancia); null mientras se resuelve o si falla.
    var resolvedHandle by remember(actorUrl) { mutableStateOf<String?>(null) }
    var isResolvingHandle by remember(actorUrl) { mutableStateOf(actorUrl.isNotBlank()) }
    var isFollowing by remember(actorUrl) { mutableStateOf(false) }
    var isFollowPending by remember(actorUrl) { mutableStateOf(false) }

    LaunchedEffect(actorUrl) {
        if (actorUrl.isBlank()) {
            isResolvingHandle = false
            return@LaunchedEffect
        }
        resolvedHandle = BookWyrmScraper.resolveActorHandle(api, actorUrl, instanceHostUrl)
        isResolvingHandle = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.review_detail_title),
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
                // Header: Avatar + User + Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val avatarUrl = review.actorAvatarUrl
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = stringResource(R.string.profile_avatar_desc),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.profile_default_avatar_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reviewerName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        resolvedHandle?.let { handle ->
                            Text(
                                text = handle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = shortDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Botón para seguir / dejar de seguir al autor de la reseña.
                    val currentHandle = resolvedHandle
                    when {
                        isResolvingHandle || isFollowPending -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        currentHandle != null -> {
                            val cleanHandle = currentHandle.removePrefix("@")
                            if (isFollowing) {
                                OutlinedButton(
                                    onClick = {
                                        isFollowPending = true
                                        coroutineScope.launch {
                                            try {
                                                val response = api.unfollowUser(cleanHandle)
                                                if (response.isSuccessful || response.code() in 300..399) {
                                                    isFollowing = false
                                                    Toast.makeText(context, context.getString(R.string.unfollow_success), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.error_server, response.code().toString()), Toast.LENGTH_SHORT).show()
                                                }

                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                Toast.makeText(context, context.getString(R.string.error_network, e.message), Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isFollowPending = false
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
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
                                                    Toast.makeText(context, context.getString(R.string.follow_success), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, context.getString(R.string.error_server, response.code().toString()), Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                Toast.makeText(context, context.getString(R.string.error_network, e.message), Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isFollowPending = false
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.follow_btn_follow))
                                }
                            }
                        }
                    }
                }

                if (rating != null && rating in 1..5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★".repeat(rating) + "☆".repeat(5 - rating),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.rating_stars, rating.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                if (cleanReview.isNotBlank()) {
                    Text(
                        text = cleanReview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.review_no_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.book_close))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookActivityDialog(
    activeBookKey: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var activities by remember { mutableStateOf<List<com.ferlagod.rocinante.data.model.TimelineUiItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val sessionStorage = remember { com.ferlagod.rocinante.data.local.SessionStorage(context) }
    val currentSession by sessionStorage.sessionFlow.collectAsState(initial = null)

    LaunchedEffect(activeBookKey, currentSession) {
        if (currentSession == null) return@LaunchedEffect
        isLoading = true
        try {
            val username = currentSession?.username
            val instanceUrl = currentSession?.instanceUrl ?: ""
            if (username.isNullOrBlank() || instanceUrl.isBlank()) {
                errorMessage = context.getString(R.string.session_invalid)
                isLoading = false
                return@LaunchedEffect
            }
            
            val cleanInstance = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val outboxUrl = "${cleanInstance.trimEnd('/')}/user/${username.removePrefix("@").substringBefore("@")}/outbox"
            
            val userRepository = com.ferlagod.rocinante.data.repository.UserRepository(api, java.util.concurrent.ConcurrentHashMap())
            val timelineRepo = com.ferlagod.rocinante.data.repository.TimelineRepository(api, userRepository, context)
            
            val allOutbox = timelineRepo.loadOutboxActivities(
                outboxUrl = outboxUrl,
                actorNameHint = username,
                actorAvatarHint = null,
                maxPages = 5
            )
            
            val localBookId = com.ferlagod.rocinante.utils.BookWyrmUtils.extractBookId(activeBookKey)
            
            val filtered = allOutbox.filter { item ->
                val itemBookId = item.bookUrl?.let { com.ferlagod.rocinante.utils.BookWyrmUtils.extractBookId(it) } ?: ""
                val itemObjectId = com.ferlagod.rocinante.utils.BookWyrmUtils.extractBookId(item.objectId)
                itemBookId == localBookId || itemObjectId == localBookId || item.bookUrl?.contains(localBookId) == true || item.objectId.contains(localBookId)
            }
            activities = filtered
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            errorMessage = e.message
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Mis publicaciones", fontWeight = FontWeight.Bold)
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Text(text = errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
            } else if (activities.isEmpty()) {
                Text("No has publicado nada sobre este libro recientemente.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activities) { activity ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = activity.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activity.content,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val dateStr = activity.published
                                val shortDate = when {
                                    dateStr.isBlank() -> "Unknown date"
                                    dateStr.contains("T") -> dateStr.substringBefore("T")
                                    else -> dateStr
                                }
                                Text(
                                    text = shortDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
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

