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
import android.content.SharedPreferences
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.api.OpenLibraryLookup
import com.ferlagod.rocinante.utils.CreateBookForm
import com.ferlagod.rocinante.utils.IsbnUtils
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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

private const val PREFS_NAME = "create_book_prefs"
private const val PREF_LANGUAGES = "languages"

/** Los idiomas de la última alta o, la primera vez, el del móvil si el formulario lo ofrece. */
private fun defaultLanguageCodes(prefs: SharedPreferences, context: Context): String =
    prefs.getString(PREF_LANGUAGES, null)
        ?: CreateBookForm.languageCodeFor(ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language).orEmpty()

/** Campo al que llevar al usuario cuando falta algo. */
private enum class FieldTarget { TITLE, AUTHOR, DATE }

/**
 * Pantalla completa para la creación manual de un libro en la instancia de BookWyrm.
 *
 * Soporta entrada de metadatos completos con desplegables controlados para formato e idioma,
 * varios autores (con sugerencias de los que ya están en la instancia), escanear el ISBN,
 * rellenar los datos por ISBN desde OpenLibrary (solo, si se abre con un ISBN), aviso en cuanto
 * el ISBN ya está en la instancia, selección opcional de portada desde el dispositivo, las
 * preguntas de confirmación de la instancia (autores, obra, serie), ponerlo en una estantería al
 * crearlo y opción de crear y seguir añadiendo más libros consecutivamente. Si hay algo escrito,
 * pregunta antes de cerrar.
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
    val prefs = remember(appContext) { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val viewModel: CreateBookViewModel = androidx.hilt.navigation.compose.hiltViewModel(key = "create_book_dialog")
    // Una clave por apertura del diálogo; al girar se conserva y el ViewModel sigue con lo suyo.
    val sessionKey = rememberSaveable { java.util.UUID.randomUUID().toString() }
    remember(sessionKey) { viewModel.start(sessionKey) }
    val vmState by viewModel.state.collectAsStateWithLifecycle()
    val step = vmState.step
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
    var selectedLanguageCodes by rememberSaveable { mutableStateOf(defaultLanguageCodes(prefs, context)) }
    var selectedFormatCode by rememberSaveable { mutableStateOf("") }
    var shelfSlug by rememberSaveable { mutableStateOf("") }

    // Estados de desplegables
    var formatDropdownExpanded by remember { mutableStateOf(false) }
    var shelfDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

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

    // Escanear el ISBN sin salir del alta (útil al dar de alta varios seguidos).
    val scanLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        if (IsbnUtils.isIsbn(contents)) {
            isbn = IsbnUtils.normalize(contents)
            viewModel.lookupIsbn(isbn)
        } else {
            Toast.makeText(context, context.getString(R.string.create_book_scan_not_isbn), Toast.LENGTH_SHORT).show()
        }
    }

    var showMoreDetails by rememberSaveable { mutableStateOf(false) }
    // Error de la instancia o de la red; los de validación van en su campo.
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var titleError by rememberSaveable { mutableStateOf(false) }
    var authorError by rememberSaveable { mutableStateOf(false) }
    var dateError by rememberSaveable { mutableStateOf(false) }
    var focusTarget by remember { mutableStateOf<FieldTarget?>(null) }
    var lastAndAddAnother by rememberSaveable { mutableStateOf(false) }
    var autoLookupDone by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val titleFocus = remember { FocusRequester() }
    val authorFocus = remember { FocusRequester() }
    val dateFocus = remember { FocusRequester() }

    val selectedLanguages = selectedLanguageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // Si hay algo que perder, se pregunta antes de cerrar. Idioma y estantería no cuentan:
    // vienen puestos por defecto.
    val isDirty = title.trim() != initialTitle.trim() ||
        authors.any { it.isNotBlank() } ||
        IsbnUtils.normalize(isbn) != IsbnUtils.normalize(initialIsbn) ||
        listOf(pages, subtitle, description, publishedDate, publishers, series, seriesNumber, selectedFormatCode)
            .any { it.isNotBlank() } ||
        coverUriString != null || remoteCoverUrl != null

    fun requestClose() {
        when {
            isWorking -> Unit
            confirmation != null -> viewModel.cancelConfirmation()
            isDirty -> showDiscardDialog = true
            else -> onDismiss()
        }
    }

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
        selectedFormatCode = ""
        coverUriString = null
        remoteCoverUrl = null
        errorMessage = null
        titleError = false
        authorError = false
        dateError = false
        viewModel.clearAuthorSuggestions()
        // La estantería y los idiomas se quedan: al dar de alta varios seguidos suelen repetirse.
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
        val normalizedDate = CreateBookForm.normalizePublishedDate(publishedDate)

        titleError = trimmedTitle.isEmpty()
        authorError = authorNames.isEmpty()
        dateError = normalizedDate.isNotEmpty() && BookWyrmScraper.splitPublishedDate(normalizedDate) == null
        if (dateError) showMoreDetails = true
        // Se lleva al usuario al primer campo que falla (aunque esté más abajo o plegado).
        focusTarget = when {
            titleError -> FieldTarget.TITLE
            authorError -> FieldTarget.AUTHOR
            dateError -> FieldTarget.DATE
            else -> null
        }
        if (focusTarget != null) return

        errorMessage = null
        lastAndAddAnother = andAddAnother
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

    LaunchedEffect(focusTarget) {
        val target = focusTarget ?: return@LaunchedEffect
        // Un fotograma para que «Más detalles» llegue a desplegarse antes de enfocar la fecha.
        withFrameNanos { }
        runCatching {
            when (target) {
                FieldTarget.TITLE -> titleFocus.requestFocus()
                FieldTarget.AUTHOR -> authorFocus.requestFocus()
                FieldTarget.DATE -> dateFocus.requestFocus()
            }
        }
        focusTarget = null
    }

    // El error de la instancia sale arriba: que se vea aunque se haya pulsado desde abajo.
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) scrollState.animateScrollTo(0)
    }
    LaunchedEffect(vmState.duplicate) {
        if (vmState.duplicate != null) scrollState.animateScrollTo(0)
    }

    // Abierto con un ISBN (escáner o búsqueda): se rellenan los datos sin tener que pedirlo.
    LaunchedEffect(Unit) {
        if (!autoLookupDone && IsbnUtils.isIsbn(initialIsbn)) {
            autoLookupDone = true
            viewModel.lookupIsbn(initialIsbn)
        }
    }

    // En cuanto el ISBN está completo se mira si la instancia ya lo tiene.
    LaunchedEffect(isbn) {
        viewModel.checkDuplicate(api, isbn)
    }

    // Lo que termina en el ViewModel se atiende aquí, una sola vez.
    LaunchedEffect(vmState.event) {
        when (val event = vmState.event) {
            is CreateBookViewModel.Event.Created -> {
                runCatching { prefs.edit().putString(PREF_LANGUAGES, selectedLanguageCodes).apply() }
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
                    scrollState.scrollTo(0)
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
                    if (title.isNotBlank()) titleError = false
                    if (authors.any { it.isNotBlank() }) authorError = false
                    Toast.makeText(context, context.getString(R.string.create_book_fill_done), Toast.LENGTH_SHORT).show()
                }
            }
            null -> Unit
        }
        if (vmState.event != null) viewModel.consumeEvent()
    }

    val textFieldsEnabled = !isWorking

    Dialog(
        // Atrás pasa por aquí: vuelve de la confirmación o pregunta antes de perder lo escrito.
        onDismissRequest = { requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (confirmation != null) {
                                stringResource(R.string.create_book_author_choice_title)
                            } else {
                                stringResource(R.string.create_book_title)
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { requestClose() }, enabled = !isWorking) {
                            if (confirmation != null) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.progress_btn_cancel)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.book_close)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Los botones suben con el teclado para poder guardar sin cerrarlo.
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        when {
                            step != null -> Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text(
                                    text = stringResource(
                                        when (step) {
                                            CreateBookViewModel.Step.CHECKING_DUPLICATES -> R.string.create_book_step_duplicates
                                            CreateBookViewModel.Step.PREPARING_COVER -> R.string.create_book_step_cover
                                            CreateBookViewModel.Step.SENDING -> R.string.create_book_saving
                                            CreateBookViewModel.Step.CONFIRMING -> R.string.create_book_step_confirming
                                            CreateBookViewModel.Step.SHELVING -> R.string.create_book_step_shelving
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            confirmation != null -> {
                                val allAnswered = confirmation.options.map { it.field }.distinct()
                                    .all { confirmation.selections.containsKey(it) }
                                Button(
                                    onClick = { viewModel.confirm(api, coverProvider) },
                                    enabled = allAnswered,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.create_book_confirm_author))
                                }
                            }
                            else -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { submitInitialForm(andAddAnother = true) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.create_book_save_and_add_another))
                                }
                                Button(
                                    onClick = { submitInitialForm(andAddAnother = false) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.create_book_save))
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                vmState.duplicate?.takeIf { confirmation == null }?.let { duplicate ->
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
                                Button(
                                    onClick = {
                                        viewModel.dismissDuplicate()
                                        onBookCreated(duplicate.url)
                                        onDismiss()
                                    },
                                    enabled = !isWorking
                                ) { Text(stringResource(R.string.create_book_duplicate_open)) }
                                TextButton(
                                    onClick = {
                                        viewModel.acceptDuplicate()
                                        // Si salió al guardar, se sigue con lo que se había pedido.
                                        if (duplicate.fromSubmit) {
                                            submitInitialForm(andAddAnother = lastAndAddAnother, checkDuplicates = false)
                                        }
                                    },
                                    enabled = !isWorking
                                ) { Text(stringResource(R.string.create_book_duplicate_create_anyway)) }
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
                        onValueChange = {
                            title = it
                            titleError = false
                        },
                        label = { Text(stringResource(R.string.create_book_field_title)) },
                        singleLine = true,
                        isError = titleError,
                        supportingText = if (titleError) {
                            { Text(stringResource(R.string.create_book_title_required)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                        enabled = textFieldsEnabled
                    )

                    // Un campo por autor: con comas no se distinguiría «Tolkien, J. R. R.» de dos.
                    authors.forEachIndexed { index, value ->
                        val showError = authorError && index == 0
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                authors[index] = it
                                authorError = false
                                viewModel.suggestAuthors(api, instanceUrl, index, it)
                            },
                            label = { Text(stringResource(R.string.create_book_field_author)) },
                            singleLine = true,
                            isError = showError,
                            supportingText = if (showError) {
                                { Text(stringResource(R.string.create_book_author_required)) }
                            } else null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(authorFocus) else Modifier),
                            enabled = textFieldsEnabled,
                            trailingIcon = if (authors.size > 1) {
                                {
                                    IconButton(
                                        onClick = {
                                            authors.removeAt(index)
                                            viewModel.clearAuthorSuggestions()
                                        },
                                        enabled = !isWorking
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.create_book_remove_author)
                                        )
                                    }
                                }
                            } else null
                        )
                        // Autores que ya están en la instancia: elegir uno evita erratas y duplicados.
                        vmState.authorSuggestions?.takeIf { it.index == index && !isWorking }?.let { suggestions ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.create_book_author_suggestions),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    suggestions.names.forEach { name ->
                                        SuggestionChip(
                                            onClick = {
                                                if (index < authors.size) authors[index] = name
                                                viewModel.clearAuthorSuggestions()
                                            },
                                            label = { Text(name) }
                                        )
                                    }
                                }
                            }
                        }
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
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            isError = isbnLooksWrong,
                            supportingText = if (isbnLooksWrong) {
                                { Text(stringResource(R.string.create_book_isbn_check_warning)) }
                            } else null,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        scanLauncher.launch(
                                            ScanOptions()
                                                .setPrompt(context.getString(R.string.search_barcode_prompt))
                                                .setBeepEnabled(false)
                                                .setOrientationLocked(false)
                                        )
                                    },
                                    enabled = !isWorking
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = stringResource(R.string.search_tools_scan)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.4f),
                            enabled = textFieldsEnabled
                        )
                        OutlinedTextField(
                            value = pages,
                            // Solo cifras: lo demás la instancia lo rechazaría al guardar.
                            onValueChange = { value -> pages = value.filter { it.isDigit() }.take(5) },
                            label = { Text(stringResource(R.string.create_book_field_pages)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            enabled = textFieldsEnabled
                        )
                    }

                    // Rellenar por ISBN a mano (al abrir con un ISBN o al escanear se hace solo).
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
                            enabled = textFieldsEnabled
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

                    // Idiomas: se pueden marcar varios (un libro bilingüe, por ejemplo). Están a la
                    // vista porque vienen marcados por defecto y conviene ver cuál se va a mandar.
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
                            enabled = textFieldsEnabled
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
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = textFieldsEnabled
                            )

                            // El formato admitido ya va en la etiqueta.
                            OutlinedTextField(
                                value = publishedDate,
                                onValueChange = {
                                    publishedDate = it
                                    dateError = false
                                },
                                label = { Text(stringResource(R.string.create_book_field_published_date)) },
                                singleLine = true,
                                isError = dateError,
                                supportingText = if (dateError) {
                                    { Text(stringResource(R.string.create_book_date_invalid)) }
                                } else null,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }, enabled = !isWorking) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = stringResource(R.string.create_book_pick_date)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().focusRequester(dateFocus),
                                enabled = textFieldsEnabled
                            )

                            OutlinedTextField(
                                value = publishers,
                                onValueChange = { publishers = it },
                                label = { Text(stringResource(R.string.create_book_field_publishers)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = textFieldsEnabled
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
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.weight(2f),
                                    enabled = textFieldsEnabled
                                )
                                OutlinedTextField(
                                    value = seriesNumber,
                                    onValueChange = { seriesNumber = it },
                                    label = { Text(stringResource(R.string.create_book_field_series_number)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    modifier = Modifier.weight(1f),
                                    enabled = textFieldsEnabled && series.isNotBlank()
                                )
                            }

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text(stringResource(R.string.create_book_field_description)) },
                                minLines = 3,
                                maxLines = 6,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = textFieldsEnabled
                            )
                        }
                    }
                }
            }
        }
    }

    // Después de la pantalla completa para que, al recrearse tras girar, queden por encima.
    if (showDatePicker) {
        // Se abre en la fecha que ya esté escrita, no en hoy.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = CreateBookForm.publishedDateToUtcMillis(publishedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        publishedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                        dateError = false
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.create_book_discard_title)) },
            text = { Text(stringResource(R.string.create_book_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDismiss()
                }) { Text(stringResource(R.string.create_book_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.create_book_discard_keep))
                }
            }
        )
    }
}
