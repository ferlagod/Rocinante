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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.api.OpenLibraryLookup
import com.ferlagod.rocinante.utils.IsbnUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Comprime la imagen de portada seleccionada a un tamaño adecuado para la web (máximo 1200px),
 * reduciendo el peso de ~10 MB a ~150 KB para evitar cortes por timeout en redes móviles.
 *
 * Decodificar y comprimir una foto grande lleva su tiempo: se hace fuera del hilo principal
 * (antes congelaba la pantalla mientras tanto). Se respeta la orientación EXIF, porque las
 * fotos de la cámara guardan los píxeles girados y la marca aparte, y sin aplicarla la
 * portada subía tumbada.
 */
private suspend fun compressCoverImage(context: Context, uri: Uri): Pair<ByteArray, String>? {
    val cr = context.contentResolver
    return compressCover(open = { cr.openInputStream(uri) }, fallbackMime = cr.getType(uri))
}

/** Lo mismo con una imagen ya descargada (la portada de OpenLibrary). */
private suspend fun compressCoverBytes(bytes: ByteArray): Pair<ByteArray, String>? =
    compressCover(open = { bytes.inputStream() }, fallbackMime = "image/jpeg")

private suspend fun compressCover(
    open: () -> InputStream?,
    fallbackMime: String?,
    maxDimension: Int = 1200,
    quality: Int = 85
): Pair<ByteArray, String>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
    runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) {
            val raw = open()?.use { it.readBytes() } ?: return@runCatching null
            return@runCatching raw to (fallbackMime ?: "image/jpeg")
        }

        var sampleSize = 1
        var w = origW
        var h = origH
        while (w > maxDimension * 2 || h > maxDimension * 2) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return@runCatching null

        val bitmap = applyExifOrientation(open, decoded)

        val finalBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        finalBitmap.recycle()
        out.toByteArray() to "image/jpeg"
    }.getOrNull()
}

/**
 * Gira o voltea [bitmap] según la marca EXIF de la imagen. Si no hay marca, no se puede leer
 * o no hace falta tocar nada, devuelve el mismo bitmap.
 */
private fun applyExifOrientation(open: () -> InputStream?, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        open()?.use {
            android.media.ExifInterface(it).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        }
    }.getOrNull() ?: return bitmap

    val matrix = android.graphics.Matrix()
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    }.getOrDefault(bitmap)
}

/** Guarda la lista de autores al girar (una SnapshotStateList no se guarda sola). */
private val authorListSaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() }
)

