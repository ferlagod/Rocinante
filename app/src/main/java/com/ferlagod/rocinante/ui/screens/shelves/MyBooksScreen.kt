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
package com.ferlagod.rocinante.ui.screens.shelves


import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.ui.components.RatingStars
import com.ferlagod.rocinante.ui.components.ShelfLayoutDialog
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.local.SettingsData
import com.ferlagod.rocinante.data.local.SettingsPreferences
import com.ferlagod.rocinante.data.model.ShelfBookItem
import com.ferlagod.rocinante.utils.BookWyrmUtils
import com.ferlagod.rocinante.utils.ShelfAlignment
import com.ferlagod.rocinante.utils.ShelfLayout
import com.ferlagod.rocinante.utils.ShelfSection
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Modelo de datos optimizado para la vista de estanterías (Shelves).
 * Contiene identificadores e información de visualización básica.
 *
 * @property id Identificador único de la estantería.
 * @property title Nombre visible de la estantería.
 * @property bookCount Número de libros en la estantería.
 * @property bookCovers Lista de URLs de portadas para mostrar una vista previa.
 * @property fullUrl URL de la colección original en la API.
 */
data class ShelfUiItem(
    val slug: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * Una serie de libros con los ejemplares de ella que hay en la estantería.
 *
 * @property url URL de la serie en la instancia. Es la clave con la que se agrupan los libros:
 *   dos series pueden llamarse igual, y el nombre cambia si alguien lo corrige.
 * @property name Nombre visible de la serie.
 * @property books Los libros leídos de esa serie, ya ordenados por su número.
 */
private data class SeriesGroup(
    val url: String,
    val name: String,
    val books: List<ShelfBookItem>
)

/**
 * Las portadas de un grupo en abanico: la primera entera y las siguientes asomando por detrás,
 * para que se vea de un vistazo cuántos libros hay sin gastar una fila por cada uno.
 *
 * Caben las que quepan: se mide el ancho de verdad de la tarjeta en vez de dar por hecho un
 * número, que dejaba media tarjeta vacía en un móvil normal y se saldría por el borde en uno
 * estrecho. Se pintan de la última a la primera para que ese sea el orden de apilado.
 */
@Composable
private fun CoverFan(books: List<ShelfBookItem>) {
    val coverWidth = 70.dp
    val step = 34.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(105.dp)) {
        val fit = if (maxWidth <= coverWidth) 1 else ((maxWidth - coverWidth) / step).toInt() + 1
        val shown = books.take(fit.coerceAtLeast(1))
        for (i in shown.indices.reversed()) {
            val coverUrl = shown[i].cover?.url
            if (!coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = step * i)
                        .width(coverWidth)
                        .height(105.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/**
 * Un autor con los libros suyos que hay en la estantería.
 *
 * @property key Nombre normalizado, que es con lo que se agrupa. La URL del autor sería más
 *   estable sobre el papel, pero la instancia tiene la misma persona dada de alta varias veces
 *   —Lucinda Riley es a la vez el autor 29068 y el 58144— y agrupando por ella salía repetida
 *   en la lista, con sus libros repartidos.
 * @property name Nombre visible, tal y como lo escribe la instancia.
 * @property books Sus libros, sin ordenar todavía: el orden lo pone la pantalla.
 */
private data class AuthorGroup(
    val key: String,
    val name: String,
    val books: List<ShelfBookItem>
)

/**
 * Modos de ordenación disponibles para el listado de una estantería.
 * DEFAULT conserva el orden que devuelve el servidor. Los modos FINISHED_* y RATING_*
 * dependen de los datos enriquecidos (fecha de fin y valoración) obtenidos por libro.
 */
/** Hvor gammel hyldelisten må blive, før den hentes igen. */
private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

private enum class ShelfSortMode {
    DEFAULT, TITLE_ASC, TITLE_DESC, FINISHED_DESC, FINISHED_ASC, RATING_DESC, RATING_ASC
}

/**
 * Comparador que mantiene los valores nulos SIEMPRE al final (en ambas direcciones),
 * para que los libros sin dato enriquecido queden abajo en vez de mezclarse.
 */
private fun <T : Comparable<T>> nullsLastComparator(
    descending: Boolean,
    selector: (ShelfBookItem) -> T?
): Comparator<ShelfBookItem> = Comparator { a, b ->
    val x = selector(a)
    val y = selector(b)
    when {
        x == null && y == null -> 0
        x == null -> 1
        y == null -> -1
        else -> if (descending) y.compareTo(x) else x.compareTo(y)
    }
}

/**
 * Formatea una fecha ISO (yyyy-MM-dd) al formato medio del idioma del dispositivo
 * (p. ej. "1. jan. 2025" en danés). Null si la entrada es nula/vacía; si no se puede
 * parsear, devuelve la cadena original.
 */
/**
 * La nota, con el separador decimal del idioma del teléfono y sin decimal cuando es entera:
 * «4» y «4,5» en danés, «4.5» en inglés. Escribirla a pelo daría «4.0» en todas partes.
 */
private fun formatStars(stars: Double): String {
    val format = java.text.NumberFormat.getInstance()
    format.maximumFractionDigits = 1
    return format.format(stars)
}

private fun formatDisplayDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val parsed = parser.parse(iso) ?: return iso
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(parsed)
    } catch (e: Exception) {
        iso
    }
}

/**
 * Nº de días de lectura (inclusivo: mismo día = 1). El cálculo vive en [ReadingStatsCalculator] para
 * que la ficha del libro cuente exactamente igual que la tarjeta de la estantería.
 */
private fun readingDays(startIso: String?, finishIso: String?): Int? =
    com.ferlagod.rocinante.utils.ReadingStatsCalculator.readingDays(startIso, finishIso)

/**
 * Pantalla que muestra y permite interactuar con los estantes personales del usuario 
 * (por leer, leyendo, leídos). Actúa como punto de entrada antes de ver el detalle de cada estante.
 *
 * @param instanceUrl URL base de la instancia de BookWyrm.
 * @param username Nombre del usuario autenticado.
 * @param cookie Token de sesión para autenticar llamadas a la API.
 * @param api Instancia opcional de [BookWyrmApi]. Si se provee, se reutiliza para eficiencia.
 * @param onNavigateToSettings Acción a ejecutar cuando se solicita navegar a los ajustes desde la pantalla de estantes.
 * @param targetShelfSlug Estantería que hay que abrir automáticamente (viene de la búsqueda), o null.
 * @param targetBookId Libro al que desplazarse y que se resalta dentro de esa estantería, o null.
 * @param targetAuthorName Autor cuyos libros hay que enseñar al abrir esa estantería (viene de
 *   «autores más leídos» del perfil), o null.
 * @param targetFilter Recorte de la estantería que hay que enseñar al abrirla —un año, una nota,
 *   un idioma, un formato—, o null. Viene de tocar una gráfica del perfil.
 * @param onTargetConsumed Se invoca cuando ya se ha saltado al libro, para no repetir el salto.
 * @param isActive Si esta es la pestaña que se está viendo. El carrusel mantiene compuestas
 *   también las de al lado, y solo la visible debe quedarse con el botón de atrás del móvil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBooksScreen(
    instanceUrl: String,
    username: String,
    cookie: String,
    api: BookWyrmApi? = null,
    onNavigateToSettings: () -> Unit,
    targetShelfSlug: String? = null,
    targetBookId: String? = null,
    targetAuthorName: String? = null,
    targetFilter: com.ferlagod.rocinante.utils.ShelfFilter? = null,
    onTargetConsumed: () -> Unit = {},
    backToShelvesKey: Int = 0,
    isActive: Boolean = true
) {
    // Cada estantería con lo suyo. La cuarta que trae BookWyrm de serie —los libros que se
    // dejaron a medias— estaba sin enseñar, así que para verlos había que ir a la web; los
    // nombres llevaban traducidos desde siempre, sin nada que los usara.
    val shelvesBySection = ShelfSection.entries.associateWith { section ->
        when (section) {
            ShelfSection.STOPPED_READING -> ShelfUiItem(section.slug, stringResource(R.string.shelf_stopped_title), stringResource(R.string.shelf_stopped_desc), Icons.Default.PauseCircleOutline)
            ShelfSection.TO_READ -> ShelfUiItem(section.slug, stringResource(R.string.shelf_to_read_title), stringResource(R.string.shelf_to_read_desc), Icons.Default.BookmarkBorder)
            ShelfSection.READING -> ShelfUiItem(section.slug, stringResource(R.string.shelf_reading_title), stringResource(R.string.shelf_reading_desc), Icons.AutoMirrored.Filled.MenuBook)
            ShelfSection.READ -> ShelfUiItem(section.slug, stringResource(R.string.shelf_read_title), stringResource(R.string.shelf_read_desc), Icons.Default.CheckCircle)
        }
    }

    // Disposición elegida por el usuario, guardada en ajustes como la del perfil.
    val layoutContext = LocalContext.current
    val layoutScope = rememberCoroutineScope()
    val settingsPreferences = remember(layoutContext) { SettingsPreferences(layoutContext) }
    val settingsState by settingsPreferences.settingsFlow.collectAsStateWithLifecycle(
        initialValue = SettingsData()
    )
    val shelfLayout = remember(settingsState.shelfLayout, settingsState.shelfAlignment) {
        ShelfLayout.decode(settingsState.shelfLayout, settingsState.shelfAlignment)
    }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showCreateShelf by remember { mutableStateOf(false) }

    // Las estanterías que el usuario se ha hecho: las suyas, no las cuatro de BookWyrm. Se
    // preguntan a la instancia porque solo ella sabe cuáles hay; mientras no llegue la
    // respuesta se ve la pantalla de siempre, sin hueco ni espera.
    val ownApi = remember(instanceUrl, cookie) {
        api ?: NetworkClient.createAuthenticatedApi(instanceUrl, cookie)
    }
    var ownShelves by remember {
        mutableStateOf<List<com.ferlagod.rocinante.data.api.BookWyrmScraper.UserShelf>>(emptyList())
    }
    // Sube al crear una estantería, para volver a preguntar sin recargar la pantalla entera.
    var ownShelvesKey by remember { mutableStateOf(0) }
    val ownShelvesCache = remember(layoutContext) {
        com.ferlagod.rocinante.data.local.TimelineCache(layoutContext)
    }
    LaunchedEffect(instanceUrl, username, ownShelvesKey) {
        val cached = ownShelvesCache.loadUserShelves()
        cached?.let { ownShelves = it }
        // Una lista de estanterías cambia dos veces al año, así que no se pide al entrar: solo
        // si no hay nada guardado, si lo guardado ya tiene un día, o si se ha pedido a mano con
        // «actualizar datos». Antes era una página de 40 kB cada vez que se pasaba por aquí.
        val stale = cached == null || ownShelvesCache.userShelvesAge() > ONE_DAY_MS
        if (stale || ownShelvesKey > 0) {
            val fresh = runCatching {
                com.ferlagod.rocinante.data.api.BookWyrmScraper
                    .getUserShelves(ownApi, instanceUrl, username)
                    .filter { !it.isSystem }
            }.getOrNull()
            // Una respuesta que no llega deja la lista como estaba, sin vaciarla.
            if (fresh != null && fresh != ownShelves) {
                ownShelves = fresh
                ownShelvesCache.saveUserShelves(fresh)
            } else if (fresh != null) {
                // Igual que estaba, pero recién comprobada: cuenta como fresca.
                ownShelvesCache.saveUserShelves(fresh)
            }
        }
    }

    // Las apagadas siguen existiendo: la búsqueda puede mandar aquí un libro de una de ellas, y
    // esconder la tarjeta no debe cerrar el camino a la estantería.
    val shelves = shelfLayout.visibleSections.map { shelvesBySection.getValue(it) } +
        // Las propias van detrás de las de serie y con otro icono, para que se vea de un
        // vistazo cuáles son estados de lectura y cuáles sitios donde uno guarda.
        ownShelves.map {
            ShelfUiItem(
                slug = it.identifier,
                title = it.name,
                description = "",
                icon = Icons.AutoMirrored.Filled.LibraryBooks
            )
        }
    val allShelves = ShelfSection.entries.map { shelvesBySection.getValue(it) } + shelves

    var selectedShelf by remember { mutableStateOf<ShelfUiItem?>(null) }

    // Al pulsar un libro de las estanterías en la búsqueda se abre aquí su estantería.
    LaunchedEffect(targetShelfSlug) {
        val slug = targetShelfSlug ?: return@LaunchedEffect
        allShelves.firstOrNull { it.slug == slug }?.let { selectedShelf = it }
    }

    // Volver a tocar «Mis libros» estando ya aquí devuelve a la lista de estanterías, como
    // hace cualquier aplicación con su barra de abajo. Llega como un número que sube en cada
    // toque, y solo cuenta que suba: al componer la pantalla ya viene con el valor que lleve,
    // y cerrar entonces la estantería se llevaría por delante la que abre la búsqueda.
    var lastBackToShelvesKey by remember { mutableStateOf(backToShelvesKey) }
    LaunchedEffect(backToShelvesKey) {
        if (backToShelvesKey != lastBackToShelvesKey) {
            lastBackToShelvesKey = backToShelvesKey
            selectedShelf = null
        }
    }

    if (selectedShelf == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // El título con el más al lado: crear una estantería es cosa de esta pantalla, no
            // de la lista, así que va arriba y se queda quieto aunque las tarjetas se muevan.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.shelf_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showCreateShelf = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.shelf_create_action)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.shelf_screen_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Cuatro tarjetas como mucho en una pantalla alta: arriba dejan medio móvil en
            // blanco y hay que estirar el pulgar hasta ellas. El hueco sobrante lo ocupa esta
            // caja, y la elección del usuario decide contra qué borde se apoyan las tarjetas.
            // El título no se mueve: es de la pantalla, no de la lista.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = when (shelfLayout.alignment) {
                    ShelfAlignment.TOP -> Alignment.TopCenter
                    ShelfAlignment.BOTTOM -> Alignment.BottomCenter
                }
            ) {
                // Con letra muy grande las cuatro tarjetas pueden no caber; entonces la
                // columna llena la caja y se desplaza, y la alineación deja de notarse.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    shelves.forEach { shelf ->
                        OutlinedCard(
                            onClick = { selectedShelf = shelf },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = shelf.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = shelf.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = shelf.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Ajustes de la propia página, detrás de las estanterías: se usa una vez y
                    // no debe competir con ellas. Igual que en el perfil.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { showLayoutDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.shelf_layout_edit))
                        }
                    }
                }
            }
        }

        if (showCreateShelf) {
            com.ferlagod.rocinante.ui.components.CreateShelfDialog(
                api = ownApi,
                instanceUrl = instanceUrl,
                username = username,
                context = layoutContext,
                coroutineScope = layoutScope,
                // Recién creada, se vuelve a preguntar por la lista, para que salga sin tener
                // que salir de la pantalla y volver.
                onCreated = { ownShelvesKey++ },
                onDismiss = { showCreateShelf = false }
            )
        }

        if (showLayoutDialog) {
            ShelfLayoutDialog(
                initialLayout = shelfLayout,
                onDismiss = { showLayoutDialog = false },
                onSave = { updated ->
                    showLayoutDialog = false
                    layoutScope.launch {
                        settingsPreferences.setShelfLayout(updated.encode(), updated.alignment.id)
                    }
                }
            )
        }
    } else {
        selectedShelf?.let { shelf ->
            ShelfNativeDetailScreen(
                instanceUrl = instanceUrl,
                username = username,
                cookie = cookie,
                sharedApi = api,
                shelf = shelf,
                onBack = { selectedShelf = null },
                onNavigateToSettings = onNavigateToSettings,
                highlightBookId = targetBookId.takeIf { targetShelfSlug == shelf.slug },
                onHighlightConsumed = onTargetConsumed,
                onResync = { ownShelvesKey++ },
                openAuthorName = targetAuthorName.takeIf { targetShelfSlug == shelf.slug },
                openFilter = targetFilter.takeIf { targetShelfSlug == shelf.slug },
                onTargetTaken = onTargetConsumed,
                backEnabled = isActive
            )
        }
    }
}

/**
 * Pantalla de detalle de una estantería nativa. Muestra una cuadrícula de libros
 * utilizando el cliente de red nativo (NetworkClient) y permite visualizar detalles de un libro.
 *
 * @param instanceUrl URL de la instancia actual.
 * @param username Nombre del usuario activo.
 * @param cookie Cookie de sesión autenticada.
 * @param sharedApi Cliente API de BookWyrm (opcional).
 * @param shelf Estantería a visualizar.
 * @param onBack Callback para volver atrás.
 * @param onNavigateToSettings Callback para navegar a la configuración.
 * @param highlightBookId Libro al que desplazarse y resaltar al abrir la pantalla, o null.
 * @param onHighlightConsumed Se invoca cuando ya se ha localizado el libro (o se sabe que no está).
 * @param openAuthorName Autor cuyos libros hay que enseñar nada más abrir, o null. Llega el
 *   nombre visible y se busca por él entre los grupos, con la misma normalización que usan.
 * @param openFilter Recorte que hay que enseñar nada más abrir —un año, una nota, un idioma, un
 *   formato—, o null.
 * @param backEnabled Si el botón de atrás del móvil lo atiende esta pantalla. Falso mientras la
 *   pestaña no sea la que se ve, para no quitárselo a la que sí lo está.
 */
@Composable
fun ShelfNativeDetailScreen(
    instanceUrl: String,
    username: String,
    cookie: String,
    sharedApi: BookWyrmApi? = null,
    shelf: ShelfUiItem,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    highlightBookId: String? = null,
    onHighlightConsumed: () -> Unit = {},
    openAuthorName: String? = null,
    openFilter: com.ferlagod.rocinante.utils.ShelfFilter? = null,
    // Se invoca en cuanto esta pantalla se ha quedado con el autor o el recorte que le
    // mandaban, para que quien lo mandó lo olvide.
    onTargetTaken: () -> Unit = {},
    // Se invoca al pedir «actualizar datos», para que también se vuelva a mirar qué
    // estanterías tiene el usuario: es el momento en que uno espera que todo se repase.
    onResync: () -> Unit = {},
    backEnabled: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    

    val api = remember(instanceUrl, cookie) {
        sharedApi ?: NetworkClient.createAuthenticatedApi(instanceUrl, cookie)
    }

    var books by remember { mutableStateOf<List<ShelfBookItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPaginating by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(1) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var isNetworkRefreshed by remember { mutableStateOf(false) }

    var sortMode by remember { mutableStateOf(ShelfSortMode.DEFAULT) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    // Collator con nivel PRIMARY: ignora mayúsculas/acentos pero respeta el orden
    // alfabético del idioma (p. ej. æ/ø/å en danés se colocan correctamente).
    val collator = remember {
        java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
    }

    // Datos enriquecidos por libro (autor, valoración, fechas) obtenidos de la página HTML,
    // cacheados localmente. Clave = ShelfBookItem.id.
    var enrichment by remember { mutableStateOf<Map<String, com.ferlagod.rocinante.data.model.BookEnrichment>>(emptyMap()) }
    var isEnriching by remember { mutableStateOf(false) }
    // Solo un recorrido de enriquecimiento a la vez: el de toda la estantería y el de un
    // libro suelto escriben en la misma caché.
    val enrichLock = remember { kotlinx.coroutines.sync.Mutex() }
    var enrichDone by remember { mutableStateOf(0) }
    var enrichTotal by remember { mutableStateOf(0) }
    // Se incrementa al pedir un resincronizado manual para forzar volver a obtener todo.
    var resyncTrigger by remember { mutableStateOf(0) }

    var selectedBookDetails by remember { mutableStateOf<com.ferlagod.rocinante.data.model.BookWyrmBookDetails?>(null) }
    var selectedBookReviews by remember { mutableStateOf<List<com.ferlagod.rocinante.data.model.ActivityPubActivity>>(emptyList()) }
    var fallbackCoverUrl by remember { mutableStateOf("") }
    var activeBookUrl by remember { mutableStateOf("") }
    var isLoadingDetails by remember { mutableStateOf(false) }
    // Número de la apertura de ficha en curso. Sube al abrir un libro y al cerrar la ficha,
    // así que una respuesta que llega tarde sabe que ya no le toca pintar nada.
    var detailsRequestId by remember { mutableStateOf(0) }

    var showTimePicker by remember { mutableStateOf(false) }

    // Estado de la lista, necesario para poder desplazarse hasta un libro concreto.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var highlightedBookId by remember { mutableStateOf<String?>(null) }
    // Libro al que ya se ha viajado, para animar solo la primera vez.
    var scrolledToTarget by remember { mutableStateOf<String?>(null) }

    val settingsPreferences = remember { com.ferlagod.rocinante.data.local.SettingsPreferences(context) }
    val settingsState by settingsPreferences.settingsFlow.collectAsState(initial = com.ferlagod.rocinante.data.local.SettingsData())

    val dataCache = remember(context) { com.ferlagod.rocinante.data.local.TimelineCache(context) }

    // Estantería que se está trayendo de la red, aparte de la que se ve. Sustituir lo que
    // hay en pantalla por la primera página encogía la lista a diez libros y la hacía crecer
    // otra vez página a página: la vista daba tumbos, y un libro al que se acabara de saltar
    // desaparecía a mitad de camino hasta que llegaba su página.
    var incoming by remember { mutableStateOf<List<ShelfBookItem>>(emptyList()) }
    // ¿Hay una lista cacheada en pantalla que merece la pena conservar mientras se refresca?
    var keepingCache by remember { mutableStateOf(false) }
    // Un refresco a medias no debe pisar ni la pantalla ni la caché con una lista truncada.
    var refreshFailed by remember { mutableStateOf(false) }

    LaunchedEffect(shelf.slug, refreshTrigger, currentPage) {
        var hadCompleteCache = false
        if (currentPage == 1) {
            isNetworkRefreshed = false
            refreshFailed = false
            val cachedBooks = dataCache.loadShelfBooks(shelf.slug)
            hadCompleteCache = !cachedBooks.isNullOrEmpty()
            if (cachedBooks != null && books.isEmpty()) {
                books = cachedBooks
                isLoading = false
            } else if (books.isEmpty()) {
                isLoading = true
            }
        } else {
            isPaginating = true
        }
        try {
            val cleanBase = if (instanceUrl.startsWith("http")) instanceUrl else "https://$instanceUrl"
            val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            val cleanUser = username.removePrefix("@").substringBefore("@").trim()
            // Por /books/ y no por /shelf/: las cuatro de serie responden por las dos vías,
            // pero las propias del usuario solo por esta.
            val shelfJsonUrl = "${baseUrl}user/$cleanUser/books/${shelf.slug}.json?page=$currentPage"

            val response = api.getShelfData(shelfJsonUrl)
            val fetchedItems = response.orderedItems ?: emptyList()

            if (currentPage == 1) {
                incoming = fetchedItems
                // Con una sola página no se sabe todavía si hay más: lo dirá la siguiente.
                hasMorePages = fetchedItems.isNotEmpty()
                // Con una lista cacheada delante no se toca la pantalla: se sigue viendo
                // entera mientras por detrás se recompone la nueva, y se cambia de golpe al
                // terminar. Guardar aquí la primera página truncaría además esa caché (y con
                // ella las estadísticas del perfil) hasta que acabase la paginación.
                keepingCache = hadCompleteCache && books.isNotEmpty()
                if (!keepingCache) {
                    books = incoming
                }
                isNetworkRefreshed = true
            } else {
                // Deduplicar: solo añadir libros cuyo id no esté ya en la lista,
                // para evitar repeticiones al re-ejecutarse el efecto.
                val existingIds = incoming.mapNotNull { it.id }.toSet()
                val newItems = fetchedItems.filter { it.id == null || it.id !in existingIds }
                // Que se acabó la estantería no lo dice el tamaño de la página sino que ya no
                // llegue nada nuevo. Pedida una página que no existe, la instancia devuelve la
                // última otra vez; y como la última de esta estantería traía justo diez libros,
                // «menos de diez es la última» no se cumplía nunca y se pedía la misma página
                // una y otra vez. La estantería no llegaba a darse por completa, así que la
                // lista nueva no llegaba a la pantalla y el relleno de datos por libro, que
                // espera a que termine la paginación, no empezaba nunca.
                hasMorePages = newItems.isNotEmpty()
                incoming = incoming + newItems
                if (!keepingCache) books = incoming
            }
            errorMessage = null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (books.isEmpty()) {
                errorMessage = com.ferlagod.rocinante.utils.NetworkErrors.message(context, e)
            }
            refreshFailed = true
            hasMorePages = false
        } finally {
            isLoading = false
            isPaginating = false
        }
    }

    // Lista mostrada: aplica la ordenación elegida. FINISHED_* y RATING_* usan los datos
    // enriquecidos; los libros sin dato quedan al final. Se recalcula al llegar más datos.
    // La misma ordenación vale para la estantería entera y para los libros de un autor, así
    // que se aplica desde un solo sitio.
    val sortBooks: (List<ShelfBookItem>, ShelfSortMode) -> List<ShelfBookItem> =
        remember(enrichment, collator) {
            { list, mode ->
                val byTitle = Comparator<ShelfBookItem> { a, b ->
                    collator.compare(a.sortTitle ?: a.title ?: "", b.sortTitle ?: b.title ?: "")
                }
                fun finishedOf(b: ShelfBookItem) = b.id?.let { enrichment[it]?.finished }
                fun ratingOf(b: ShelfBookItem) = b.id?.let { enrichment[it]?.rating }
                when (mode) {
                    ShelfSortMode.DEFAULT -> list
                    ShelfSortMode.TITLE_ASC -> list.sortedWith(byTitle)
                    ShelfSortMode.TITLE_DESC -> list.sortedWith(byTitle).reversed()
                    ShelfSortMode.FINISHED_DESC -> list.sortedWith(nullsLastComparator(true) { finishedOf(it) })
                    ShelfSortMode.FINISHED_ASC -> list.sortedWith(nullsLastComparator(false) { finishedOf(it) })
                    ShelfSortMode.RATING_DESC -> list.sortedWith(nullsLastComparator(true) { ratingOf(it) })
                    ShelfSortMode.RATING_ASC -> list.sortedWith(nullsLastComparator(false) { ratingOf(it) })
                }
            }
        }

    val displayedBooks = remember(books, sortMode, enrichment) { sortBooks(books, sortMode) }

    // ── Vista por series ──
    // «Leídos» puede verse agrupado en series: primero la lista de series y, al elegir una,
    // sus libros en el orden en que se leen. Son las mismas tarjetas de siempre; lo único que
    // cambia es qué libros entran y en qué orden.
    var showSeriesList by remember { mutableStateOf(false) }
    var openSeriesUrl by remember { mutableStateOf<String?>(null) }

    // Series de las que hay algún libro en la estantería. Solo salen los libros que alguien
    // haya atado a una serie en la instancia; del resto no se sabe y no aparecen.
    val seriesGroups = remember(books, enrichment) {
        books
            .mapNotNull { book ->
                val data = book.id?.let { enrichment[it] } ?: return@mapNotNull null
                val url = data.seriesUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                url to book
            }
            .groupBy({ it.first }, { it.second })
            .map { (url, grouped) ->
                SeriesGroup(
                    url = url,
                    name = grouped.firstNotNullOfOrNull { b ->
                        b.id?.let { enrichment[it]?.seriesName }?.takeIf { it.isNotBlank() }
                    } ?: url,
                    // Sin número el libro va al final: se sabe que es de la serie, pero no dónde.
                    books = grouped.sortedWith(
                        nullsLastComparator(false) { b -> b.id?.let { enrichment[it]?.seriesPosition } }
                    )
                )
            }
            .sortedWith(compareBy(collator) { it.name })
    }

    // ── Vista por autores ──
    // Igual que las series, pero por quien escribe: primero los autores en orden alfabético y,
    // al elegir uno, sus libros. Un libro escrito a cuatro manos sale bajo cada uno de ellos,
    // que es lo que se espera al buscar «qué he leído de esta persona».
    var showAuthorList by remember { mutableStateOf(false) }
    var openAuthorKey by remember { mutableStateOf<String?>(null) }

    // Mismo nombre, misma persona: se ignoran mayúsculas y espacios de más para que una ficha
    // escrita con dos espacios no abra un autor aparte.
    fun authorKey(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase()

    val authorGroups = remember(books, enrichment) {
        books
            .flatMap { book ->
                // El .json trae las URLs de los autores y el enriquecimiento sus nombres, en el
                // mismo orden. Cuando las cuentas no cuadran (un libro sin enriquecer todavía,
                // o un nombre con coma), se agrupa por el texto entero antes que repartirlo mal.
                val urls = book.authors?.filter { it.isNotBlank() }.orEmpty()
                val names = book.id?.let { enrichment[it]?.authorName }
                    ?.split(", ")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                when {
                    urls.isNotEmpty() && urls.size == names.size ->
                        names.map { name -> Triple(authorKey(name), name, book) }
                    names.isNotEmpty() -> {
                        val whole = names.joinToString(", ")
                        listOf(Triple(authorKey(whole), whole, book))
                    }
                    // Sin nombre todavía (el libro aún no se ha leído de la web) queda la URL,
                    // y en cuanto llegue el nombre se junta con los demás libros de esa persona.
                    urls.isNotEmpty() ->
                        urls.map { url -> Triple(url, url.trimEnd('/').substringAfterLast('/'), book) }
                    else -> emptyList()
                }
            }
            .groupBy { it.first }
            .map { (key, entries) ->
                AuthorGroup(
                    key = key,
                    // Si la instancia escribe el nombre de varias maneras, manda la más repetida.
                    name = entries.map { it.second }.filter { it.isNotBlank() }
                        .groupingBy { it }.eachCount()
                        .maxByOrNull { it.value }?.key ?: key,
                    books = entries.map { it.third }.distinctBy { it.id ?: it.title }
                )
            }
            .sortedWith(compareBy(collator) { it.name })
    }

    // Autor que llega del perfil: se entra directamente a sus libros. Los grupos se arman con
    // los datos por libro, que tardan un momento en llegar de la caché, así que no basta con
    // mirarlo al entrar; se espera a que haya autores y entonces se decide de una vez.
    // Mientras tanto se enseña la lista de autores, que es a dónde lleva ese camino.
    var authorTargetDone by remember(openAuthorName) { mutableStateOf(false) }
    LaunchedEffect(openAuthorName, authorGroups.isNotEmpty()) {
        if (openAuthorName == null || authorTargetDone) return@LaunchedEffect
        showAuthorList = true
        if (authorGroups.isEmpty()) return@LaunchedEffect
        // Si el nombre no cuadra con ningún grupo —la instancia lo escribe de otra manera, o
        // ese libro aún no se ha leído de la web— se queda en la lista, que al menos deja
        // elegir, en vez de en una pantalla vacía.
        openAuthorKey = authorGroups.firstOrNull { it.key == authorKey(openAuthorName) }?.key
        authorTargetDone = true
        // Servido, igual que el recorte: si no, volver a «Mis libros» desde la barra de abajo
        // volvería a meter en los libros de ese autor.
        onTargetTaken()
    }

    // Recorte que llega del perfil (un año, una nota, un idioma, un formato). Se copia a un
    // estado propio para que «Volver» pueda deshacerlo: si se leyera del parámetro, quitarlo
    // no serviría de nada porque volvería a entrar en la siguiente recomposición.
    var activeFilter by remember {
        mutableStateOf<com.ferlagod.rocinante.utils.ShelfFilter?>(null)
    }
    LaunchedEffect(openFilter) {
        if (openFilter == null) return@LaunchedEffect
        activeFilter = openFilter
        // Ya está servido: se avisa para que deje de mandarse. Si siguiera puesto, volver a
        // «Mis libros» desde la barra de abajo y abrir cualquier estantería lo aplicaría otra
        // vez, y un recorte que se acababa de deshacer reaparecería solo. Se avisa después de
        // guardarlo, no antes, para que quitarlo no llegue a borrar lo que se acaba de coger.
        onTargetTaken()
    }

    // Con un recorte manda el recorte; con una serie abierta manda su orden —el de lectura—;
    // con un autor abierto manda la ordenación elegida, y sin elegir nada sus libros salen
    // alfabéticos, que es como se busca en la obra de alguien. Sin nada abierto, la estantería.
    val visibleBooks = when {
        activeFilter != null -> sortBooks(
            com.ferlagod.rocinante.utils.ShelfFiltering.apply(
                books, enrichment, activeFilter!!
            ),
            sortMode
        )
        openSeriesUrl != null ->
            seriesGroups.firstOrNull { it.url == openSeriesUrl }?.books ?: displayedBooks
        openAuthorKey != null -> {
            val ofAuthor = authorGroups.firstOrNull { it.key == openAuthorKey }?.books.orEmpty()
            sortBooks(
                ofAuthor,
                if (sortMode == ShelfSortMode.DEFAULT) ShelfSortMode.TITLE_ASC else sortMode
            )
        }
        else -> displayedBooks
    }

    // Cargamos todas las páginas de la estantería (son pocas y personales). Es necesario
    // para ordenar y enriquecer la estantería completa, ya que el .json ignora ?sort=.
    LaunchedEffect(hasMorePages, isPaginating, isLoading, isNetworkRefreshed, currentPage) {
        // El tope es una red de seguridad, no un límite pensado: con diez o quince libros por
        // página son miles, muy por encima de cualquier estantería, y evita que una respuesta
        // rara de la instancia vuelva a dejar la app pidiendo páginas sin parar.
        if (hasMorePages && !isPaginating && !isLoading && isNetworkRefreshed && currentPage < 200) {
            currentPage++
        }
    }

    // Al guardar solo la primera página, la caché dejaba la estantería truncada: sin conexión
    // se veían diez libros y las estadísticas del perfil contaban de menos. Una vez recorridas
    // todas las páginas se persiste la lista completa y, si se venía mostrando la cacheada,
    // se cambia por la nueva de una sola vez, sin encogimientos intermedios.
    // Un refresco interrumpido a medias no toca nada: mejor la lista de antes, entera, que
    // una nueva a trozos, tanto en pantalla como en disco.
    LaunchedEffect(incoming, hasMorePages, isNetworkRefreshed, refreshFailed) {
        if (!hasMorePages && isNetworkRefreshed && !refreshFailed) {
            // La pantalla se cambia siempre, también si la estantería ha quedado vacía;
            // si no, vaciarla desde la web dejaría los libros de antes ahí para siempre.
            books = incoming
            keepingCache = false
            // La caché, en cambio, no se pisa con una lista vacía: una respuesta rara
            // borraría la estantería entera y con ella las estadísticas del perfil.
            if (incoming.isNotEmpty()) dataCache.saveShelfBooks(shelf.slug, incoming)
        }
    }

    // Carga inicial de la caché de enriquecimiento (autor, valoración, fechas por libro).
    LaunchedEffect(Unit) {
        enrichment = dataCache.loadEnrichment()
    }

    // Salto al libro que se ha pulsado en la búsqueda: se desplaza hasta él y se resalta
    // un momento para que se vea dónde ha caído dentro de la estantería.
    LaunchedEffect(highlightBookId, displayedBooks, isLoading, hasMorePages) {
        val targetId = highlightBookId ?: return@LaunchedEffect
        val index = displayedBooks.indexOfFirst { it.id == targetId }
        if (index < 0) {
            // Puede que la estantería aún esté cargando; solo nos rendimos al terminar.
            if (!isLoading && !hasMorePages) onHighlightConsumed()
            return@LaunchedEffect
        }
        // Delante de los libros hay elementos propios de la lista que desplazan los índices.
        val leadingItems = (if (isLoadingDetails) 1 else 0) + (if (shelf.slug == "reading") 1 else 0)
        val position = (index + leadingItems).coerceAtLeast(0)

        // La estantería sigue trayendo páginas detrás y cada una recompone la lista, así que
        // el libro cambia de posición varias veces. Solo el primer viaje se anima, para que se
        // vea adónde lleva el salto; los reajustes posteriores son instantáneos y el libro se
        // queda quieto mientras la lista crece bajo él. Animarlos también hacía que la pantalla
        // subiese y bajase buscando el libro hasta que terminaba la paginación.
        if (scrolledToTarget != targetId) {
            listState.animateScrollToItem(position)
            scrolledToTarget = targetId
            highlightedBookId = targetId
        } else {
            listState.scrollToItem(position)
        }

        if (!isLoading && !hasMorePages) onHighlightConsumed()
    }

    // El resaltado se apaga en su propio efecto: descartar el objetivo cambia la clave del
    // efecto anterior y lo cancela, así que una espera puesta allí no llegaría a terminar.
    // Se mantiene mientras siguen llegando páginas y la cuenta atrás empieza al asentarse,
    // para que no se apague justo cuando la lista todavía se está recolocando.
    LaunchedEffect(highlightedBookId, isLoading, hasMorePages) {
        if (highlightedBookId != null && !isLoading && !hasMorePages) {
            kotlinx.coroutines.delay(2500)
            highlightedBookId = null
        }
    }

    // Primera vez (o resincronizado): una vez cargada TODA la estantería, se obtienen los
    // datos que faltan libro a libro, de forma secuencial y suave, mostrando una barra de
    // progreso. Al terminar queda todo cacheado y las siguientes aperturas son inmediatas.
    LaunchedEffect(books, hasMorePages, resyncTrigger) {
        if (books.isEmpty() || hasMorePages) return@LaunchedEffect
        // El candado, y no la bandera de la barra de progreso, es lo que impide que dos
        // recorridos se pisen. Mirar la bandera dejaba la estantería a medio rellenar: abrir un
        // libro nada más entrar la levanta para su propio refresco, y el recorrido que iba a
        // empezar se daba media vuelta y no volvía; lo mismo al pedir «actualizar datos»
        // mientras uno estaba en marcha, que además cancelaba el que había. Ahora el segundo
        // espera al primero, y como se recuenta lo que falta al entrar, no rehace su trabajo.
        enrichLock.withLock {
            val ids = books.mapNotNull { it.id }
            val snapshot = enrichment
            // Faltan las que nunca se leyeron y las que se guardaron con un formato anterior
            // (les faltarían campos nuevos, p. ej. los identificadores de la estantería).
            val missing = ids.filter {
                val cached = snapshot[it]
                cached == null || cached.schemaVersion != BookWyrmScraper.ENRICHMENT_SCHEMA_VERSION
            }
            if (missing.isEmpty()) return@withLock

            isEnriching = true
            enrichDone = 0
            enrichTotal = missing.size
            val working = snapshot.toMutableMap()
            try {
                for (id in missing) {
                    val enriched = BookWyrmScraper.scrapeBookEnrichment(api, id)
                    if (enriched != null) {
                        working[id] = enriched
                        enrichment = working.toMap()
                        // Se guarda libro a libro y no el mapa entero: la ficha también escribe en
                        // esta caché mientras el resincronizado avanza, y volcar aquí una copia
                        // hecha antes borraría lo que acabase de apuntar. De paso, lo ya leído
                        // queda guardado aunque se salga a mitad.
                        dataCache.mergeEnrichment(enriched)
                    }
                    enrichDone++
                    kotlinx.coroutines.delay(250)
                }
            } finally {
                isEnriching = false
            }
        }
    }

    // «Volver» deshace un paso cada vez: de una serie a la lista de series, de ahí a la
    // estantería, y solo entonces se sale a la lista de estanterías.
    val goBack: () -> Unit = {
        when {
            activeFilter != null -> activeFilter = null
            openSeriesUrl != null -> openSeriesUrl = null
            openAuthorKey != null -> openAuthorKey = null
            showSeriesList -> showSeriesList = false
            showAuthorList -> showAuthorList = false
            else -> onBack()
        }
    }

    // El botón de atrás del móvil deshace exactamente los mismos pasos que el de la pantalla.
    // Ya en la lista de estanterías esta pantalla no existe, así que el toque le llega a quien
    // lo atiende desde fuera y es entonces cuando se pregunta si se quiere salir de la app.
    androidx.activity.compose.BackHandler(enabled = backEnabled) { goBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = goBack) {
                Text(stringResource(R.string.shelf_back))
            }
            // El recorte dice cuál es, para que se sepa qué se está viendo: el año, la nota,
            // el idioma o el formato que se tocó en el perfil.
            val filterTitle = activeFilter?.let { f ->
                when (f) {
                    is com.ferlagod.rocinante.utils.ShelfFilter.Year -> f.year.toString()
                    is com.ferlagod.rocinante.utils.ShelfFilter.Rating ->
                        stringResource(R.string.shelf_filter_rating, formatStars(f.stars))
                    is com.ferlagod.rocinante.utils.ShelfFilter.Language -> f.label
                    is com.ferlagod.rocinante.utils.ShelfFilter.Format ->
                        com.ferlagod.rocinante.ui.components.formatLabel(f.name)
                }
            }
            Text(
                text = when {
                    filterTitle != null -> filterTitle
                    openSeriesUrl != null ->
                        seriesGroups.firstOrNull { it.url == openSeriesUrl }?.name ?: shelf.title
                    openAuthorKey != null ->
                        authorGroups.firstOrNull { it.key == openAuthorKey }?.name ?: shelf.title
                    showSeriesList -> stringResource(R.string.series_title)
                    showAuthorList -> stringResource(R.string.authors_title)
                    else -> shelf.title
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            // Agrupar por series: solo en «Leídos», y solo mientras se esté viendo la
            // estantería entera. Dentro de una serie el orden lo da su numeración.
            if (shelf.slug == "read" && !showSeriesList && !showAuthorList) {
                IconButton(onClick = { showSeriesList = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = stringResource(R.string.series_title)
                    )
                }
                // Agrupar por autor, al lado de las series y con las mismas reglas.
                IconButton(onClick = { showAuthorList = true }) {
                    Icon(
                        imageVector = Icons.Filled.People,
                        contentDescription = stringResource(R.string.authors_title)
                    )
                }
            }
            Box {
                // En la lista de autores no hay nada que ordenar; dentro de un autor sí, y es la
                // misma ordenación de la estantería. Dentro de una serie no, porque allí el
                // orden es el de lectura.
                if (!showSeriesList && (!showAuthorList || openAuthorKey != null)) {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.shelf_sort)
                        )
                    }
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shelf_sort_default)) },
                        onClick = { sortMode = ShelfSortMode.DEFAULT; sortMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shelf_sort_title_asc)) },
                        onClick = { sortMode = ShelfSortMode.TITLE_ASC; sortMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shelf_sort_title_desc)) },
                        onClick = { sortMode = ShelfSortMode.TITLE_DESC; sortMenuExpanded = false }
                    )
                    if (shelf.slug == "read") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shelf_sort_finished_desc)) },
                            onClick = { sortMode = ShelfSortMode.FINISHED_DESC; sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shelf_sort_finished_asc)) },
                            onClick = { sortMode = ShelfSortMode.FINISHED_ASC; sortMenuExpanded = false }
                        )
                    }
                    if (shelf.slug == "read" || shelf.slug == "reading") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shelf_sort_rating_desc)) },
                            onClick = { sortMode = ShelfSortMode.RATING_DESC; sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shelf_sort_rating_asc)) },
                            onClick = { sortMode = ShelfSortMode.RATING_ASC; sortMenuExpanded = false }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shelf_resync)) },
                        onClick = {
                            enrichment = emptyMap()
                            resyncTrigger++
                            onResync()
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (isEnriching && enrichTotal > 0) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = stringResource(R.string.shelf_loading_data, enrichDone, enrichTotal),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (enrichTotal > 0) enrichDone.toFloat() / enrichTotal else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Un tropiezo puntual (la instancia va lenta, un libro que no abre) no debe llevarse
        // por delante la estantería que ya está en pantalla: el aviso se pone encima y los
        // libros siguen ahí. Solo cuando no hay nada que enseñar ocupa la pantalla entera.
        if (errorMessage != null && books.isNotEmpty()) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (errorMessage != null && books.isEmpty()) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else if (isLoading && currentPage == 1) {
            ShelfSkeletonLoader()
        } else if (books.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.shelf_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoadingDetails) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                }

                if (shelf.slug == "reading") {
                    item {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.reminder_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (settingsState.reminderEnabled) {
                                    Text(
                                        text = stringResource(R.string.reminder_active_at, String.format("%02d:%02d", settingsState.reminderHour, settingsState.reminderMinute)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = { showTimePicker = true }) {
                                        Text(stringResource(R.string.reminder_modify_time))
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.reminder_inactive_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(onClick = onNavigateToSettings) {
                                        Text(stringResource(R.string.reminder_go_to_settings))
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAuthorList && openAuthorKey == null) {
                    if (authorGroups.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.authors_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    items(
                        count = authorGroups.size,
                        key = { index -> authorGroups[index].key }
                    ) { index ->
                        val group = authorGroups[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openAuthorKey = group.key }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                CoverFan(group.books)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.series_books_count,
                                        group.books.size,
                                        group.books.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (showSeriesList && openSeriesUrl == null) {
                    if (seriesGroups.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.series_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    items(
                        count = seriesGroups.size,
                        key = { index -> seriesGroups[index].url }
                    ) { index ->
                        val group = seriesGroups[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openSeriesUrl = group.url }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                CoverFan(group.books)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.series_books_count,
                                        group.books.size,
                                        group.books.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                items(
                    count = visibleBooks.size,
                    key = { index -> visibleBooks[index].id ?: index }
                ) { index ->
                    val book = visibleBooks[index]
                    val isHighlighted = book.id != null && book.id == highlightedBookId

                    Card(
                        border = if (isHighlighted) {
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                        colors = if (isHighlighted) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else CardDefaults.cardColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isLoadingDetails) return@clickable
                                val bookUrl = book.id
                                if (!bookUrl.isNullOrEmpty()) {
                                    if (settingsState.openLinksExternally) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(bookUrl))
                                        context.startActivity(intent)
                                    } else {
                                        activeBookUrl = bookUrl
                                        fallbackCoverUrl = book.cover?.url ?: ""
                                        isLoadingDetails = true
                                        // El aviso del intento anterior se va al volver a
                                        // probar; si no, se queda ahí aunque ya funcione.
                                        errorMessage = null
                                        // Cada apertura lleva su número: lo que llegue tarde de
                                        // la red solo se pinta si sigue siendo este libro y la
                                        // ficha no se ha cerrado mientras tanto.
                                        detailsRequestId++
                                        val requestId = detailsRequestId
                                        coroutineScope.launch {
                                            try {
                                                com.ferlagod.rocinante.data.repository.BookPageLoader.load(
                                                    api = api,
                                                    cache = dataCache,
                                                    cacheKey = bookUrl,
                                                    resolveDetailsUrl = { BookWyrmUtils.ensureJsonUrl(bookUrl) },
                                                    onDetails = { details, fromCache ->
                                                        if (detailsRequestId != requestId) return@load
                                                        selectedBookDetails = details
                                                        // Con la ficha ya en pantalla (venga de
                                                        // donde venga) se apaga la espera, para
                                                        // poder abrir otro libro sin esperar al
                                                        // refresco que sigue por detrás.
                                                        isLoadingDetails = false
                                                        if (fromCache) selectedBookReviews = emptyList()
                                                    },
                                                    onReviews = { reviews ->
                                                        if (detailsRequestId == requestId) selectedBookReviews = reviews
                                                    },
                                                    onFailure = { e, hadCache ->
                                                        // Con la ficha ya abierta desde la caché no
                                                        // se avisa de nada: hay algo que leer y el
                                                        // refresco llegará la próxima vez.
                                                        if (!hadCache && detailsRequestId == requestId) {
                                                            errorMessage = com.ferlagod.rocinante.utils.NetworkErrors.message(context, e)
                                                        }
                                                    }
                                                )
                                            } finally {
                                                isLoadingDetails = false
                                            }

                                            // Oportunista: refrescamos los datos enriquecidos de este libro con la
                                            // barra de progreso etiquetada (1/1), para que se vea qué está pasando.
                                            // La barra 1/1 solo si no hay un recorrido en
                                            // marcha, para no pisarle la suya; el refresco de
                                            // este libro se hace igualmente.
                                            val ownProgress = !isEnriching
                                            if (ownProgress) {
                                                isEnriching = true
                                                enrichDone = 0
                                                enrichTotal = 1
                                            }
                                            try {
                                                BookWyrmScraper.scrapeBookEnrichment(api, bookUrl)?.let { enriched ->
                                                    enrichment = enrichment.toMutableMap()
                                                        .apply { put(bookUrl, enriched) }
                                                    // Solo este libro: volcar el mapa entero
                                                    // borraría lo que el recorrido acabara de
                                                    // guardar mientras tanto.
                                                    enrichLock.withLock {
                                                        val stored = dataCache.loadEnrichment()
                                                        stored[bookUrl] = enriched
                                                        dataCache.saveEnrichment(stored)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                            } finally {
                                                if (ownProgress) {
                                                    enrichDone = 1
                                                    isEnriching = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val enrich = book.id?.let { enrichment[it] }
                            val lang = book.languages?.firstOrNull()
                            val flag = com.ferlagod.rocinante.utils.LanguageFlags.flagFor(lang)
                            val rating = enrich?.rating
                            val flagText = flag ?: lang?.takeIf { it.isNotBlank() }

                            val coverUrl = book.cover?.url
                            if (!coverUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = stringResource(R.string.book_cover_desc),
                                    modifier = Modifier
                                        .width(70.dp)
                                        .height(105.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                // Título centrado en la parte superior.
                                Text(
                                    text = book.title ?: stringResource(R.string.book_no_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Subtítulo centrado justo bajo el título, algo más pequeño.
                                book.subtitle?.trim()?.takeIf { it.isNotEmpty() }?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Estrellas centradas, justo bajo el título.
                                if (rating != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        RatingStars(rating = rating)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Autor (izquierda), con un pequeño icono de persona.
                                enrich?.authorName?.takeIf { it.isNotBlank() }?.let { author ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = author,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Nº de páginas seguido de la bandera del idioma.
                                val pagesFlag = buildList {
                                    book.pages?.let { add("📖 $it") }
                                    flagText?.let { add(it) }
                                }.joinToString("  ")
                                if (pagesFlag.isNotEmpty()) {
                                    Text(
                                        text = pagesFlag,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Fecha (fin de lectura en "read", inicio en "reading").
                                val rawDate = when (shelf.slug) {
                                    "read" -> enrich?.finished
                                    "reading" -> enrich?.started
                                    else -> null
                                }
                                formatDisplayDate(rawDate)?.let { dateStr ->
                                    // En "read", si hay fecha de inicio y de fin, añadimos "(N días)".
                                    val days = if (shelf.slug == "read") {
                                        readingDays(enrich?.started, enrich?.finished)
                                    } else null
                                    val dateText = if (days != null) {
                                        "📅 $dateStr (${pluralStringResource(R.plurals.reading_days, days, days)})"
                                    } else {
                                        "📅 $dateStr"
                                    }
                                    Text(
                                        text = dateText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Serie del libro y qué número hace en ella. Solo consta si
                                // alguien la ha atado en la instancia; mientras no, no hay línea.
                                enrich?.seriesName?.takeIf { it.isNotBlank() }?.let { seriesName ->
                                    val position = enrich.seriesPosition
                                    Text(
                                        text = if (position != null) {
                                            "📚 " + stringResource(
                                                R.string.series_book_of, seriesName, position
                                            )
                                        } else {
                                            "📚 $seriesName"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
            activeBookKey = activeBookUrl,
            fallbackCoverUrl = fallbackCoverUrl,
            currentShelf = shelf.slug,
            api = api,
            context = context,
            coroutineScope = coroutineScope,
            onDismiss = {
                // Cerrar cuenta como apertura nueva: si la petición sigue viva, lo que traiga
                // ya no vuelve a abrir la ficha en la cara de quien la acaba de cerrar.
                detailsRequestId++
                selectedBookDetails = null
                selectedBookReviews = emptyList()
            },
            onShelved = { refreshTrigger++ },
            // Quitado de la estantería: se saca de la lista en el acto, tanto de la que se ve
            // como de la que se está recomponiendo por detrás, sin releer la estantería entera.
            onRemovedFromShelf = { removedId ->
                books = books.filterNot { it.id == removedId }
                incoming = incoming.filterNot { it.id == removedId }
            },
            // Estrellas al instante: pasamos lo que ya tenemos cacheado de la estantería.
            initialEnrichment = enrichment[activeBookUrl],
            // La caché de enriquecimiento no se vuelve a leer una vez guardada, así que lo
            // que la ficha lea de la web (p. ej. unas fechas de lectura recién cambiadas)
            // se guarda aquí para que la estantería lo enseñe sin esperar a un resincronizado.
            onEnrichmentUpdated = { fresh ->
                val updated = enrichment.toMutableMap().apply { put(activeBookUrl, fresh) }
                enrichment = updated
                coroutineScope.launch { dataCache.saveEnrichment(updated) }
            }
        )
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            initialHour = settingsState.reminderHour,
            initialMinute = settingsState.reminderMinute,
            onConfirm = { hour, minute ->
                coroutineScope.launch {
                    settingsPreferences.setReminderTime(hour, minute)
                    com.ferlagod.rocinante.workers.ReminderManager.schedule(context, hour, minute)
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

/**
 * Diálogo para seleccionar a qué hora y minuto se debe lanzar el recordatorio diario de lectura.
 *
 * @param initialHour Hora inicial a mostrar (formato 24h).
 * @param initialMinute Minuto inicial a mostrar.
 * @param onDismiss Callback ejecutado al cancelar el diálogo.
 * @param onConfirm Callback ejecutado al confirmar la selección, devuelve la hora y minuto elegidos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.settings_logout_confirm_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_logout_cancel))
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reminder_dialog_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                TimePicker(state = state)
            }
        }
    )
}

/**
 * Animación de esqueleto (Skeleton Loader) para la lista de estanterías, mostrada mientras se cargan los datos.
 */
@Composable
fun ShelfSkeletonLoader() {
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
        contentPadding = PaddingValues(16.dp),
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
                                .fillMaxWidth(0.9f)
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(LocalContentColor.current.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(16.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(LocalContentColor.current.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(24.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(LocalContentColor.current.copy(alpha = alpha))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(24.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(LocalContentColor.current.copy(alpha = alpha))
                            )
                        }
                    }
                }
            }
        }
    }
}