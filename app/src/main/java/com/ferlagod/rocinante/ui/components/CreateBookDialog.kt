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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Comprime la imagen de portada seleccionada a un tamaño adecuado para la web (máximo 1200px),
 * reduciendo el peso de ~10 MB a ~150 KB para evitar cortes por timeout en redes móviles.
 */
private fun compressCoverImage(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1200,
    quality: Int = 85
): Pair<ByteArray, String>? {
    return runCatching {
        val cr = context.contentResolver
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) {
            val raw = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val mime = cr.getType(uri) ?: "image/jpeg"
            return raw to mime
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
        val bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null

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
 * Diálogo para la creación manual de un libro en la instancia de BookWyrm.
 *
 * Soporta entrada de metadatos completos con desplegables controlados para formato e idioma,
 * selección opcional de portada desde el dispositivo, desambiguación de autor (antiduplicados),
 * y opción de crear y seguir añadiendo más libros consecutivamente.
 *
 * @param initialTitle Título inicial (ej. proveniente de la búsqueda si no es un ISBN).
 * @param initialIsbn ISBN inicial (ej. proveniente del escáner de código de barras o búsqueda numérica).
 * @param instanceUrl Dirección de la instancia.
 * @param api Cliente API configurado para la instancia.
 * @param onBookCreated Callback invocado cuando el libro ha sido creado satisfactoriamente en el servidor,
 *                      con la URL completa del nuevo libro.
 * @param onDismiss Callback para cerrar el diálogo.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val coroutineScope = rememberCoroutineScope()

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
            "" to R.string.create_book_lang_unspecified,
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

    // Campos del formulario principal persistidos frente a giros o cambios de app
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var author by rememberSaveable { mutableStateOf("") }
    var isbn by rememberSaveable { mutableStateOf(initialIsbn) }
    var pages by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var publishedDate by rememberSaveable { mutableStateOf("") }
    var selectedLanguageCode by rememberSaveable { mutableStateOf("") }
    var selectedFormatCode by rememberSaveable { mutableStateOf("") }

    // Estados de desplegables
    var formatDropdownExpanded by remember { mutableStateOf(false) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    // Imagen de portada (persistida como String para sobrevivir a recreation de Activity)
    var coverUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val coverUri = remember(coverUriString) { coverUriString?.let { Uri.parse(it) } }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coverUriString = uri.toString()
        }
    }

    var showMoreDetails by rememberSaveable { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // Si el usuario quiere guardar y añadir otro a continuación
    var keepAddingAnother by rememberSaveable { mutableStateOf(false) }

    // Estado de desambiguación de autor (paso 2)
    var pendingConfirmationForm by remember { mutableStateOf<BookWyrmScraper.BookEditForm?>(null) }
    var authorCandidates by remember { mutableStateOf<List<BookWyrmScraper.AuthorOption>>(emptyList()) }
    var selectedAuthorOption by remember { mutableStateOf<BookWyrmScraper.AuthorOption?>(null) }

    fun resetForm() {
        title = ""
        author = ""
        isbn = ""
        pages = ""
        subtitle = ""
        description = ""
        publishedDate = ""
        selectedLanguageCode = ""
        selectedFormatCode = ""
        coverUriString = null
        pendingConfirmationForm = null
        authorCandidates = emptyList()
        selectedAuthorOption = null
        errorMessage = null
        keepAddingAnother = false
    }

    fun submitInitialForm(andAddAnother: Boolean = false) {
        val trimmedTitle = title.trim()
        val trimmedAuthor = author.trim()

        if (trimmedTitle.isEmpty()) {
            errorMessage = context.getString(R.string.create_book_title_required)
            return
        }
        if (trimmedAuthor.isEmpty()) {
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
        isWorking = true
        keepAddingAnother = andAddAnother

        coroutineScope.launch {
            val (coverBytes, coverMimeType) = coverUri?.let { uri ->
                compressCoverImage(context, uri)
            } ?: (null to "image/jpeg")

            val result = BookWyrmScraper.createBookManually(
                api = api,
                instanceUrl = instanceUrl,
                title = trimmedTitle,
                authorName = trimmedAuthor,
                subtitle = subtitle.trim(),
                isbn13 = isbn.trim(),
                pages = pages.trim(),
                publishedDate = normalizedDate,
                description = description.trim(),
                languages = selectedLanguageCode,
                physicalFormat = selectedFormatCode,
                coverBytes = coverBytes,
                coverMimeType = coverMimeType
            )

            isWorking = false

            when (result) {
                is BookWyrmScraper.CreateBookResult.Created -> {
                    if (andAddAnother) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.create_book_created_toast, trimmedTitle),
                            Toast.LENGTH_SHORT
                        ).show()
                        resetForm()
                    } else {
                        onBookCreated(result.bookUrl)
                        onDismiss()
                    }
                }
                is BookWyrmScraper.CreateBookResult.NeedsAuthorConfirmation -> {
                    pendingConfirmationForm = result.form
                    authorCandidates = result.options
                    selectedAuthorOption = result.options.firstOrNull()
                }
                is BookWyrmScraper.CreateBookResult.Error -> {
                    errorMessage = result.message.ifBlank {
                        context.getString(R.string.create_book_error_generic)
                    }
                }
            }
        }
    }

    fun submitAuthorConfirmation() {
        val form = pendingConfirmationForm ?: return
        val option = selectedAuthorOption ?: return
        val bookTitle = title.trim()

        errorMessage = null
        isWorking = true

        coroutineScope.launch {
            // Si el formulario ya contiene la portada en la caché del servidor (file_resubmit),
            // no es necesario volver a subirla por la red.
            val hasCachedCover = form.fields.any { it.first.contains("cover-clearable_file_input_cache-key") }
            val (coverBytes, coverMimeType) = if (hasCachedCover) {
                null to null
            } else {
                coverUri?.let { uri -> compressCoverImage(context, uri) } ?: (null to null)
            }

            val result = BookWyrmScraper.confirmCreateBookAuthor(
                api = api,
                form = form,
                option = option,
                coverBytes = coverBytes,
                coverMimeType = coverMimeType
            )
            isWorking = false

            when (result) {
                is BookWyrmScraper.CreateBookResult.Created -> {
                    if (keepAddingAnother) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.create_book_created_toast, bookTitle),
                            Toast.LENGTH_SHORT
                        ).show()
                        resetForm()
                    } else {
                        onBookCreated(result.bookUrl)
                        onDismiss()
                    }
                }
                is BookWyrmScraper.CreateBookResult.NeedsAuthorConfirmation -> {
                    pendingConfirmationForm = result.form
                    authorCandidates = result.options
                    selectedAuthorOption = result.options.firstOrNull()
                }
                is BookWyrmScraper.CreateBookResult.Error -> {
                    errorMessage = result.message.ifBlank {
                        context.getString(R.string.create_book_error_generic)
                    }
                }
            }
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
                text = if (pendingConfirmationForm != null) {
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

                if (pendingConfirmationForm != null) {
                    // Pantalla 2: Selección y confirmación de autor
                    Text(
                        text = stringResource(R.string.create_book_author_choice_explanation, author.trim()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        authorCandidates.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(enabled = !isWorking) {
                                        selectedAuthorOption = option
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedAuthorOption == option,
                                    onClick = { selectedAuthorOption = option },
                                    enabled = !isWorking
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (option.kind) {
                                            BookWyrmScraper.AuthorOption.Kind.NEW ->
                                                stringResource(R.string.missing_authors_choice_new, author.trim())
                                            else -> option.label
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

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(R.string.create_book_field_author)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWorking
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = isbn,
                            onValueChange = { isbn = it },
                            label = { Text(stringResource(R.string.create_book_field_isbn)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
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

                    // Selector de portada
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (coverUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 76.dp)
                                    .clip(MaterialTheme.shapes.small)
                            ) {
                                AsyncImage(
                                    model = coverUri,
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
                                    onClick = { coverUriString = null },
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
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking
                            )

                            // Selector de idioma (desplegable)
                            ExposedDropdownMenuBox(
                                expanded = languageDropdownExpanded && !isWorking,
                                onExpandedChange = { if (!isWorking) languageDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val langLabel = languageOptions.firstOrNull { it.first == selectedLanguageCode }?.let {
                                    stringResource(it.second)
                                } ?: stringResource(R.string.create_book_lang_unspecified)

                                OutlinedTextField(
                                    value = langLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.create_book_field_languages)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                    enabled = !isWorking
                                )

                                ExposedDropdownMenu(
                                    expanded = languageDropdownExpanded && !isWorking,
                                    onDismissRequest = { languageDropdownExpanded = false }
                                ) {
                                    languageOptions.forEach { (code, stringRes) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(stringRes)) },
                                            onClick = {
                                                selectedLanguageCode = code
                                                languageDropdownExpanded = false
                                            }
                                        )
                                    }
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
                    if (pendingConfirmationForm != null) {
                        Button(
                            onClick = { submitAuthorConfirmation() },
                            enabled = !isWorking && selectedAuthorOption != null
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
                    if (pendingConfirmationForm != null) {
                        // Volver al formulario de campos si cancela la confirmación de autor
                        pendingConfirmationForm = null
                    } else {
                        onDismiss()
                    }
                },
                enabled = !isWorking
            ) {
                Text(
                    if (pendingConfirmationForm != null) {
                        stringResource(R.string.progress_btn_cancel)
                    } else {
                        stringResource(R.string.book_close)
                    }
                )
            }
        }
    )
}
