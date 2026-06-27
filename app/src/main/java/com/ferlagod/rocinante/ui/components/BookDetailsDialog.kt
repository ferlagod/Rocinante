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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.ActivityPubActivity
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmBookDetails
import com.ferlagod.rocinante.data.api.NetworkClient
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
    onShelved: (() -> Unit)? = null
) {
    var showProgressDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showQuotationDialog by remember { mutableStateOf(false) }
    var selectedReviewForDetail by remember { mutableStateOf<ActivityPubActivity?>(null) }

    // Reseñas ordenadas de más reciente a más antigua. BookWyrm renderiza la fecha en
    // formato legible (p. ej. "Aug. 22, 2024"), así que se parsea a una fecha comparable.
    // Las reseñas con fecha no reconocida se colocan al final.
    val sortedReviews = remember(reviews) {
        reviews.sortedByDescending {
            BookWyrmUtils.parseReviewDate(it.published) ?: java.time.LocalDate.MIN
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = bookDetails.title ?: stringResource(R.string.book_details_fallback),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val detailCoverUrl = bookDetails.cover?.url ?: fallbackCoverUrl
                if (detailCoverUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AsyncImage(
                            model = detailCoverUrl,
                            contentDescription = stringResource(R.string.book_cover_detail_desc),
                            modifier = Modifier
                                .width(110.dp)
                                .height(165.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (bookDetails.publishedDate != null) {
                    Text(
                        text = stringResource(R.string.book_published_date, bookDetails.publishedDate),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (bookDetails.pages != null) {
                    Text(
                        text = stringResource(R.string.book_pages, bookDetails.pages.toString()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val rawDesc = bookDetails.description ?: stringResource(R.string.book_no_description)
                val cleanDesc = HtmlUtils.stripHtml(rawDesc)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.book_change_shelf),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val targetShelves = listOf(
                                Triple("to-read", stringResource(R.string.shelf_chip_to_read), stringResource(R.string.shelf_toast_pending)),
                                Triple("reading", stringResource(R.string.shelf_chip_reading), stringResource(R.string.shelf_toast_reading)),
                                Triple("read", stringResource(R.string.shelf_chip_read), stringResource(R.string.shelf_toast_read))
                            )

                            targetShelves.forEach { (slug, label, toastLabel) ->
                                if (slug != currentShelf) {
                                    SuggestionChip(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    val localUrl = NetworkClient.resolveLocalBookUrl(api, activeBookKey) ?: activeBookKey
                                                    val bookId = BookWyrmUtils.extractBookId(localUrl)
                                                    if (bookId.isBlank()) {
                                                        Toast.makeText(context, context.getString(R.string.error_book_not_identified), Toast.LENGTH_SHORT).show()
                                                        return@launch
                                                    }

                                                    val statusMap = mapOf(
                                                        "to-read" to "want",
                                                        "reading" to "start",
                                                        "read" to "finish"
                                                    )
                                                    val mappedStatus = statusMap[slug]
                                                    
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
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        if (currentShelf == "reading") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showProgressDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.book_update_progress))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showQuotationDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.book_add_quote))
                            }
                            OutlinedButton(
                                onClick = { showReviewDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.book_write_review))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.book_synopsis),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cleanDesc,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.book_community_reviews),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (sortedReviews.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.book_no_reviews),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        items(sortedReviews) { review ->
                            // BookWyrm puede devolver la reseña en la raíz de la actividad (ActivityStreams extendido)
                            // o dentro del envoltorio "objectData" (ActivityStreams estándar). Se comprueban ambas vías.
                            val rawContent = review.objectData?.content ?: review.content ?: ""
                            val cleanReview = HtmlUtils.stripHtml(rawContent).trim()

                            // El campo de calificación (rating) también puede estar en la raíz o anidado
                            val rating = review.objectData?.rating

                            // Se descartan objetos de actividad vacíos (e.g. tipo "Announce" / Boost sin texto)
                            if (cleanReview.isNotBlank() || rating != null) {
                                ElevatedCard(
                                    onClick = { selectedReviewForDetail = review },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Avatar del autor
                                        val avatarUrl = review.actorAvatarUrl
                                        if (!avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = stringResource(R.string.profile_avatar_desc),
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Surface(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape),
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

                                        // Contenedor de texto y puntuación
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

    // ── Diálogo de progreso de lectura (rediseñado según la UI de BookWyrm) ──
    if (showProgressDialog) {
        ReadingProgressDialog(
            bookDetails = bookDetails,
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showProgressDialog = false },
            onSuccess = {
                showProgressDialog = false
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
    onSuccess: () -> Unit
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
                                val progressContext = NetworkClient.getProgressContext(api, activeBookKey)
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

                                // 2. Enviar actualización detallada
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
                                    contentWarning = if (includeSpoiler) spoilerText else ""
                                )

                                val isRedirectToLogin = response.code() in 300..399 && response.headers()["Location"]?.contains("login") == true
                                if ((response.isSuccessful || response.code() == 302) && !isRedirectToLogin) {
                                    Toast.makeText(context, context.getString(R.string.progress_success), Toast.LENGTH_SHORT).show()
                                    onSuccess()
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
                                val reviewContext = NetworkClient.getReviewContext(api, activeBookKey)
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

                                if (response.isSuccessful || response.code() == 302) {
                                    Toast.makeText(context, context.getString(R.string.review_success), Toast.LENGTH_SHORT).show()
                                    onSuccess()
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
                                val reviewContext = NetworkClient.getReviewContext(api, activeBookKey)
                                if (reviewContext == null) {
                                    Toast.makeText(context, context.getString(R.string.quotation_missing_data), Toast.LENGTH_SHORT).show()
                                    isSending = false
                                    return@launch
                                }

                                val finalContent = buildString {
                                    if (pageText.isNotBlank()) {
                                        append("Pág. $pageText")
                                        if (contentText.isNotBlank()) append("\n\n")
                                    }
                                    if (contentText.isNotBlank()) {
                                        if (pageText.isNotBlank()) append("Cita: ")
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

                                if (response.isSuccessful || response.code() == 302) {
                                    Toast.makeText(context, context.getString(R.string.quotation_success), Toast.LENGTH_SHORT).show()
                                    onSuccess()
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
        resolvedHandle = NetworkClient.resolveActorHandle(api, actorUrl, instanceHostUrl)
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
