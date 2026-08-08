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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * @param currentShelf El estante (ej. 'to-read', 'reading') en el que se ubica el libro, cuando
 *   quien abre la ficha lo sabe. Sin él se recurre al que diga la página del libro.
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

/**
 * Bloque con título para la pestaña «Diverse»: agrupa datos afines en una tarjeta.
 * Si el contenido no emite nada (el libro no trae ninguno de esos campos), la tarjeta
 * se queda con el título solo, así que quien la usa comprueba antes que haya algo.
 */
@Composable
internal fun BookInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

/**
 * Una fila que lleva a algún sitio: su nombre, el sitio al que va y la flecha que lo dice.
 *
 * Se enseña el servidor y no la dirección entera —«piratforlaget.se» dice más que setenta
 * caracteres de URL—, igual que en las copias enlazadas del libro. Toca toda la fila, no solo
 * la flecha: el icono es el cartel, no el botón.
 */
@Composable
private fun BookLinkRow(label: String, url: String, onOpen: (String) -> Unit) {
    val site = remember(url) {
        runCatching { java.net.URL(url).host.removePrefix("www.") }.getOrDefault(url)
    }
    BookInfoRow(label, onClick = { onOpen(url) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = site,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Una copia enlazada del libro: adónde lleva, en qué formato y en qué condiciones.
 *
 * Se enseña el sitio y no la dirección entera —«standardebooks.org» dice más que setenta
 * caracteres de URL—, y al lado el formato y si es gratis. Quien la añadió no sale: importa
 * adónde lleva, no de quién es el mérito.
 */
@Composable
private fun BookFileLinkRow(
    link: com.ferlagod.rocinante.data.model.BookFileLink,
    onOpen: (String) -> Unit
) {
    val href = link.href ?: return
    val site = remember(href) {
        runCatching { java.net.URL(href).host.removePrefix("www.") }.getOrDefault(href)
    }
    val availability = when (link.availability?.lowercase()?.trim()) {
        "free" -> stringResource(R.string.book_link_free)
        "purchase" -> stringResource(R.string.book_link_purchase)
        "loan" -> stringResource(R.string.book_link_loan)
        // Una condición que no habíamos previsto se enseña tal cual: mejor una palabra en
        // inglés que dejar a alguien creer que algo es gratis.
        else -> link.availability?.trim()?.takeIf { it.isNotEmpty() }
    }
    val detail = listOfNotNull(
        link.mediaType?.trim()?.takeIf { it.isNotEmpty() },
        availability
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(href) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = site,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Copia un texto al portapapeles y lo dice con un aviso.
 *
 * Desde Android 13 el propio sistema enseña además su confirmación al copiar, así que ahí se
 * ve dos veces; se deja igualmente para que la respuesta sea la misma en todas las versiones,
 * que es lo que se pidió. Quitarlo en las nuevas sería una línea.
 */
@Composable
private fun rememberCopyToClipboard(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { text ->
            clipboard.setText(AnnotatedString(text))
            // Se enseña lo copiado, para no tener que ir a pegarlo para saber qué se llevó uno.
            // Recortado, porque hay valores largos —un título, una lista de editoriales— y un
            // aviso de cinco líneas tapa media pantalla.
            val shown = if (text.length > COPY_PREVIEW_MAX) {
                text.take(COPY_PREVIEW_MAX).trimEnd() + "…"
            } else {
                text
            }
            // Desde Android 13 el sistema enseña su propia confirmación al copiar y no se puede
            // quitar, así que aquí va el valor a secas: se lee «Dan Brown» y, justo detrás, el
            // «copiado» que pone el sistema. Antes de esa versión no hay tal confirmación, y el
            // valor solo sería un nombre saliendo de la nada, así que ahí se dice entero.
            val message = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                shown
            } else {
                context.getString(R.string.copied_to_clipboard, shown)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Cuánto de lo copiado cabe en el aviso antes de recortarlo. */
private const val COPY_PREVIEW_MAX = 60

/**
 * Fila «etiqueta → valor» de la pestaña «Diverse». La etiqueta ocupa un ancho fijo para
 * que los valores queden alineados entre sí aunque las etiquetas midan distinto.
 *
 * @param onClick Qué hacer al tocar la fila, o null si no hace nada. No cambia nada de lo que
 *   se ve: la fila mide y se coloca igual toque o no.
 */
@Composable
internal fun BookInfoRow(
    label: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        content()
    }
}

/**
 * Toda fila de dato suelto se copia al tocarla, y cada una copia lo suyo: con el ISBN, el OCLC
 * y el de Open Library uno debajo de otro, se lleva uno el que ha tocado y no los tres.
 */
@Composable
internal fun BookInfoRow(label: String, value: String) {
    val copy = rememberCopyToClipboard()
    BookInfoRow(label, onClick = { copy(value) }) {
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Formatea una fecha ISO (yyyy-MM-dd) al formato medio del idioma del dispositivo. */
internal fun formatDetailDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val parsed = parser.parse(iso) ?: return iso
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(parsed)
    } catch (e: Exception) {
        iso
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onReadingFinished: (() -> Unit)? = null,
    // Aviso de que este libro ya no está en ninguna estantería, con su id/URL. Quien abre la
    // ficha puede así quitarlo de la lista en pantalla al momento, sin esperar a un refresco.
    // Si no se pasa, se recurre a [onShelved].
    onRemovedFromShelf: ((String) -> Unit)? = null,
    // Datos enriquecidos ya conocidos por quien abre la ficha (p. ej. la caché de la
    // estantería), para mostrar las estrellas al instante mientras se refresca en segundo plano.
    initialEnrichment: com.ferlagod.rocinante.data.model.BookEnrichment? = null,
    // Aviso con los datos enriquecidos recién leídos de la web (al abrir la ficha o tras
    // cambiar las fechas de lectura). Quien abre la ficha puede así refrescar su caché, que
    // por sí sola no se vuelve a leer nunca una vez guardada.
    onEnrichmentUpdated: ((com.ferlagod.rocinante.data.model.BookEnrichment) -> Unit)? = null
) {
    val copyToClipboard = rememberCopyToClipboard()
    var showProgressDialog by remember { mutableStateOf(false) }
    // Progreso pendiente de publicar: mientras no sea null se muestra la hoja de publicación.
    var progressPost by remember { mutableStateOf<ProgressSubmission?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }
    // Preguntar por la reseña en vez de abrirla sin más: terminar un libro y valorarlo son
    // dos cosas distintas, y quien solo quería apuntarlo se encontraba con el formulario
    // encima sin haberlo pedido.
    var showReviewPrompt by remember { mutableStateOf(false) }
    var showQuotationDialog by remember { mutableStateOf(false) }
    var showMyActivityDialog by remember { mutableStateOf(false) }
    var selectedReviewForDetail by remember { mutableStateOf<ActivityPubActivity?>(null) }
    var isFinishing by remember { mutableStateOf(false) }

    // Progreso de lectura actual (solo relevante mientras el libro está en lectura).
    // Se recarga cada vez que cambia el libro o tras una actualización correcta.
    var readingProgress by remember { mutableStateOf<BookWyrmScraper.ReadingProgressInfo?>(null) }
    var isLoadingProgress by remember { mutableStateOf(false) }
    var progressRefreshKey by remember { mutableStateOf(0) }

    // Configuración de progreso de este libro, que se olvida al terminarlo.
    val setupStore = remember(context) { com.ferlagod.rocinante.data.local.ProgressSetupStore(context) }

    // Datos enriquecidos del libro (autor, valoración, fechas, idioma) leídos de su página
    // HTML — no vienen en el .json. Se cargan una vez al abrir la ficha.
    var enrichment by remember { mutableStateOf(initialEnrichment) }
    LaunchedEffect(activeBookKey) {
        val fresh = runCatching { BookWyrmScraper.scrapeBookEnrichment(api, activeBookKey) }.getOrNull()
        if (fresh != null) {
            enrichment = fresh
            // Se cachea desde aquí, y no solo avisando a quien abrió la ficha: por la búsqueda
            // y la actividad pasan los libros que aún no están en ninguna estantería, que son
            // justo los que se acaban de añadir. Guardado ya, «Mis libros» los enseña completos
            // sin volver a leer su página.
            runCatching {
                com.ferlagod.rocinante.data.local.TimelineCache(context).mergeEnrichment(fresh)
            }
            onEnrichmentUpdated?.invoke(fresh)
        }
    }

    // Fechas de lectura (empezado / terminado): se cambian en su propio diálogo.
    var showReadDatesDialog by remember { mutableStateOf(false) }

    // Lista de ediciones del mismo libro. Cuesta una petición aparte, así que se pide solo
    // cuando se abre: no es algo que se mire en cada libro.
    var showEditionPicker by remember { mutableStateOf(false) }

    // Identificadores para quitar el libro de su estantería: vienen con el enriquecimiento
    // (misma página HTML, sin descarga extra) y solo existen si el libro está en una, así que
    // sirven además para decidir si se ofrece la acción. Si la caché ya los traía, la opción
    // aparece al instante.
    val shelfBookId = enrichment?.shelfBookId
    val shelfId = enrichment?.shelfId

    // En qué estantería está el libro. Abierto desde una estantería lo dice ella; abierto desde
    // la búsqueda, la actividad o un perfil no lo sabe nadie, y lo dice su propia página. Sin
    // esto, un libro que se está leyendo abierto desde la búsqueda no ofrecía ni el progreso ni
    // «Empezar a leer», como si no estuviera en ninguna parte.
    var activeShelf by remember(currentShelf, enrichment?.shelfSlug) { mutableStateOf(currentShelf ?: enrichment?.shelfSlug) }

    // A un libro que no está en ninguna estantería se llega buscándolo, y lo primero que se
    // quiere hacer con él es ponerlo en una: se ofrece abajo del todo, sin tener que dar con
    // el menú de ⋮. Abierto desde una estantería no hace falta, porque ya está en ella.
    val canShelve = activeShelf == null && shelfBookId == null

    // Un libro que se dejó a medias solo tiene una cosa que ofrecer: retomarlo. Va en la barra
    // de abajo, en el mismo sitio que «Empezar a leer», porque es la misma decisión. Lo devuelve
    // a «Leyendo», que es lo que hace BookWyrm con el estado de lectura, así que además
    // desaparece de donde estaba.
    val resumeReading = activeShelf == "stopped-reading"

    // El progreso se pide en cuanto se sabe que el libro se está leyendo, que puede ser al
    // abrir la ficha (desde la estantería) o al llegar su página (desde cualquier otro sitio).
    LaunchedEffect(activeBookKey, activeShelf, progressRefreshKey) {
        if (activeShelf == "reading") {
            isLoadingProgress = true
            readingProgress = runCatching { BookWyrmScraper.getReadingProgress(api, activeBookKey) }.getOrNull()
            isLoadingProgress = false
        } else {
            readingProgress = null
        }
    }

    // Menú de tres puntos (⋮) de la barra + confirmación para cambiar de estante.
    var overflowExpanded by remember { mutableStateOf(false) }
    // Diálogo para elegir estantería. Al ser una elección deliberada en su propia lista,
    // no se pide además confirmación: se mueve el libro en cuanto se toca una.
    // Abrir un enlace sigue el ajuste que ya tiene la aplicación para los del libro, en vez
    // de inventar otro. No hay navegador propio: el WebView solo se usa para entrar y para
    // Anubis, y mantener uno más no se paga con lo que daría.
    val openLink: (String) -> Unit = { url ->
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        )
    }

    // ── Los autores del libro ──
    // El .json del libro solo trae sus direcciones; el nombre y lo demás están en la ficha de
    // cada uno. Se piden **después** de que la pantalla esté abierta y aparecen cuando llegan:
    // la ficha del libro no debe esperar por esto.
    val authorCache = remember(context) {
        com.ferlagod.rocinante.data.local.TimelineCache(context)
    }
    var authors by remember {
        mutableStateOf<List<com.ferlagod.rocinante.data.model.BookWyrmAuthor>>(emptyList())
    }
    LaunchedEffect(activeBookKey, bookDetails.authors) {
        val urls = bookDetails.authors.orEmpty().filter { it.isNotBlank() }
        if (urls.isEmpty()) {
            authors = emptyList()
            return@LaunchedEffect
        }
        val loaded = mutableListOf<com.ferlagod.rocinante.data.model.BookWyrmAuthor>()
        urls.forEach { url ->
            // Guardada la ficha, el segundo libro del mismo autor no cuesta nada.
            val cached = authorCache.loadAuthor(url)
            val author = cached ?: runCatching {
                api.getAuthor(BookWyrmUtils.ensureJsonUrl(url))
            }.getOrNull()?.also { authorCache.saveAuthor(url, it) }
            if (author != null) {
                loaded += author
                // Se van enseñando según llegan, en vez de esperar a tenerlos todos.
                authors = loaded.toList()
            }
        }
    }

    var showShelfPicker by remember { mutableStateOf(false) }
    // Confirmación antes de quitar el libro de su estantería.
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var isRemoving by remember { mutableStateOf(false) }

    // Ejecuta el cambio de estante (llamado tras confirmar en el diálogo).
    fun moveToShelf(slug: String, toastLabel: String) {
        coroutineScope.launch {
            try {
                val localUrl = BookWyrmScraper.resolveLocalBookUrl(api, activeBookKey) ?: activeBookKey
                val baseUrl = java.net.URL(localUrl).let { "${it.protocol}://${it.host}/" }
                val html = BookWyrmScraper.fetchBookPage(api, localUrl, baseUrl)
                val bookId = BookWyrmUtils.extractBookId(localUrl)
                val editionId = BookWyrmScraper.extractEditionId(html) ?: bookId
                
                if (editionId.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.error_book_not_identified), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val mappedStatus = mapOf(
                    "to-read" to "want",
                    "reading" to "start",
                    "read" to "finish",
                    // Dejar un libro a medias también es un estado de lectura de BookWyrm, no una
                    // estantería cualquiera: yendo por aquí lo saca él solo de «Leyendo».
                    "stopped-reading" to "stop"
                )[slug]
                // La fecha del día, la misma que el formulario de la web trae ya rellena. Sin
                // mandarla, BookWyrm da de alta la lectura **sin fechas**, y luego no hay de
                // dónde sacar ni cuándo se empezó ni cuándo se terminó: hay que escribirlas a
                // mano una por una.
                val today = java.time.LocalDate.now().toString()
                var response = if (mappedStatus != null) {
                    api.updateReadingStatus(
                        status = mappedStatus,
                        bookId = editionId,
                        startDate = today.takeIf { mappedStatus == "start" },
                        finishDate = today.takeIf { mappedStatus == "finish" },
                        stoppedDate = today.takeIf { mappedStatus == "stop" }
                    )
                } else {
                    api.shelveBook(editionId, slug)
                }

                // Fallback for BookWyrm >= 0.8: reading-status may return 404 for Work IDs, 
                // but shelveBook resolves them to Editions automatically.
                if (mappedStatus != null && !response.isSuccessful && response.code() != 302) {
                    val fallback = api.shelveBook(editionId, slug)
                    if (fallback.isSuccessful || fallback.code() == 302) {
                        response = fallback
                    }
                }

                if (response.isSuccessful || response.code() == 302) {
                    Toast.makeText(context, context.getString(R.string.error_shelve_added, toastLabel), Toast.LENGTH_SHORT).show()
                    // Ya leído: las páginas del ebook anotadas aquí dejan de tener sentido.
                    if (slug == "read") setupStore.clear(activeBookKey)
                    activeShelf = slug
                    onShelved?.invoke()
                    if (slug == "read") {
                        showReviewPrompt = true
                    } else {
                        onDismiss()
                    }
                } else {
                    Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Quita el libro de la estantería en la que está (llamado tras confirmar en el diálogo).
    fun removeFromShelf() {
        val bookId = shelfBookId ?: return
        val shelf = shelfId ?: return
        coroutineScope.launch {
            isRemoving = true
            try {
                // Token sin enmascarar de la cookie: coincide siempre con lo que espera Django.
                val csrfToken = com.ferlagod.rocinante.data.api.NetworkClient.currentCsrfToken() ?: ""
                val response = api.unshelveBook(bookId, shelf, csrfToken)
                if (response.isSuccessful || response.code() == 302) {
                    // La caché de estanterías alimenta la búsqueda local, así que se limpia
                    // aquí: pase por donde pase la ficha, el libro deja de aparecer al momento.
                    com.ferlagod.rocinante.data.local.TimelineCache(context)
                        .removeBookFromShelfCaches(activeBookKey)
                    Toast.makeText(context, context.getString(R.string.book_remove_toast), Toast.LENGTH_SHORT).show()
                    activeShelf = null
                    if (onRemovedFromShelf != null) onRemovedFromShelf(activeBookKey) else onShelved?.invoke()
                    onDismiss()
                } else {
                    Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, e), Toast.LENGTH_SHORT).show()
            } finally {
                isRemoving = false
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
                        // Ya leído: las páginas del ebook anotadas aquí dejan de tener sentido.
                        setupStore.clear(activeBookKey)
                        activeShelf = "read"
                        onReadingFinished?.invoke()
                        onShelved?.invoke()
                        showReviewPrompt = true
                    } else {
                        Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.error_book_not_identified), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, e), Toast.LENGTH_SHORT).show()
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
    // Las tres pestañas se pasan además arrastrando de lado, como la barra de abajo de la
    // aplicación: quien está leyendo una reseña espera poder seguir con el dedo.
    val pagerState = rememberPagerState(
        initialPage = if (currentShelf == "reading") 2 else 0,
        pageCount = { 3 }
    )
    val selectedTab = pagerState.currentPage
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
                                // Cambiar de estante: una sola entrada que abre el diálogo
                                // con las estanterías a las que se puede mover el libro.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_change_shelf_title)) },
                                    onClick = { overflowExpanded = false; showShelfPicker = true }
                                )
                                HorizontalDivider()
                                if (activeShelf == "reading") {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.book_update_progress)) },
                                        onClick = { overflowExpanded = false; showProgressDialog = true }
                                    )
                                    // Dejarlo a medias: solo se ofrece leyendo, que es lo único
                                    // que se puede dejar. Va aquí y no en la lista de estanterías
                                    // porque no es mover un libro de sitio, es rendirse con él.
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.book_stop_reading)) },
                                        onClick = {
                                            overflowExpanded = false
                                            moveToShelf(
                                                "stopped-reading",
                                                context.getString(R.string.shelf_stopped_title)
                                            )
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_edit_read_dates)) },
                                    onClick = { overflowExpanded = false; showReadDatesDialog = true }
                                )
                                // Elegir edición: la misma obra en otro idioma, en bolsillo o en
                                // audio. La lista se pide al abrirla.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_choose_edition)) },
                                    onClick = { overflowExpanded = false; showEditionPicker = true }
                                )
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
                                // Quitar de la estantería: solo si el libro está en una.
                                if (shelfBookId != null && shelfId != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        enabled = !isRemoving,
                                        text = {
                                            Text(
                                                text = stringResource(R.string.book_remove_from_shelf),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = { overflowExpanded = false; showRemoveConfirm = true }
                                    )
                                }
                            }
                        }
                    )
                },
                // Acción principal del libro según dónde esté: ponerlo en una estantería,
                // empezar a leerlo o anotar por dónde va. Va abajo del todo y fuera de las
                // pestañas, porque es lo que se quiere hacer con el libro se esté mirando la
                // sinopsis, las reseñas o los datos; antes solo salía al final de «Diverse»,
                // donde había que ir a buscarla desplazándose. Los tres casos se excluyen
                // entre sí: sin estantería no hay ni lectura ni progreso.
                bottomBar = {
                    val startReading = activeShelf == "to-read"
                    val isReading = activeShelf == "reading"
                    // Otra edición del mismo libro ya guardada: se avisa junto al botón, que es
                    // donde se decide, y no en una pestaña que a lo mejor no se abre. Las
                    // estanterías guardan ediciones concretas, así que sin el aviso se acaba con
                    // el mismo libro dos veces, cada una en un idioma.
                    // El nombre viene escrito en el idioma de la instancia («Read»); con el
                    // identificador se dice en el de la app, y solo si es una de las tres de
                    // siempre se cae al nombre tal cual para las estanterías propias.
                    val otherEditionShelf = when (enrichment?.otherEditionShelfSlug) {
                        "to-read" -> stringResource(R.string.shelf_chip_to_read)
                        "reading" -> stringResource(R.string.shelf_chip_reading)
                        "read" -> stringResource(R.string.shelf_chip_read)
                        "stopped-reading" -> stringResource(R.string.shelf_stopped_title)
                        else -> enrichment?.otherEditionShelfName
                    }
                    if (canShelve || startReading || resumeReading || isReading ||
                        otherEditionShelf != null
                    ) {
                        Surface(tonalElevation = 3.dp) {
                            Column {
                                otherEditionShelf?.let { shelfName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, end = 24.dp, top = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.book_other_edition_shelved,
                                                shelfName
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (canShelve || startReading || resumeReading) {
                                    Button(
                                        onClick = {
                                            if (canShelve) showShelfPicker = true
                                            else moveToShelf(
                                                "reading",
                                                context.getString(R.string.shelf_chip_reading)
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        if (canShelve) {
                                            Icon(
                                                imageVector = Icons.Filled.BookmarkBorder,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = stringResource(
                                                when {
                                                    canShelve -> R.string.book_add_to_shelf
                                                    startReading -> R.string.book_start_reading
                                                    else -> R.string.book_resume_reading
                                                }
                                            )
                                        )
                                    }
                                } else if (isReading) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Media pantalla por botón no da para «Actualizar
                                        // progreso» en dos líneas sin que el botón crezca al
                                        // doble de alto que su vecino. Una línea, con el hueco
                                        // de los lados recortado y la letra un punto más
                                        // pequeña, que es lo que cabe en danés y en castellano.
                                        OutlinedButton(
                                            onClick = { showProgressDialog = true },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.book_update_progress),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Button(
                                            onClick = { finishReading() },
                                            enabled = !isFinishing,
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.book_finish_reading),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                        }
                    }
                }
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    // ── Cabecera: portada con las estrellas debajo ──
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Subtítulo sobre la portada: la barra superior solo cabe una línea
                        // con el título, así que el complemento se muestra aquí. Si el libro
                        // no lo tiene, no se emite nada y no ocupa espacio.
                        bookDetails.subtitle?.trim()?.takeIf { it.isNotEmpty() }?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val detailCoverUrl = bookDetails.cover?.url ?: fallbackCoverUrl
                        if (detailCoverUrl.isNotBlank()) {
                            // La portada copia el título: es lo que representa, y el título de
                            // arriba no es una fila de datos que se pueda tocar.
                            val bookTitle = bookDetails.title?.trim()?.takeIf { it.isNotEmpty() }
                            AsyncImage(
                                model = detailCoverUrl,
                                contentDescription = stringResource(R.string.book_cover_detail_desc),
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(180.dp)
                                    .then(
                                        if (bookTitle != null) {
                                            Modifier.clickable { copyToClipboard(bookTitle) }
                                        } else Modifier
                                    ),
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
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text(stringResource(R.string.book_tab_synopsis)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text(stringResource(R.string.book_tab_reviews)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                            text = { Text(stringResource(R.string.book_tab_misc)) }
                        )
                    }

                    // El peso acota la altura del carrusel: sin él, la página querría ser
                    // infinita dentro de una columna que ya llena la pantalla.
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                    when (page) {
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
                            // ── Tu lectura: solo aparece si hay algo tuyo que contar ──
                            // Los días de lectura se cuentan sobre una lectura entera, no del
                            // primer inicio al último fin: con una relectura eso mediría también
                            // los años que el libro estuvo en la estantería sin abrirse.
                            val readings = enrichment?.readthroughs.orEmpty()
                            val lastComplete = readings.lastOrNull {
                                it.started != null && it.finished != null
                            }
                            val readingDays = com.ferlagod.rocinante.utils.ReadingStatsCalculator
                                .readingDays(
                                    lastComplete?.started ?: enrichment?.started,
                                    lastComplete?.finished ?: enrichment?.finished
                                )
                            val hasReadingData = enrichment?.rating != null ||
                                enrichment?.started != null || enrichment?.finished != null
                            if (hasReadingData) {
                                BookInfoSection(stringResource(R.string.book_section_reading)) {
                                    enrichment?.rating?.let { r ->
                                        BookInfoRow(stringResource(R.string.book_label_rating)) {
                                            BookRatingStars(r)
                                        }
                                    }
                                    enrichment?.started?.let { iso ->
                                        BookInfoRow(
                                            stringResource(R.string.book_label_started),
                                            formatDetailDate(iso) ?: iso
                                        )
                                    }
                                    enrichment?.finished?.let { iso ->
                                        val date = formatDetailDate(iso) ?: iso
                                        BookInfoRow(
                                            stringResource(R.string.book_label_finished),
                                            if (readingDays != null) {
                                                "$date (${pluralStringResource(R.plurals.reading_days, readingDays, readingDays)})"
                                            } else date
                                        )
                                    }
                                    // Releído: las fechas de arriba resumen todas las lecturas
                                    // (primer inicio, último fin), así que se enseña además cada
                                    // una por separado. Con una sola lectura no aportarían nada.
                                    if (readings.size > 1) {
                                        BookInfoRow(stringResource(R.string.book_label_readings)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                readings.forEach { reading ->
                                                    val range = listOfNotNull(
                                                        reading.started?.let { formatDetailDate(it) ?: it },
                                                        reading.finished?.let { formatDetailDate(it) ?: it }
                                                    ).joinToString(" – ")
                                                    if (range.isNotEmpty()) {
                                                        Text(
                                                            text = range,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // Las fechas se pueden corregir aquí mismo; en la web están
                                    // detrás del lápiz de «Read dates» de la página del libro.
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { showReadDatesDialog = true }) {
                                            Text(stringResource(R.string.book_edit_read_dates))
                                        }
                                    }
                                }
                            }

                            // ── Sobre el libro ──
                            val languageLabel = enrichment?.language?.takeIf { it.isNotBlank() }?.let { lang ->
                                val flag = com.ferlagod.rocinante.utils.LanguageFlags.flagFor(lang)
                                if (flag != null) "$flag  $lang" else lang
                            }
                            // La serie sale de la página HTML («Book 5 in ...»): en el .json del
                            // ejemplar los campos series/seriesNumber casi siempre vienen vacíos,
                            // aunque la instancia sí tenga el libro atado a una serie. Se usan
                            // igualmente de reserva por si algún ejemplar los trae rellenos.
                            // Nombre y número van juntos de la misma fuente: mezclarlos daría el
                            // número de una serie con el nombre de otra.
                            val series = enrichment?.seriesName?.trim()?.takeIf { it.isNotEmpty() }
                                ?.let { it to enrichment?.seriesPosition?.toString() }
                                ?: bookDetails.series?.trim()?.takeIf { it.isNotEmpty() }
                                    ?.let { it to bookDetails.seriesNumber?.trim()?.takeIf { n -> n.isNotEmpty() } }
                            val seriesLabel = series?.let { (name, number) ->
                                if (number != null) "$name  #$number" else name
                            }
                            val formatLabel = listOfNotNull(
                                bookDetails.physicalFormatDetail?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: bookDetails.physicalFormat?.trim()?.takeIf { it.isNotEmpty() }
                            ).firstOrNull()
                            val publisherLabel = bookDetails.publishers
                                ?.mapNotNull { it.trim().takeIf { p -> p.isNotEmpty() } }
                                ?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                            val subjects = bookDetails.subjects
                                ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
                                ?.distinct()
                                .orEmpty()

                            BookInfoSection(stringResource(R.string.book_section_about)) {
                                enrichment?.authorName?.takeIf { it.isNotBlank() }?.let {
                                    BookInfoRow(stringResource(R.string.book_label_author), it)
                                }
                                seriesLabel?.let {
                                    BookInfoRow(stringResource(R.string.book_label_series), it)
                                }
                                bookDetails.pages?.let {
                                    BookInfoRow(stringResource(R.string.book_label_pages), it.toString())
                                }
                                languageLabel?.let {
                                    BookInfoRow(stringResource(R.string.book_label_language), it)
                                }
                                formatLabel?.let {
                                    BookInfoRow(stringResource(R.string.book_label_format), it)
                                }
                                publisherLabel?.let {
                                    BookInfoRow(stringResource(R.string.book_label_publisher), it)
                                }
                                bookDetails.publishedDate?.takeIf { it.isNotBlank() }?.let { iso ->
                                    BookInfoRow(
                                        stringResource(R.string.book_label_published),
                                        formatDetailDate(iso) ?: iso
                                    )
                                }
                                // La primera edición solo aporta algo si no coincide con esta.
                                bookDetails.firstPublishedDate?.takeIf {
                                    it.isNotBlank() && it != bookDetails.publishedDate
                                }?.let { iso ->
                                    BookInfoRow(
                                        stringResource(R.string.book_label_first_published),
                                        formatDetailDate(iso) ?: iso
                                    )
                                }
                                if (subjects.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.book_label_subjects),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        subjects.forEach { subject ->
                                            SuggestionChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        text = subject,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Números: ISBN y demás identificadores del ejemplar ──
                            val isbn = bookDetails.isbn13?.trim()?.takeIf { it.isNotEmpty() }
                                ?: bookDetails.isbn10?.trim()?.takeIf { it.isNotEmpty() }
                            val oclc = bookDetails.oclcNumber?.trim()?.takeIf { it.isNotEmpty() }
                            val openLibrary = bookDetails.openlibraryKey?.trim()?.takeIf { it.isNotEmpty() }
                            val inventaire = bookDetails.inventaireId?.trim()?.takeIf { it.isNotEmpty() }
                            if (isbn != null || oclc != null || openLibrary != null || inventaire != null) {
                                BookInfoSection(stringResource(R.string.book_section_ids)) {
                                    isbn?.let {
                                        BookInfoRow(stringResource(R.string.book_label_isbn), it)
                                    }
                                    oclc?.let {
                                        BookInfoRow(stringResource(R.string.book_label_oclc), it)
                                    }
                                    openLibrary?.let {
                                        BookInfoRow(stringResource(R.string.book_label_openlibrary), it)
                                    }
                                    inventaire?.let {
                                        BookInfoRow(stringResource(R.string.book_label_inventaire), it)
                                    }
                                }
                            }

                            // ── Los autores, uno por bloque ──
                            // Un bloque por persona y no una lista: la biografía, el año y el
                            // sitio web son de alguien concreto, y con dos autores en el mismo
                            // bloque habría que adivinar de quién es cada cosa.
                            authors.forEach { author ->
                                AuthorInfoBlock(author, openLink)
                            }

                            // ── Copias enlazadas desde la instancia ──
                            // Pocos libros las tienen, pero cuando las tienen es lo que se
                            // quiere pulsar, así que van encima de los números y no debajo.
                            val links = bookDetails.fileLinks
                                ?.filter { !it.href.isNullOrBlank() }
                                // El mismo enlace aparece repetido cuando lo han añadido
                                // varias personas; para quien lee es un solo sitio.
                                ?.distinctBy { it.href }
                                .orEmpty()
                            if (links.isNotEmpty()) {
                                BookInfoSection(stringResource(R.string.book_section_downloads)) {
                                    links.forEach { link ->
                                        BookFileLinkRow(link) { url ->
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(url)
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // La acción contextual del libro (empezar a leer / anotar progreso)
                            // vive en la barra de abajo del diálogo, visible en las tres pestañas.

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
                                // Un progreso en porcentaje (así viaja el del ebook) no dice por
                                // dónde se va: se traduce a la página de la edición impresa, que
                                // es la que sirve para hablarlo con cualquiera. Anotado en
                                // páginas ya lo dice la línea de arriba.
                                val printedLabel = if (rp.mode == "PCT" && totalPages != null && totalPages > 0) {
                                    fraction?.let { f ->
                                        stringResource(
                                            R.string.progress_ebook_printed_estimate,
                                            (totalPages * f).toInt().coerceIn(0, totalPages),
                                            totalPages
                                        )
                                    }
                                } else null
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.book_current_progress),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = progressLabel, style = MaterialTheme.typography.bodyMedium)
                                        if (printedLabel != null) {
                                            Text(
                                                text = printedLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (fraction != null) {
                                            LinearProgressIndicator(
                                                progress = { fraction },
                                                modifier = Modifier.fillMaxWidth()
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
    }

    // ── Elegir estantería ──
    if (showShelfPicker) {
        // (slug, nombre visible, etiqueta del aviso al terminar). Se ofrecen todas menos
        // aquella en la que ya está el libro.
        // Cada estantería lleva el mismo icono con el que aparece en «Mis libros».
        data class ShelfTarget(
            val slug: String,
            val label: String,
            val toastLabel: String,
            val icon: androidx.compose.ui.graphics.vector.ImageVector
        )
        val targets = listOf(
            ShelfTarget(
                "to-read", stringResource(R.string.shelf_chip_to_read),
                stringResource(R.string.shelf_toast_pending), Icons.Filled.BookmarkBorder
            ),
            ShelfTarget(
                "reading", stringResource(R.string.shelf_chip_reading),
                stringResource(R.string.shelf_toast_reading), Icons.AutoMirrored.Filled.MenuBook
            ),
            ShelfTarget(
                "read", stringResource(R.string.shelf_chip_read),
                stringResource(R.string.shelf_toast_read), Icons.Filled.CheckCircle
            )
        ).filter { it.slug != activeShelf }
        AlertDialog(
            onDismissRequest = { showShelfPicker = false },
            // Poner en una estantería y cambiar de estantería son la misma lista, pero no la
            // misma frase: al libro que aún no está en ninguna se le añade.
            title = {
                Text(
                    stringResource(
                        if (canShelve) R.string.book_add_to_shelf else R.string.book_change_shelf_title
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { target ->
                        FilledTonalButton(
                            onClick = {
                                showShelfPicker = false
                                moveToShelf(target.slug, target.toastLabel)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = target.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = target.label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShelfPicker = false }) {
                    Text(stringResource(R.string.progress_btn_cancel))
                }
            }
        )
    }

    // ── Confirmación antes de quitar el libro de la estantería ──
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.book_remove_from_shelf)) },
            text = { Text(stringResource(R.string.book_remove_confirm)) },
            confirmButton = {
                TextButton(
                    enabled = !isRemoving,
                    onClick = {
                        showRemoveConfirm = false
                        removeFromShelf()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.book_remove_confirm_yes),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(R.string.progress_btn_cancel))
                }
            }
        )
    }

    // ── Fechas de lectura (empezado / terminado) ──
    if (showReadDatesDialog) {
        ReadDatesDialog(
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showReadDatesDialog = false },
            onSaved = {
                // Las fechas se leen de la página del libro, así que se vuelve a raspar para
                // enseñarlas ya cambiadas aquí y avisar a quien tenga la ficha abierta.
                coroutineScope.launch {
                    val fresh = runCatching {
                        BookWyrmScraper.scrapeBookEnrichment(api, activeBookKey)
                    }.getOrNull()
                    if (fresh != null) {
                        enrichment = fresh
                        onEnrichmentUpdated?.invoke(fresh)
                    }
                }
            }
        )
    }

    // ── Elegir edición ──
    if (showEditionPicker) {
        EditionPickerDialog(
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showEditionPicker = false },
            // Cambiada la edición, lo que se está viendo ya no es el libro que la instancia
            // tiene guardado: se cierra la ficha y se refresca lo de detrás, igual que al
            // cambiar de estantería.
            onSwitched = {
                showEditionPicker = false
                onShelved?.invoke()
                onDismiss()
            }
        )
    }

    // Refleja el progreso recién enviado y avisa a quien abrió la ficha.
    fun applyNewProgress(newProgress: BookWyrmScraper.ReadingProgressInfo?) {
        // Reflejar el nuevo progreso al instante con el valor enviado.
        // Si por algún motivo no llegó el valor, recargar desde la red como respaldo.
        if (newProgress != null) {
            readingProgress = newProgress
        } else {
            progressRefreshKey++
        }
        onShelved?.invoke()
    }

    // ── Progreso de lectura: solo el progreso ──
    if (showProgressDialog) {
        ReadingProgressDialog(
            bookDetails = bookDetails,
            activeBookKey = activeBookKey,
            currentProgress = readingProgress,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { showProgressDialog = false },
            onCompose = { submission ->
                // Pasar el testigo a la hoja de publicación: allí se envía todo junto.
                showProgressDialog = false
                progressPost = submission
            },
            onSuccess = { newProgress ->
                showProgressDialog = false
                applyNewProgress(newProgress)
            }
        )
    }

    // ── Publicación del progreso, en su propia hoja ──
    progressPost?.let { submission ->
        ProgressPostDialog(
            submission = submission,
            activeBookKey = activeBookKey,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = { progressPost = null },
            onSuccess = { newProgress ->
                progressPost = null
                applyNewProgress(newProgress)
            }
        )
    }

    // ── ¿Reseñar el libro recién terminado? ──
    if (showReviewPrompt) {
        AlertDialog(
            onDismissRequest = { showReviewPrompt = false; onDismiss() },
            title = { Text(stringResource(R.string.book_review_prompt_title)) },
            text = { Text(stringResource(R.string.book_review_prompt_text)) },
            confirmButton = {
                TextButton(onClick = { showReviewPrompt = false; showReviewDialog = true }) {
                    Text(stringResource(R.string.book_write_review))
                }
            },
            // Sin reseña, el libro ya está guardado: se cierra la ficha como con cualquier
            // otro cambio de estantería.
            dismissButton = {
                TextButton(onClick = { showReviewPrompt = false; onDismiss() }) {
                    Text(stringResource(R.string.book_review_prompt_skip))
                }
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

/** Unidad en la que el usuario indica su progreso. */
private enum class ProgressMode {
    /** Página de la edición impresa: BookWyrm lo entiende tal cual. */
    PAGES,
    /** Porcentaje leído. */
    PERCENT,
    /** Página del ebook: se convierte a porcentaje con el total guardado en el dispositivo. */
    EBOOK
}

/**
 * Progreso ya listo para enviar a BookWyrm.
 *
 * @property value Valor que espera el servidor (página o porcentaje).
 * @property mode Modo de BookWyrm: "PG" (páginas) o "PCT" (porcentaje).
 * @property summary Resumen legible del progreso, para recordarlo al redactar la publicación.
 */
private data class ProgressSubmission(
    val value: String,
    val mode: String,
    val summary: String
)

/**
 * Envía una actualización de progreso a BookWyrm, con o sin publicación en el feed.
 * Avisa por toast tanto del acierto como del fallo.
 *
 * @param api Cliente autenticado.
 * @param context Contexto para los textos y los toasts.
 * @param activeBookKey URL o id del libro cuyo progreso se actualiza.
 * @param submission Progreso a enviar.
 * @param postToFeed Si además se publica un estado en el feed.
 * @param privacy Visibilidad de la publicación ("public", "followers" o "direct").
 * @param content Texto del comentario (vacío si no se publica).
 * @param contentWarning Aviso de spoiler, o vacío.
 * @return true si el servidor aceptó la actualización.
 */
private suspend fun submitProgress(
    api: BookWyrmApi,
    context: Context,
    activeBookKey: String,
    submission: ProgressSubmission,
    postToFeed: Boolean,
    privacy: String,
    content: String,
    contentWarning: String
): Boolean {
    return try {
        // 1. Obtener el context (readthrough ID + user ID + localBookId)
        // getProgressContext automáticamente resuelve la URL local si es federada
        val progressContext = BookWyrmScraper.getProgressContext(api, activeBookKey)
        if (progressContext == null) {
            Toast.makeText(context, context.getString(R.string.progress_readthrough_not_found), Toast.LENGTH_SHORT).show()
            return false
        }

        val bookId = progressContext.localBookId
        if (bookId.isBlank()) {
            Toast.makeText(context, context.getString(R.string.error_book_not_identified), Toast.LENGTH_SHORT).show()
            return false
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
            progress = submission.value,
            progressMode = submission.mode,
            postStatus = if (postToFeed) "on" else "",
            privacy = privacy,
            content = content,
            contentWarning = contentWarning,
            csrfToken = csrfForForm
        )

        val isRedirectToLogin = response.code() in 300..399 && response.headers()["Location"]?.contains("login") == true
        when {
            isRedirectToLogin -> {
                Toast.makeText(context, context.getString(R.string.auth_login_required), Toast.LENGTH_SHORT).show()
                false
            }
            response.isSuccessful || response.code() == 302 -> {
                Toast.makeText(context, context.getString(R.string.progress_success), Toast.LENGTH_SHORT).show()
                true
            }
            else -> {
                Toast.makeText(context, context.getString(R.string.progress_error, response.code().toString()), Toast.LENGTH_SHORT).show()
                false
            }
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Toast.makeText(context, context.getString(R.string.progress_network_error, e.message), Toast.LENGTH_LONG).show()
        false
    }
}

/**
 * Progreso de lectura, y nada más: campo del valor, selector de unidad (páginas, porcentaje o
 * páginas del ebook) y el botón de guardar. La publicación en el feed vive en su propia hoja
 * ([ProgressPostDialog]), a la que se llega con el botón «Publicar».
 *
 * En modo ebook se pide además cuántas páginas tiene *tu* ebook (depende del tamaño de letra):
 * se guarda solo en este dispositivo y sirve para convertir la página en el porcentaje que
 * BookWyrm sí entiende, mostrando de paso a qué página de la edición impresa equivale.
 *
 * La unidad (y el total del ebook) se fijan una vez por libro: si ya están decididas, se muestran
 * bloqueadas y solo se cambian tocando «Editar», para que actualizar el progreso sea escribir un
 * número y poco más.
 *
 * @param bookDetails Datos del libro (de aquí sale el número de páginas impresas).
 * @param activeBookKey URL o id del libro.
 * @param currentProgress Progreso ya registrado en BookWyrm, si lo hay: su modo indica en qué
 *   unidad se viene anotando este libro cuando no consta nada en este dispositivo.
 * @param api Cliente autenticado.
 * @param context Contexto para los textos y los toasts.
 * @param coroutineScope Ámbito en el que se envía la actualización.
 * @param onDismiss Cierra la hoja.
 * @param onCompose Pasa el progreso a la hoja de publicación.
 * @param onSuccess Avisa del progreso ya guardado (null si no se pudo deducir el valor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingProgressDialog(
    bookDetails: BookWyrmBookDetails,
    activeBookKey: String,
    currentProgress: BookWyrmScraper.ReadingProgressInfo?,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onCompose: (ProgressSubmission) -> Unit,
    onSuccess: (BookWyrmScraper.ReadingProgressInfo?) -> Unit
) {
    // Estado del formulario
    var progressInput by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ProgressMode.PAGES) }
    var ebookTotalInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    // Con la unidad ya decidida para este libro, los campos de configuración se enseñan
    // bloqueados; «Editar» los desbloquea.
    var isEditingSetup by remember { mutableStateOf(false) }

    // Configuración guardada en este dispositivo (unidad y páginas del ebook). Si no consta nada
    // de este libro se recurre a la unidad del progreso ya registrado y, en último término, a la
    // última que se usó en cualquier libro: quien lee siempre en ebook no la elige cada vez.
    val setupStore = remember(context) { com.ferlagod.rocinante.data.local.ProgressSetupStore(context) }
    LaunchedEffect(activeBookKey) {
        val setup = setupStore.get(activeBookKey)
        setup.ebookPages?.let { ebookTotalInput = it.toString() }
        val savedMode = setup.mode?.let { runCatching { ProgressMode.valueOf(it) }.getOrNull() }
        // Un total de ebook anotado solo puede venir de haber leído este libro en ebook, así que
        // manda sobre lo que diga el servidor: allí un ebook viaja como porcentaje.
        val impliedByEbook = if (setup.ebookPages != null) ProgressMode.EBOOK else null
        val registeredMode = when (currentProgress?.mode) {
            "PG" -> ProgressMode.PAGES
            "PCT" -> ProgressMode.PERCENT
            else -> null
        }
        // Decidido para este libro: se bloquea. Si solo hay una costumbre general, se propone
        // pero se deja abierto, porque para este libro aún no se ha elegido nada.
        val decided = savedMode ?: impliedByEbook ?: registeredMode
        val proposed = decided
            ?: setupStore.getLastMode()?.let { runCatching { ProgressMode.valueOf(it) }.getOrNull() }
        if (proposed != null) mode = proposed
        // En ebook no basta con la unidad: sin el total no se puede calcular nada.
        isEditingSetup = decided == null ||
            (decided == ProgressMode.EBOOK && setup.ebookPages == null)
    }
    // Se anota el total en cuanto deja de escribirse, sin esperar a que se envíe el progreso.
    LaunchedEffect(ebookTotalInput) {
        val total = ebookTotalInput.toIntOrNull()
        if (total != null && total > 0 && mode == ProgressMode.EBOOK) {
            kotlinx.coroutines.delay(600)
            setupStore.setEbookPages(activeBookKey, total)
            setupStore.setMode(activeBookKey, ProgressMode.EBOOK.name)
        }
    }

    // Deja constancia de la unidad elegida al registrar un progreso: la próxima vez este libro
    // aparece ya configurado, y otro libro nuevo estrenará esta misma unidad.
    fun rememberMode() {
        coroutineScope.launch { setupStore.setMode(activeBookKey, mode.name) }
    }

    val pagesLabel = stringResource(R.string.progress_pages)
    val percentLabel = stringResource(R.string.progress_percent)
    val ebookLabel = stringResource(R.string.progress_pages_ebook)
    val modeLabel = when (mode) {
        ProgressMode.PAGES -> pagesLabel
        ProgressMode.PERCENT -> percentLabel
        ProgressMode.EBOOK -> ebookLabel
    }

    // ── Cuentas del modo ebook ──
    val ebookTotal = ebookTotalInput.toIntOrNull()?.takeIf { it > 0 }
    val enteredNumber = progressInput.toIntOrNull()?.takeIf { it >= 0 }
    val printedPages = bookDetails.pages?.takeIf { it > 0 }
    val ebookPercent = if (mode == ProgressMode.EBOOK && ebookTotal != null && enteredNumber != null) {
        (enteredNumber * 100 / ebookTotal).coerceIn(0, 100)
    } else null
    // Página aproximada de la edición impresa, para hacerse una idea al comentar con alguien
    // que lea el libro en papel.
    val printedEstimate = if (ebookPercent != null && printedPages != null) {
        (printedPages * ebookPercent / 100).coerceIn(0, printedPages)
    } else null

    // Resumen legible del progreso: se enseña en la hoja de publicación.
    val progressSummary = when {
        mode == ProgressMode.PAGES && enteredNumber != null && printedPages != null ->
            stringResource(R.string.book_progress_pages, enteredNumber, printedPages, enteredNumber * 100 / printedPages)
        mode == ProgressMode.PAGES && enteredNumber != null ->
            stringResource(R.string.book_progress_pages_no_total, enteredNumber)
        mode == ProgressMode.PERCENT && enteredNumber != null ->
            stringResource(R.string.book_progress_percent, enteredNumber.coerceIn(0, 100))
        ebookPercent != null && enteredNumber != null && ebookTotal != null ->
            stringResource(R.string.book_progress_ebook, ebookPercent, enteredNumber, ebookTotal)
        else -> ""
    }

    // Progreso listo para enviar, o null si aún falta algún dato.
    val submission = when (mode) {
        ProgressMode.PAGES -> enteredNumber?.let { ProgressSubmission(it.toString(), "PG", progressSummary) }
        ProgressMode.PERCENT -> enteredNumber?.let {
            ProgressSubmission(it.coerceIn(0, 100).toString(), "PCT", progressSummary)
        }
        ProgressMode.EBOOK -> ebookPercent?.let { ProgressSubmission(it.toString(), "PCT", progressSummary) }
    }
    // Mensaje de lo que falta cuando aún no se puede enviar.
    val missingDataMessage = if (mode == ProgressMode.EBOOK && ebookTotal == null) {
        stringResource(R.string.progress_ebook_total_missing)
    } else {
        stringResource(R.string.progress_dialog_hint)
    }

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

                // Selector de modo (páginas / porcentaje / páginas del ebook). Ya decidido para
                // este libro, se enseña como simple texto hasta que se toque «Editar».
                if (!isEditingSetup) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                } else ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = modeLabel,
                        onValueChange = {},
                        readOnly = true,
                        // Ancho suficiente para la etiqueta más larga («páginas (ebook)»).
                        modifier = Modifier
                            .width(185.dp)
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        textStyle = MaterialTheme.typography.bodyMedium,
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
                            onClick = { mode = ProgressMode.PAGES; modeExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(percentLabel) },
                            onClick = { mode = ProgressMode.PERCENT; modeExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(ebookLabel) },
                            onClick = { mode = ProgressMode.EBOOK; modeExpanded = false }
                        )
                    }
                }
            }

            // ── Páginas del ebook: se piden al configurar, y luego solo se recuerdan ──
            if (mode == ProgressMode.EBOOK && isEditingSetup) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.progress_ebook_total_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ebookTotalInput,
                    onValueChange = { nuevo -> ebookTotalInput = nuevo.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.progress_ebook_total_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.progress_ebook_total_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Configuración ya fijada: un recordatorio discreto y el enlace para cambiarla.
            if (!isEditingSetup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (mode == ProgressMode.EBOOK && ebookTotal != null) {
                            stringResource(R.string.progress_ebook_total_saved, ebookTotal)
                        } else {
                            stringResource(R.string.progress_mode_saved, modeLabel)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { isEditingSetup = true }) {
                        Text(
                            text = stringResource(R.string.progress_edit_setup),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Equivalencias del ebook: el porcentaje que se enviará y, si se sabe cuántas
            // páginas tiene la edición impresa, por qué página se iría en papel.
            if (ebookPercent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.progress_ebook_percent, ebookPercent),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (printedEstimate != null && printedPages != null) {
                    Text(
                        text = stringResource(
                            R.string.progress_ebook_printed_estimate,
                            printedEstimate,
                            printedPages
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Guardar el progreso, o pasar a redactar la publicación ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val listo = submission
                        if (listo == null) {
                            Toast.makeText(context, missingDataMessage, Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        rememberMode()
                        onCompose(listo)
                    },
                    enabled = !isSending,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.progress_btn_post))
                }

                Button(
                    onClick = {
                        val listo = submission
                        if (listo == null) {
                            Toast.makeText(context, missingDataMessage, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        rememberMode()
                        isSending = true
                        coroutineScope.launch {
                            try {
                                val ok = submitProgress(
                                    api = api,
                                    context = context,
                                    activeBookKey = activeBookKey,
                                    submission = listo,
                                    postToFeed = false,
                                    privacy = "public",
                                    content = "",
                                    contentWarning = ""
                                )
                                if (ok) {
                                    // Actualización optimista: informar al diálogo del nuevo
                                    // progreso con el valor recién enviado, sin volver a la red.
                                    onSuccess(
                                        listo.value.toIntOrNull()?.let {
                                            BookWyrmScraper.ReadingProgressInfo(it, listo.mode)
                                        }
                                    )
                                }
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    enabled = !isSending,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isSending) stringResource(R.string.post_btn_sending)
                               else stringResource(R.string.progress_btn_save),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Publicación sobre el progreso de lectura: comentario, aviso de spoiler y privacidad.
 * El progreso ya viene decidido de [ReadingProgressDialog] y solo se recuerda arriba; al
 * compartir se envía todo junto (progreso + estado) en la misma petición.
 *
 * @param submission Progreso que se enviará junto con la publicación.
 * @param activeBookKey URL o id del libro.
 * @param api Cliente autenticado.
 * @param context Contexto para los textos y los toasts.
 * @param coroutineScope Ámbito en el que se envía la publicación.
 * @param onDismiss Cierra la hoja.
 * @param onSuccess Avisa del progreso ya guardado (null si no se pudo deducir el valor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressPostDialog(
    submission: ProgressSubmission,
    activeBookKey: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onSuccess: (BookWyrmScraper.ReadingProgressInfo?) -> Unit
) {
    var commentText by remember { mutableStateOf("") }
    var includeSpoiler by remember { mutableStateOf(false) }
    var spoilerText by remember { mutableStateOf("") }
    var selectedPrivacy by remember { mutableStateOf("public") }
    var isSending by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }

    // Textos de privacidad
    val privacyOptions = listOf(
        "public" to stringResource(R.string.progress_privacy_public),
        "followers" to stringResource(R.string.progress_privacy_followers),
        "direct" to stringResource(R.string.progress_privacy_private)
    )
    val currentPrivacyLabel = privacyOptions.firstOrNull { it.first == selectedPrivacy }?.second
        ?: stringResource(R.string.progress_privacy_public)

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
                    text = stringResource(R.string.progress_post_title),
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

            // Recordatorio del progreso que acompaña a la publicación.
            if (submission.summary.isNotBlank()) {
                Text(
                    text = submission.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                        isSending = true
                        coroutineScope.launch {
                            try {
                                val ok = submitProgress(
                                    api = api,
                                    context = context,
                                    activeBookKey = activeBookKey,
                                    submission = submission,
                                    postToFeed = true,
                                    privacy = selectedPrivacy,
                                    content = commentText,
                                    contentWarning = if (includeSpoiler) spoilerText else ""
                                )
                                if (ok) {
                                    onSuccess(
                                        submission.value.toIntOrNull()?.let {
                                            BookWyrmScraper.ReadingProgressInfo(it, submission.mode)
                                        }
                                    )
                                }
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
}

@OptIn(ExperimentalMaterial3Api::class)
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
                                Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, e), Toast.LENGTH_LONG).show()
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
                                Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, e), Toast.LENGTH_LONG).show()
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
                                                    Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()), Toast.LENGTH_SHORT).show()
                                                }

                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, e), Toast.LENGTH_SHORT).show()
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
                                                    Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()), Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                Toast.makeText(context, com.ferlagod.rocinante.utils.NetworkErrors.message(context, e), Toast.LENGTH_SHORT).show()
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


/** Convierte una fecha ISO (yyyy-MM-dd) a los milisegundos UTC que usa el calendario. */
private fun isoToUtcMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        java.time.LocalDate.parse(iso).toEpochDay() * 86_400_000L
    }.getOrNull()
}

/** Convierte la fecha elegida en el calendario (milisegundos UTC) a ISO (yyyy-MM-dd). */
private fun utcMillisToIso(millis: Long): String =
    java.time.LocalDate.ofEpochDay(Math.floorDiv(millis, 86_400_000L)).toString()

/**
 * Calendario para elegir una de las dos fechas de lectura. Se abre ya puesto en la fecha
 * que hubiera, y no deja elegir el futuro: BookWyrm rechaza una lectura terminada mañana.
 *
 * @param initialIso Fecha de partida en ISO, o null para abrirlo en blanco.
 * @param onPick Devuelve la fecha elegida en ISO.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadDatePickerDialog(
    initialIso: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val todayMillis = remember { java.time.LocalDate.now().toEpochDay() * 86_400_000L }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = isoToUtcMillis(initialIso),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= java.time.LocalDate.now().year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(utcMillisToIso(it)) } }
            ) {
                Text(stringResource(R.string.progress_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.progress_btn_cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

/**
 * Diálogo para poner o cambiar las fechas de lectura de un libro (lo que en la web de
 * BookWyrm son las «read dates»). Trae las lecturas que ya tiene el libro; si hay más de
 * una se elige cuál se toca, y siempre se puede añadir otra distinta.
 *
 * Una fecha ya guardada se puede corregir, pero no dejar en blanco: BookWyrm ignora los
 * campos vacíos al editar. Para eso hay que borrar la lectura entera desde la web.
 *
 * @param activeBookKey URL o id del libro del que se editan las fechas.
 * @param onSaved Se llama tras guardar, para refrescar lo que se enseñe de ese libro.
 */
@Composable
private fun ReadDatesDialog(
    activeBookKey: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var readContext by remember { mutableStateOf<BookWyrmScraper.ReadDatesContext?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    // Lectura que se está editando; null significa «una lectura nueva».
    var selectedId by remember { mutableStateOf<String?>(null) }
    var startIso by remember { mutableStateOf<String?>(null) }
    var finishIso by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingFinish by remember { mutableStateOf(false) }

    LaunchedEffect(activeBookKey) {
        isLoading = true
        val loaded = runCatching { BookWyrmScraper.getReadDatesContext(api, activeBookKey) }.getOrNull()
        readContext = loaded
        // Se abre por la lectura más reciente, que es la que casi siempre se quiere corregir.
        val latest = loaded?.readthroughs?.maxByOrNull { it.finishDate ?: it.startDate ?: "" }
        selectedId = latest?.id
        startIso = latest?.startDate
        finishIso = latest?.finishDate
        isLoading = false
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun save() {
        val ctx = readContext ?: return
        val start = startIso
        val finish = finishIso
        if (start == null && finish == null) {
            toast(context.getString(R.string.book_read_dates_empty))
            return
        }
        if (start != null && finish != null && start > finish) {
            toast(context.getString(R.string.book_read_dates_order))
            return
        }
        coroutineScope.launch {
            isSaving = true
            try {
                val editingId = selectedId
                val response = if (editingId != null) {
                    api.editReadthrough(
                        readthroughId = editingId,
                        startDate = start ?: "",
                        finishDate = finish ?: "",
                        csrfToken = ctx.csrfToken
                    )
                } else {
                    api.createReadthrough(
                        book = ctx.bookId,
                        user = ctx.userId,
                        startDate = start ?: "",
                        finishDate = finish ?: "",
                        csrfToken = ctx.csrfToken
                    )
                }
                // Al editar, BookWyrm contesta 200 vacío (pedimos JSON) o redirige a la página
                // del libro. Al crear, en cambio, un 200 es el formulario devuelto con errores:
                // ahí solo vale la redirección.
                val saved = response.code() == 302 || (editingId != null && response.isSuccessful)
                if (saved) {
                    toast(context.getString(R.string.book_read_dates_saved))
                    onSaved()
                    onDismiss()
                } else {
                    toast(com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                toast(com.ferlagod.rocinante.utils.NetworkErrors.message(context, e))
            } finally {
                isSaving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.book_edit_read_dates)) },
        text = {
            val ctx = readContext
            when {
                isLoading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }

                ctx == null -> Text(stringResource(R.string.error_book_not_identified))

                // Un libro releído varias veces trae una fila por lectura, así que el
                // contenido se desplaza para que no se coma los botones del diálogo.
                else -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Con lecturas registradas se elige a cuál se le tocan las fechas; la
                    // última opción deja registrar otra lectura distinta del mismo libro.
                    if (ctx.readthroughs.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.book_read_dates_which),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val noDate = stringResource(R.string.book_read_dates_none)
                        ctx.readthroughs.forEach { readthrough ->
                            val label = listOf(readthrough.startDate, readthrough.finishDate)
                                .joinToString(" → ") { formatDetailDate(it) ?: noDate }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSaving) {
                                        selectedId = readthrough.id
                                        startIso = readthrough.startDate
                                        finishIso = readthrough.finishDate
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedId == readthrough.id,
                                    onClick = {
                                        selectedId = readthrough.id
                                        startIso = readthrough.startDate
                                        finishIso = readthrough.finishDate
                                    }
                                )
                                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSaving) {
                                    selectedId = null
                                    startIso = null
                                    finishIso = null
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedId == null,
                                onClick = {
                                    selectedId = null
                                    startIso = null
                                    finishIso = null
                                }
                            )
                            Text(
                                text = stringResource(R.string.book_read_dates_new),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider()
                    }

                    val pickLabel = stringResource(R.string.book_read_dates_pick)
                    BookInfoRow(stringResource(R.string.book_label_started)) {
                        OutlinedButton(
                            onClick = { pickingStart = true },
                            enabled = !isSaving
                        ) {
                            Text(formatDetailDate(startIso) ?: pickLabel)
                        }
                    }
                    BookInfoRow(stringResource(R.string.book_label_finished)) {
                        OutlinedButton(
                            onClick = { pickingFinish = true },
                            enabled = !isSaving
                        ) {
                            Text(formatDetailDate(finishIso) ?: pickLabel)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading && !isSaving && readContext != null,
                onClick = { save() }
            ) {
                Text(stringResource(R.string.progress_btn_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text(stringResource(R.string.progress_btn_cancel))
            }
        }
    )

    if (pickingStart) {
        ReadDatePickerDialog(
            initialIso = startIso,
            onDismiss = { pickingStart = false },
            onPick = { startIso = it; pickingStart = false }
        )
    }
    if (pickingFinish) {
        ReadDatePickerDialog(
            initialIso = finishIso,
            onDismiss = { pickingFinish = false },
            onPick = { finishIso = it; pickingFinish = false }
        )
    }
}

/**
 * Lista de ediciones del mismo libro (la danesa, la inglesa, el audiolibro...) para cambiarse
 * a otra. En BookWyrm la estantería, las fechas de lectura y la valoración cuelgan de una
 * edición concreta, así que cambiarla se las lleva consigo: por eso se puede buscar un libro
 * en inglés, que es como suele encontrarse, y quedarse con la edición que de verdad se lee.
 *
 * La lista cuesta su propia petición, así que se pide al abrir el diálogo y no antes.
 *
 * @param activeBookKey URL o id de la edición desde la que se abre la lista.
 * @param onSwitched Se llama tras cambiar de edición: lo que hay detrás ya está desfasado.
 */
@Composable
private fun EditionPickerDialog(
    activeBookKey: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onSwitched: () -> Unit
) {
    var editions by remember { mutableStateOf<List<BookWyrmScraper.EditionOption>>(emptyList()) }
    var languages by remember { mutableStateOf<List<String>>(emptyList()) }
    // Idioma por el que se filtra; null son todas. La instancia ordena las ediciones por un
    // ranking con muchos empates y las sirve por páginas, y al pasar de página se le escapa
    // alguna; pedirlas por idioma las deja en una sola página y ahí salen todas.
    var language by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSwitching by remember { mutableStateOf(false) }

    LaunchedEffect(activeBookKey, language) {
        isLoading = true
        val loaded = runCatching { BookWyrmScraper.getEditions(api, activeBookKey, language) }
            .getOrDefault(BookWyrmScraper.Editions())
        editions = loaded.editions
        // La lista de idiomas es la misma se filtre o no, así que la primera que llegue vale.
        if (loaded.languages.isNotEmpty()) languages = loaded.languages
        isLoading = false
    }

    fun switchTo(edition: BookWyrmScraper.EditionOption) {
        if (isSwitching) return
        coroutineScope.launch {
            isSwitching = true
            try {
                // Token sin enmascarar de la cookie, como el resto de envíos.
                val csrfToken = com.ferlagod.rocinante.data.api.NetworkClient.currentCsrfToken() ?: ""
                val response = api.switchEdition(edition.id, csrfToken)
                if (response.isSuccessful || response.code() == 302) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.book_edition_switched),
                        Toast.LENGTH_SHORT
                    ).show()
                    onSwitched()
                } else {
                    Toast.makeText(
                        context,
                        com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(
                    context,
                    com.ferlagod.rocinante.utils.NetworkErrors.message(context, e),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isSwitching = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSwitching) onDismiss() },
        // Con el número delante se ve de un vistazo si están todas: un libro muy publicado
        // tiene decenas de ediciones y la instancia las da de página en página.
        title = {
            Text(
                if (editions.size > 1) {
                    "${stringResource(R.string.book_choose_edition)} (${editions.size})"
                } else {
                    stringResource(R.string.book_choose_edition)
                }
            )
        },
        text = {
            Column {
                // Filtro por idioma: es lo que se busca casi siempre («la tengo en italiano,
                // pero la leí en danés»), y de paso evita que se pierda ninguna al paginar.
                if (languages.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        FilterChip(
                            selected = language == null,
                            onClick = { if (!isSwitching) language = null },
                            label = { Text(stringResource(R.string.book_editions_all_languages)) }
                        )
                        languages.forEach { lang ->
                            val flag = com.ferlagod.rocinante.utils.LanguageFlags.flagFor(lang)
                            FilterChip(
                                selected = language == lang,
                                onClick = { if (!isSwitching) language = lang },
                                label = { Text(if (flag != null) "$flag $lang" else lang) }
                            )
                        }
                    }
                }
            when {
                isLoading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                // Filtrando puede no quedar ninguna; sin filtrar, una sola es la que ya se
                // está viendo y no hay nada que elegir.
                editions.size <= 1 && language == null ->
                    Text(stringResource(R.string.book_editions_none))
                editions.isEmpty() -> Text(stringResource(R.string.book_editions_none_language))
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(editions, key = { it.id }) { edition ->
                        val details = listOfNotNull(
                            edition.language?.let { lang ->
                                val flag = com.ferlagod.rocinante.utils.LanguageFlags.flagFor(lang)
                                if (flag != null) "$flag $lang" else lang
                            },
                            edition.format,
                            edition.pages?.let { stringResource(R.string.book_pages, it.toString()) },
                            edition.published?.takeIf { it.isNotBlank() }?.take(4)
                        ).joinToString(" · ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSwitching && !edition.isCurrent) {
                                    switchTo(edition)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!edition.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = edition.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.width(40.dp).height(60.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = edition.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (details.isNotEmpty()) {
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // La edición desde la que se ha abierto la lista se marca en vez
                                // de ofrecerse: cambiarse a la que ya se tiene no hace nada.
                                if (edition.isCurrent) {
                                    Text(
                                        text = stringResource(R.string.book_edition_current),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSwitching) {
                Text(stringResource(R.string.progress_btn_cancel))
            }
        }
    )
}