/**
 * Diálogo para la creación manual de un libro en la instancia de BookWyrm.
 *
 * Soporta entrada de metadatos completos con desplegables controlados para formato e idioma,
 * varios autores, rellenar los datos por ISBN desde OpenLibrary, aviso si el ISBN ya está en
 * la instancia, selección opcional de portada desde el dispositivo, las preguntas de
 * confirmación de la instancia (autores, obra, serie), ponerlo en una estantería al crearlo y
 * opción de crear y seguir añadiendo más libros consecutivamente.
 *
 * @param initialTitle Título inicial (ej. proveniente de la búsqueda si no es un ISBN).
 * @param initialIsbn ISBN inicial (ej. proveniente del escáner de código de barras o búsqueda numérica).
 * @param instanceUrl Dirección de la instancia.
 * @param api Cliente API configurado para la instancia.
 * @param onBookCreated Callback invocado cuando el libro ha sido creado satisfactoriamente en el servidor,
 *                      con la URL completa del nuevo libro. También para abrir el que ya existía.
 * @param onDismiss Callback para cerrar el diálogo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateBookDialog(
    instanceUrl: String,
    api: BookWyrmApi,
    initialTitle: String = "",
    initialIsbn: String = "",
    onBookCreated: (bookUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val viewModel: CreateBookViewModel = androidx.hilt.navigation.compose.hiltViewModel(key = "create_book_dialog")
    // Una clave por apertura del diálogo; al girar se conserva y el ViewModel sigue con lo suyo.
    val sessionKey = rememberSaveable { java.util.UUID.randomUUID().toString() }
    remember(sessionKey) { viewModel.start(sessionKey) }
    val vmState by viewModel.state.collectAsStateWithLifecycle()
    val isWorking = vmState.isWorking
    val confirmation = vmState.confirmation

    // Opciones oficiales de formato en BookWyrm (schema.org/BookFormatType)
    val formatOptions = remember {
        listOf(
            "" to R.string.create_book_format_unspecified,
            "Paperback" to R.string.create_book_format_paperback,
            "Hardcover" to R.string.create_book_format_hardcover,
            "EBook" to R.string.create_book_format_ebook,
            "AudiobookFormat" to R.string.create_book_format_audiobook,
            "GraphicNovel" to R.string.create_book_format_comic
        )
    }

    // Opciones oficiales de idioma en BookWyrm
    val languageOptions = remember {
        listOf(
            "spa" to R.string.create_book_lang_es,
            "eng" to R.string.create_book_lang_en,
            "cat" to R.string.create_book_lang_ca,
            "glg" to R.string.create_book_lang_gl,
            "eus" to R.string.create_book_lang_eu,
            "fra" to R.string.create_book_lang_fr,
            "deu" to R.string.create_book_lang_de,
            "ita" to R.string.create_book_lang_it,
            "por" to R.string.create_book_lang_pt
        )
    }

    val shelfOptions = remember {
        listOf(
            "" to R.string.create_book_shelf_none,
            "to-read" to R.string.shelf_chip_to_read,
            "reading" to R.string.shelf_chip_reading
        )
    }

    // Campos del formulario principal persistidos frente a giros o cambios de app
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    val authors = rememberSaveable(saver = authorListSaver) { mutableStateListOf("") }
    var isbn by rememberSaveable { mutableStateOf(initialIsbn) }
    var pages by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var publishedDate by rememberSaveable { mutableStateOf("") }
    var publishers by rememberSaveable { mutableStateOf("") }
    var series by rememberSaveable { mutableStateOf("") }
    var seriesNumber by rememberSaveable { mutableStateOf("") }
    // Varios idiomas, separados por comas como los manda el formulario web.
    var selectedLanguageCodes by rememberSaveable { mutableStateOf("") }
    var selectedFormatCode by rememberSaveable { mutableStateOf("") }
    var shelfSlug by rememberSaveable { mutableStateOf("") }

    // Estados de desplegables
    var formatDropdownExpanded by remember { mutableStateOf(false) }
    var shelfDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Imagen de portada (persistida como String para sobrevivir a recreation de Activity).
    // La local tiene prioridad; la remota es la que trae OpenLibrary al rellenar por ISBN.
    var coverUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val coverUri = remember(coverUriString) { coverUriString?.let { Uri.parse(it) } }
    var remoteCoverUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coverUriString = uri.toString()
        }
    }

    var showMoreDetails by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedLanguages = selectedLanguageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun resetForm() {
        title = ""
        authors.clear()
        authors.add("")
        isbn = ""
        pages = ""
        subtitle = ""
        description = ""
        publishedDate = ""
        publishers = ""
        series = ""
        seriesNumber = ""
        selectedLanguageCodes = ""
        selectedFormatCode = ""
        coverUriString = null
        remoteCoverUrl = null
        errorMessage = null
        // La estantería se queda: al dar de alta varios seguidos suelen ir al mismo sitio.
    }

    // La portada se prepara justo antes de enviarla, ya fuera del hilo principal.
    val coverProvider: suspend () -> Pair<ByteArray?, String> = {
        val local = coverUri
        val remote = remoteCoverUrl
        when {
            local != null -> compressCoverImage(appContext, local) ?: (null to "image/jpeg")
            remote != null -> OpenLibraryLookup.downloadCover(remote)?.let { compressCoverBytes(it) }
                ?: (null to "image/jpeg")
            else -> null to "image/jpeg"
        }
    }

    fun submitInitialForm(andAddAnother: Boolean, checkDuplicates: Boolean = true) {
        val trimmedTitle = title.trim()
        val authorNames = authors.map { it.trim() }.filter { it.isNotEmpty() }

        if (trimmedTitle.isEmpty()) {
            errorMessage = context.getString(R.string.create_book_title_required)
            return
        }
        if (authorNames.isEmpty()) {
            errorMessage = context.getString(R.string.create_book_author_required)
            return
        }

        // Si el usuario escribe DD/MM/AAAA o DD-MM-AAAA, normalizar a AAAA-MM-DD para Django
        val normalizedDate = run {
            val raw = publishedDate.trim()
            val dmyRegex = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$""")
            val match = dmyRegex.find(raw)
            if (match != null) {
                val (d, m, y) = match.destructured
                "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
            } else {
                raw
            }
        }

        errorMessage = null
        viewModel.submit(
            api = api,
            instanceUrl = instanceUrl,
            request = CreateBookViewModel.Request(
                title = trimmedTitle,
                authors = authorNames,
                subtitle = subtitle.trim(),
                isbn = isbn.trim(),
                pages = pages.trim(),
                publishedDate = normalizedDate,
                description = description.trim(),
                languages = selectedLanguages.joinToString(", "),
                physicalFormat = selectedFormatCode,
                publishers = publishers.trim(),
                series = series.trim(),
                seriesNumber = seriesNumber.trim(),
                shelfSlug = shelfSlug,
                andAddAnother = andAddAnother
            ),
            checkDuplicates = checkDuplicates,
            cover = coverProvider
        )
    }

    // Lo que termina en el ViewModel se atiende aquí, una sola vez.
    LaunchedEffect(vmState.event) {
        when (val event = vmState.event) {
            is CreateBookViewModel.Event.Created -> {
                if (event.shelved == false) {
                    Toast.makeText(context, context.getString(R.string.create_book_shelve_failed), Toast.LENGTH_LONG).show()
                }
                if (event.andAddAnother) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.create_book_created_toast, event.title),
                        Toast.LENGTH_SHORT
                    ).show()
                    resetForm()
                } else {
                    onBookCreated(event.bookUrl)
                    onDismiss()
                }
            }
            is CreateBookViewModel.Event.Failed -> {
                errorMessage = event.message.ifBlank { context.getString(R.string.create_book_error_generic) }
            }
            is CreateBookViewModel.Event.LookupDone -> {
                val data = event.data
                if (data == null) {
                    Toast.makeText(context, context.getString(R.string.create_book_fill_not_found), Toast.LENGTH_SHORT).show()
                } else {
                    // Solo lo que esté vacío: lo que ya escribió el usuario manda.
                    if (title.isBlank()) title = data.title
                    if (subtitle.isBlank()) subtitle = data.subtitle
                    if (authors.all { it.isBlank() } && data.authors.isNotEmpty()) {
                        authors.clear()
                        authors.addAll(data.authors)
                    }
                    if (pages.isBlank()) data.pages?.let { pages = it.toString() }
                    if (publishedDate.isBlank()) publishedDate = OpenLibraryLookup.isoDateOrYear(data.publishedDate)
                    if (publishers.isBlank()) publishers = data.publishers.joinToString(", ")
                    if (coverUriString == null && remoteCoverUrl == null) remoteCoverUrl = data.coverUrl
                    if (data.subtitle.isNotBlank() || data.publishers.isNotEmpty() || data.publishedDate.isNotBlank()) {
                        showMoreDetails = true
                    }
                    Toast.makeText(context, context.getString(R.string.create_book_fill_done), Toast.LENGTH_SHORT).show()
                }
            }
            null -> Unit
        }
        if (vmState.event != null) viewModel.consumeEvent()
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        publishedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.progress_btn_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isWorking) onDismiss()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false
        ),
        title = {
            Text(
                text = if (confirmation != null) {
                    stringResource(R.string.create_book_author_choice_title)
                } else {
                    stringResource(R.string.create_book_title)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                vmState.duplicateUrl?.let { existingUrl ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.create_book_duplicate_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.create_book_duplicate_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    viewModel.dismissDuplicate()
                                    onBookCreated(existingUrl)
                                    onDismiss()
                                }) { Text(stringResource(R.string.create_book_duplicate_open)) }
                                TextButton(onClick = {
                                    viewModel.dismissDuplicate()
                                    submitInitialForm(andAddAnother = false, checkDuplicates = false)
                                }) { Text(stringResource(R.string.create_book_duplicate_create_anyway)) }
                            }
                        }
                    }
                }

                if (confirmation != null) {
                    // Pantalla 2: las preguntas de la instancia, una por grupo
                    val groups = confirmation.options.groupBy { it.field }
                    groups.forEach { (field, groupOptions) ->
                        val typedName = groupOptions.firstOrNull { it.kind == BookWyrmScraper.AuthorOption.Kind.NEW }?.value.orEmpty()
                        Text(
                            text = when {
                                field.startsWith("author_match-") ->
                                    stringResource(R.string.create_book_author_choice_explanation, typedName)
                                field == "parent_work" -> stringResource(R.string.create_book_work_choice)
                                else -> stringResource(R.string.create_book_series_choice)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            groupOptions.forEach { option ->
                                val selected = confirmation.selections[field] == option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable(enabled = !isWorking) { viewModel.select(option) }
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { viewModel.select(option) },
                                        enabled = !isWorking
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (field.startsWith("author_match-") &&
                                                option.kind == BookWyrmScraper.AuthorOption.Kind.NEW
                                            ) {
                                                stringResource(R.string.missing_authors_choice_new, option.value)
                                            } else {
                                                option.label
                                            },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        val detail = when (option.kind) {
                                            BookWyrmScraper.AuthorOption.Kind.ISNI ->
                                                listOf(stringResource(R.string.missing_authors_choice_isni), option.help)
                                                    .filter { it.isNotBlank() }.joinToString(" · ")
                                            else -> option.help
                                        }
                                        if (detail.isNotBlank()) {
                                            Text(
                                                text = detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Pantalla 1: Formulario principal de creación
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.create_book_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWorking
                    )

                    // Un campo por autor: con comas no se distinguiría «Tolkien, J. R. R.» de dos.
                    authors.forEachIndexed { index, value ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { authors[index] = it },
                            label = { Text(stringResource(R.string.create_book_field_author)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isWorking,
                            trailingIcon = if (authors.size > 1) {
                                {
                                    IconButton(onClick = { authors.removeAt(index) }, enabled = !isWorking) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.create_book_remove_author)
                                        )
                                    }
                                }
                            } else null
                        )
                    }
                    TextButton(
                        onClick = { authors.add("") },
                        enabled = !isWorking,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_book_add_author))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Teclado de texto: el numérico no tiene la «X» de control de los ISBN-10.
                        // Solo se admiten los caracteres de un ISBN.
                        // Se avisa al tener ya la longitud de un ISBN, no mientras se teclea.
                        val isbnLooksWrong = IsbnUtils.normalize(isbn).length
                            .let { it == 10 || it >= 13 } &&
                            !IsbnUtils.hasValidChecksum(isbn)
                        OutlinedTextField(
                            value = isbn,
                            onValueChange = { value ->
                                isbn = value.filter { it.isDigit() || it == 'X' || it == 'x' || it == '-' || it == ' ' }
                                    .uppercase()
                            },
                            label = { Text(stringResource(R.string.create_book_field_isbn)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                            ),
                            singleLine = true,
                            isError = isbnLooksWrong,
                            supportingText = if (isbnLooksWrong) {
                                { Text(stringResource(R.string.create_book_isbn_check_warning)) }
                            } else null,
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking
                        )
                        OutlinedTextField(
                            value = pages,
                            onValueChange = { pages = it },
                            label = { Text(stringResource(R.string.create_book_field_pages)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            enabled = !isWorking
                        )
                    }

                    // Rellenar por ISBN: solo al pulsarlo, que es cuando se manda el ISBN a OpenLibrary.
                    TextButton(
                        onClick = { viewModel.lookupIsbn(isbn) },
                        enabled = !isWorking && !vmState.isLookingUp && IsbnUtils.isIsbn(isbn),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        if (vmState.isLookingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.create_book_fill_from_isbn))
                    }

                    // Selector de formato (desplegable)
                    ExposedDropdownMenuBox(
                        expanded = formatDropdownExpanded && !isWorking,
                        onExpandedChange = { if (!isWorking) formatDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val formatLabel = formatOptions.firstOrNull { it.first == selectedFormatCode }?.let {
                            stringResource(it.second)
                        } ?: stringResource(R.string.create_book_format_unspecified)

                        OutlinedTextField(
                            value = formatLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.create_book_field_format)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            enabled = !isWorking
                        )

                        ExposedDropdownMenu(
                            expanded = formatDropdownExpanded && !isWorking,
                            onDismissRequest = { formatDropdownExpanded = false }
                        ) {
                            formatOptions.forEach { (code, stringRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(stringRes)) },
                                    onClick = {
                                        selectedFormatCode = code
                                        formatDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Estantería en la que ponerlo nada más crearlo
                    ExposedDropdownMenuBox(
                        expanded = shelfDropdownExpanded && !isWorking,
                        onExpandedChange = { if (!isWorking) shelfDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val shelfLabel = shelfOptions.firstOrNull { it.first == shelfSlug }?.let {
                            stringResource(it.second)
                        } ?: stringResource(R.string.create_book_shelf_none)

                        OutlinedTextField(
                            value = shelfLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.create_book_shelf_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shelfDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            enabled = !isWorking
                        )

                        ExposedDropdownMenu(
                            expanded = shelfDropdownExpanded && !isWorking,
                            onDismissRequest = { shelfDropdownExpanded = false }
                        ) {
                            shelfOptions.forEach { (slug, stringRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(stringRes)) },
                                    onClick = {
                                        shelfSlug = slug
                                        shelfDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Selector de portada
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val previewModel: Any? = coverUri ?: remoteCoverUrl
                        if (previewModel != null) {
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 76.dp)
                                    .clip(MaterialTheme.shapes.small)
                            ) {
                                AsyncImage(
                                    model = previewModel,
                                    contentDescription = stringResource(R.string.create_book_cover_label),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isWorking,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.create_book_cover_change))
                                }
                                TextButton(
                                    onClick = {
                                        coverUriString = null
                                        remoteCoverUrl = null
                                    },
                                    enabled = !isWorking,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.create_book_cover_remove))
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.create_book_cover_select))
                            }
                        }
                    }

                    // Botón para desplegar más detalles
                    TextButton(
                        onClick = { showMoreDetails = !showMoreDetails },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                            text = if (showMoreDetails) {
                                stringResource(R.string.create_book_less_details)
                            } else {
                                stringResource(R.string.create_book_more_details)
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = showMoreDetails) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = subtitle,
                                onValueChange = { subtitle = it },
                                label = { Text(stringResource(R.string.create_book_field_subtitle)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking
                            )

                            OutlinedTextField(
                                value = publishedDate,
                                onValueChange = { publishedDate = it },
                                label = { Text(stringResource(R.string.create_book_field_published_date)) },
                                placeholder = { Text("AAAA o AAAA-MM-DD") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }, enabled = !isWorking) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = stringResource(R.string.create_book_pick_date)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking
                            )

                            OutlinedTextField(
                                value = publishers,
                                onValueChange = { publishers = it },
                                label = { Text(stringResource(R.string.create_book_field_publishers)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = series,
                                    onValueChange = { series = it },
                                    label = { Text(stringResource(R.string.create_book_field_series)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                    enabled = !isWorking
                                )
                                OutlinedTextField(
                                    value = seriesNumber,
                                    onValueChange = { seriesNumber = it },
                                    label = { Text(stringResource(R.string.create_book_field_series_number)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    enabled = !isWorking && series.isNotBlank()
                                )
                            }

                            // Idiomas: se pueden marcar varios (un libro bilingüe, por ejemplo).
                            Text(
                                text = stringResource(R.string.create_book_field_languages),
                                style = MaterialTheme.typography.labelLarge
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                languageOptions.forEach { (code, stringRes) ->
                                    val isSelected = code in selectedLanguages
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val updated = if (isSelected) selectedLanguages - code else selectedLanguages + code
                                            selectedLanguageCodes = updated.joinToString(",")
                                        },
                                        label = { Text(stringResource(stringRes)) },
                                        enabled = !isWorking
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text(stringResource(R.string.create_book_field_description)) },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (confirmation != null) {
                        val allAnswered = confirmation.options.map { it.field }.distinct()
                            .all { confirmation.selections.containsKey(it) }
                        Button(
                            onClick = { viewModel.confirm(api, coverProvider) },
                            enabled = !isWorking && allAnswered
                        ) {
                            Text(stringResource(R.string.create_book_confirm_author))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { submitInitialForm(andAddAnother = true) },
                            enabled = !isWorking
                        ) {
                            Text(stringResource(R.string.create_book_save_and_add_another))
                        }
                        Button(
                            onClick = { submitInitialForm(andAddAnother = false) },
                            enabled = !isWorking
                        ) {
                            Text(stringResource(R.string.create_book_save))
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (confirmation != null) {
                        // Volver al formulario de campos si cancela la confirmación
                        viewModel.cancelConfirmation()
                    } else {
                        onDismiss()
                    }
                },
                enabled = !isWorking
            ) {
                Text(
                    if (confirmation != null) {
                        stringResource(R.string.progress_btn_cancel)
                    } else {
                        stringResource(R.string.book_close)
                    }
                )
            }
        }
    )
}
