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
package com.ferlagod.rocinante.ui.screens.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.api.OpenLibraryClient
import com.ferlagod.rocinante.data.local.SubjectCache
import com.ferlagod.rocinante.data.model.OpenLibraryEdition
import com.ferlagod.rocinante.data.model.OpenLibrarySubject
import com.ferlagod.rocinante.data.model.OpenLibraryWork
import com.ferlagod.rocinante.utils.ExploreQuery
import kotlinx.coroutines.launch
import java.util.Locale

/** Los diecisiete idiomas de la aplicación, que son los que se ofrecen para filtrar. */
private val APP_LANGUAGES = listOf(
    "da", "en", "es", "de", "fr", "nl", "ro", "cs", "el", "it", "sv", "pt", "pl", "uk", "fi", "ca", "gl"
)

private const val PAGE_SIZE = 24

/** Alto de la fila donde están el orden y «Buscar». La barra entera puede ser más alta. */
private val BOTTOM_BAR_HEIGHT = 72.dp

/**
 * Los filtros que aparecen desplegados sobre el botón flotante.
 *
 * Son cinco y no once por una razón de sitio: cada uno ocupa unos 60dp con su separación, así
 * que once se salen por arriba de la pantalla de un móvil pequeño. El resto vive detrás de «más
 * filtros», que abre una hoja donde caben y se pueden explicar.
 */
private val QUICK_FIELDS = listOf(
    ExploreQuery.Field.MATERIA,
    ExploreQuery.Field.TITULO,
    ExploreQuery.Field.IDIOMA,
    ExploreQuery.Field.ANIO,
    ExploreQuery.Field.AUTOR
)

private val MORE_FIELDS = ExploreQuery.Field.entries.filterNot { it in QUICK_FIELDS }

/**
 * Lo que hay que guardar cuando el sistema se lleva la pantalla por delante —girarla es lo
 * corriente, pero también vale volver de otra aplicación—. Sin esto, girar el teléfono borra los
 * filtros que uno acaba de montar y los libros que ya había buscado.
 *
 * Se guardan como JSON y no como Parcelable a propósito: [ExploreQuery] es Kotlin a secas, se
 * prueba sin Android delante, y hacerlo Parcelable por una necesidad de la pantalla lo ataría al
 * teléfono. Gson ya está en el proyecto y con los enum se entiende por su nombre.
 */
private val SAVER_GSON = Gson()

private val ROWS_SAVER = listSaver<SnapshotStateList<ExploreQuery.Row>, String>(
    save = { filas -> filas.map { SAVER_GSON.toJson(it) } },
    restore = { guardadas ->
        guardadas.mapNotNull {
            // Una fila que no se pueda leer se tira. Perder un filtro es molesto; reventar al
            // volver a la pantalla, no tiene arreglo desde dentro de la aplicación.
            runCatching { SAVER_GSON.fromJson(it, ExploreQuery.Row::class.java) }.getOrNull()
        }.toMutableStateList()
    }
)

private val WORKS_SAVER = listSaver<List<OpenLibraryWork>, String>(
    save = { obras -> obras.map { SAVER_GSON.toJson(it) } },
    restore = { guardadas ->
        guardadas.mapNotNull {
            runCatching { SAVER_GSON.fromJson(it, OpenLibraryWork::class.java) }.getOrNull()
        }
    }
)

/** El orden, por su nombre. Si algún día se quita una opción, se vuelve a la relevancia. */
private val ORDEN_SAVER = Saver<ExploreQuery.Orden, String>(
    save = { it.name },
    restore = {
        runCatching { ExploreQuery.Orden.valueOf(it) }.getOrDefault(ExploreQuery.Orden.RELEVANCIA)
    }
)

/**
 * «Explorar»: encontrar un libro nuevo apilando filtros sobre el catálogo de Open Library.
 *
 * Los libros salen de Open Library y no de la instancia porque BookWyrm no sabe buscar por
 * materia —su índice solo cubre título, subtítulo, autor y serie—. Lo que se elija entra en la
 * instancia por `resolve-book`, que sí tiene conector para openlibrary.org.
 *
 * La búsqueda no se lanza sola al tocar un filtro, sino con el botón de abajo. Open Library es
 * lenta y se cae a rachas: disparar una petición por cada letra que se escribe deja la pantalla
 * peleándose consigo misma.
 *
 * @param onOpenLocalBook Recibe la dirección del libro ya en la instancia, para abrir su ficha.
 * @param backEnabled Si el botón de atrás del móvil lo atiende esta pantalla. Falso mientras la
 *   pestaña no sea la que se ve, para no quitárselo a la que sí lo está.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    api: BookWyrmApi,
    modifier: Modifier = Modifier,
    onOpenLocalBook: (String) -> Unit,
    onBack: () -> Unit,
    backEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLocale = LocalConfiguration.current.locales[0]

    val subjectCache = remember { SubjectCache(context) }
    // Null es que no está abierto. Un índice es que se está cambiando esa fila y no añadiendo una.
    var subjectPickerFor by remember { mutableStateOf<Int?>(null) }
    var subjectPickerOpen by remember { mutableStateOf(false) }

    val rows = rememberSaveable(saver = ROWS_SAVER) { mutableStateListOf<ExploreQuery.Row>() }
    var orden by rememberSaveable(stateSaver = ORDEN_SAVER) {
        mutableStateOf(ExploreQuery.Orden.RELEVANCIA)
    }
    var ordenMenuOpen by remember { mutableStateOf(false) }

    // Lo que mide la barra de abajo de verdad. Antes era un número fijo, pero ahora crece cuando
    // aparece el aviso de que los filtros han cambiado, y el botón flotante tiene que subir con
    // ella o se queda tapando el orden.
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(BOTTOM_BAR_HEIGHT) }

    var dialOpen by remember { mutableStateOf(false) }
    var moreSheetOpen by remember { mutableStateOf(false) }

    var works by rememberSaveable(stateSaver = WORKS_SAVER) {
        mutableStateOf<List<OpenLibraryWork>>(emptyList())
    }
    var total by rememberSaveable { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editionsOf by remember { mutableStateOf<OpenLibraryWork?>(null) }

    // La consulta con la que se pidió lo que hay en pantalla. Se guarda aparte de las filas
    // porque «ver más» tiene que pedir la página siguiente de *esta* búsqueda: si usara las filas
    // de ahora, cambiar un filtro y seguir paginando mezclaría resultados de dos búsquedas.
    var activeQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSort by rememberSaveable { mutableStateOf<String?>(null) }

    val currentQuery = ExploreQuery.build(rows.toList(), orden)
    // Los filtros ya no son los de los resultados que se están viendo. No se busca solo: se avisa.
    val stale = activeQuery != null && (currentQuery != activeQuery || orden.value != activeSort)

    // El botón de atrás del móvil deshace un paso cada vez, igual que la flecha de arriba:
    // primero cierra el desplegable de añadir filtros y, si no hay nada abierto, devuelve a la
    // búsqueda. Lo que se pone encima —las hojas de abajo y el diálogo de ediciones— se cierra
    // solo, y por eso no sale aquí. Ya en la búsqueda esta pantalla no existe, así que el toque
    // llega a quien lo atiende desde fuera y es entonces cuando se pregunta si se sale de la app.
    BackHandler(enabled = backEnabled) {
        if (dialOpen) dialOpen = false else onBack()
    }

    /** Añade una fila del tipo pedido, ya con el operador Y, que es el que casi siempre se quiere. */
    fun addRow(field: ExploreQuery.Field) {
        val row = when (field) {
            // El idioma entra con el de la aplicación puesto: es en el que se lee, y así la fila
            // sirve nada más añadirla en vez de obligar a abrir un desplegable.
            ExploreQuery.Field.IDIOMA -> ExploreQuery.Row(
                field = field,
                text = OpenLibraryClient.marcCode(appLocale.language) ?: "eng"
            )
            else -> ExploreQuery.Row(field = field)
        }
        rows.add(row)
    }

    /**
     * Mete una materia como filtro, o cambia la de una fila que ya estaba.
     *
     * La fila guarda la clave y enseña el nombre: buscar es cosa de `subject_key`, pero en la
     * pantalla tiene que poner «Historical fiction» y no `historical_fiction`.
     *
     * Al añadir una nueva entra también el idioma de la aplicación si no había ninguno. Es lo que
     * la pantalla hacía por su cuenta antes de que los filtros fueran una lista, y sin él una
     * materia sola devuelve los mismos best sellers en inglés a todo el mundo.
     */
    fun pickSubject(subject: OpenLibrarySubject, index: Int?) {
        val key = subject.subjectKey ?: return
        val row = ExploreQuery.Row(
            field = ExploreQuery.Field.MATERIA,
            text = key,
            label = subject.name
        )
        if (index != null && index in rows.indices) {
            rows[index] = rows[index].copy(text = key, label = subject.name)
        } else {
            if (rows.none { it.field == ExploreQuery.Field.IDIOMA }) {
                addRow(ExploreQuery.Field.IDIOMA)
            }
            rows.add(0, row)
        }
        scope.launch { subjectCache.remember(subject) }
    }

    fun search(append: Boolean) {
        val q = if (append) activeQuery else currentQuery
        if (q == null) return
        val sort = if (append) activeSort else orden.value
        searching = true
        error = null
        scope.launch {
            try {
                val response = OpenLibraryClient.api.search(
                    q = q,
                    sort = sort,
                    limit = PAGE_SIZE,
                    offset = if (append) works.size else 0
                )
                total = response.numFound
                works = if (append) works + response.docs else response.docs
                activeQuery = q
                activeSort = sort
                if (works.isEmpty()) {
                    error = context.getString(R.string.explore_no_results)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Open Library se cae y limita por rachas; el aviso dice que es de ellos y no
                // de la instancia, para que nadie vaya a buscar el fallo donde no está.
                error = context.getString(R.string.explore_source_unavailable)
            } finally {
                searching = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
                Text(
                    text = stringResource(R.string.explore_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Sitio para que el botón flotante no tape la última fila ni el último libro.
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.explore_no_filters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                itemsIndexed(rows) { index, row ->
                    Column {
                        // La primera fila no lleva operador: no hay nada encima a lo que unirla.
                        if (index > 0) {
                            OperatorChip(
                                op = row.op,
                                onCycle = {
                                    rows[index] = row.copy(
                                        op = when (row.op) {
                                            ExploreQuery.Op.Y -> ExploreQuery.Op.O
                                            ExploreQuery.Op.O -> ExploreQuery.Op.NO
                                            ExploreQuery.Op.NO -> ExploreQuery.Op.Y
                                        }
                                    )
                                }
                            )
                        }
                        FilterRow(
                            row = row,
                            appLocale = appLocale,
                            onChange = { rows[index] = it },
                            onPickSubject = {
                                subjectPickerFor = index
                                subjectPickerOpen = true
                            },
                            onRemove = { rows.removeAt(index) }
                        )
                    }
                }

                if (searching && works.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator() }
                    }
                }

                error?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (works.isNotEmpty()) {
                    item {
                        Text(
                            text = pluralStringResource(R.plurals.explore_results, total, total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(works, key = { it.key ?: it.hashCode().toString() }) { work ->
                    WorkCard(work = work, onClick = { editionsOf = work })
                }

                if (works.isNotEmpty() && works.size < total) {
                    item {
                        OutlinedButton(
                            onClick = { search(append = true) },
                            enabled = !searching,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (searching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Text(stringResource(R.string.explore_more))
                            }
                        }
                    }
                }

                // De dónde salen los datos. Open Library lo pide y además explica por qué esto no
                // se parece a lo que hay en la instancia.
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.explore_source_credit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // El orden vive aquí abajo, al lado de «Buscar», y no entre los filtros: no filtra
            // nada, es cómo se enseña lo que salga, así que pertenece al botón que lo pide.
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } }
            ) {
                Column {
                    if (stale) {
                        Text(
                            text = stringResource(R.string.explore_stale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(BOTTOM_BAR_HEIGHT)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        // El orden a la izquierda y «Buscar» pegado al borde derecho. El peso sin
                        // relleno deja que el orden se encoja si el nombre es largo, en vez de
                        // empujar al botón fuera de la pantalla.
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OrdenSelector(
                            orden = orden,
                            menuOpen = ordenMenuOpen,
                            onMenuOpen = { ordenMenuOpen = it },
                            modifier = Modifier.weight(1f, fill = false)
                        ) { orden = it }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { search(append = false) },
                            // Sin filtros no se busca: con la consulta vacía Open Library
                            // devolvería su catálogo entero.
                            enabled = currentQuery != null && !searching
                        ) {
                            Text(stringResource(R.string.explore_search))
                        }
                    }
                }
            }
        }

        // El velo. Por él la ruleta no puede vivir en el hueco de botón flotante de un Scaffold:
        // ese hueco no sabe dibujar nada por detrás de sí mismo.
        AnimatedVisibility(visible = dialOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .clickable { dialOpen = false }
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = barHeight + 16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = dialOpen,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QUICK_FIELDS.forEach { field ->
                        DialItem(stringResource(fieldLabel(field))) {
                            dialOpen = false
                            // La materia no se escribe: se elige de las que Open Library tiene,
                            // porque una que ellos no tengan no da error, da cero libros.
                            if (field == ExploreQuery.Field.MATERIA) {
                                subjectPickerFor = null
                                subjectPickerOpen = true
                            } else {
                                addRow(field)
                            }
                        }
                    }
                    DialItem(stringResource(R.string.explore_more_filters)) {
                        dialOpen = false
                        moreSheetOpen = true
                    }
                }
            }

            FloatingActionButton(onClick = { dialOpen = !dialOpen }) {
                Icon(
                    imageVector = if (dialOpen) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = stringResource(
                        if (dialOpen) R.string.explore_add_filter_close else R.string.explore_add_filter
                    )
                )
            }
        }
    }

    if (moreSheetOpen) {
        ModalBottomSheet(onDismissRequest = { moreSheetOpen = false }) {
            Text(
                text = stringResource(R.string.explore_more_filters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            MORE_FIELDS.forEach { field ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            moreSheetOpen = false
                            addRow(field)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(fieldLabel(field)), style = MaterialTheme.typography.bodyLarge)
                    fieldHelp(field)?.let { help ->
                        Text(
                            text = stringResource(help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (subjectPickerOpen) {
        SubjectPickerSheet(
            cache = subjectCache,
            onPick = { subject ->
                subjectPickerOpen = false
                pickSubject(subject, subjectPickerFor)
                subjectPickerFor = null
            },
            onDismiss = {
                subjectPickerOpen = false
                subjectPickerFor = null
            }
        )
    }

    editionsOf?.let { work ->
        EditionPickerDialog(
            work = work,
            preferredLanguage = rows.firstOrNull { it.field == ExploreQuery.Field.IDIOMA }?.text,
            appLocale = appLocale,
            api = api,
            onOpenLocalBook = { url ->
                editionsOf = null
                onOpenLocalBook(url)
            },
            onDismiss = { editionsOf = null }
        )
    }
}

/**
 * Las materias de Open Library, que son de las que se elige.
 *
 * No se ofrecen las de los libros propios aunque las tengamos a mano: vienen de los catálogos de
 * la instancia y no del suyo, así que una que ellos no tengan no da error, da una pantalla sin
 * libros. Preguntándoles a ellos, la materia siempre existe y además llega con su clave exacta,
 * que no se puede deducir del nombre.
 *
 * Lo contestado se guarda en disco un mes. El catálogo de materias apenas cambia, se escribe
 * hacia delante y hacia atrás sobre lo mismo, y sobre todo: con lo ya preguntado guardado, elegir
 * una materia conocida sigue funcionando el día que Open Library no contesta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectPickerSheet(
    cache: SubjectCache,
    onPick: (OpenLibrarySubject) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OpenLibrarySubject>>(emptyList()) }
    var recent by remember { mutableStateOf<List<OpenLibrarySubject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { recent = cache.recent() }

    LaunchedEffect(query) {
        val q = query.trim()
        failed = false
        if (q.length < 2) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }

        // Lo guardado se enseña sin esperar ni preguntar; solo lo que no está pasa por la red, y
        // con un respiro para no disparar una petición por tecla contra un servidor lento.
        val guardado = cache.lookup(q)
        if (guardado != null) {
            results = guardado
            loading = false
            return@LaunchedEffect
        }

        loading = true
        kotlinx.coroutines.delay(350)
        try {
            val contestado = OpenLibraryClient.api.subjectsAutocomplete(q)
            // La misma clave vuelve con grafías distintas —«Historical fiction» y «Historical
            // Fiction»—, así que se junta por clave y se queda la que más obras dice tener.
            val unicas = contestado
                .filter { !it.subjectKey.isNullOrBlank() && !it.name.isNullOrBlank() }
                .groupBy { it.subjectKey }
                .map { (_, iguales) -> iguales.maxBy { it.workCount } }
                .sortedByDescending { it.workCount }
            results = unicas
            cache.save(q, unicas)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
            results = emptyList()
        } finally {
            loading = false
        }
    }

    val shown = if (query.trim().length < 2) recent else results

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.explore_subject_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.explore_subject_search)) },
            singleLine = true,
            trailingIcon = {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (query.trim().length < 2 && recent.isNotEmpty()) {
            Text(
                text = stringResource(R.string.explore_subject_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        text = when {
                            failed -> stringResource(R.string.explore_source_unavailable)
                            loading -> ""
                            query.trim().length < 2 -> stringResource(R.string.explore_subject_hint)
                            else -> stringResource(R.string.explore_subject_none)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }

            items(shown, key = { it.key ?: it.hashCode().toString() }) { subject ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onPick(subject) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subject.name.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    // El número separa preguntas que se escriben casi igual: «Fiction, historical,
                    // general» son 72.622 obras y «Historical fiction» son 4.655.
                    Text(
                        text = pluralStringResource(
                            R.plurals.explore_results,
                            subject.workCount,
                            subject.workCount
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Un punto de la ruleta. Lleva texto y no icono: «época» y «lugar» no se pueden dibujar. */
@Composable
private fun DialItem(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** El operador entre dos filas. Se toca y va pasando por Y, O y NO. */
@Composable
private fun OperatorChip(op: ExploreQuery.Op, onCycle: () -> Unit) {
    val label = when (op) {
        ExploreQuery.Op.Y -> R.string.explore_op_and
        ExploreQuery.Op.O -> R.string.explore_op_or
        ExploreQuery.Op.NO -> R.string.explore_op_not
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        AssistChip(
            onClick = onCycle,
            label = { Text(stringResource(label)) },
            colors = if (op == ExploreQuery.Op.NO) {
                AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.error
                )
            } else {
                AssistChipDefaults.assistChipColors()
            }
        )
    }
}

/** Una fila de filtro: el nombre del campo, su valor y el aspa para quitarla. */
@Composable
private fun FilterRow(
    row: ExploreQuery.Row,
    appLocale: Locale,
    onChange: (ExploreQuery.Row) -> Unit,
    onPickSubject: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(fieldLabel(row.field)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    // La materia no se teclea. Se enseña su nombre, y tocarla vuelve a abrir el
                    // selector: dejar editar la clave a mano sería dejar escribir una que no
                    // existe, que es justo lo que este campo evita.
                    row.field == ExploreQuery.Field.MATERIA -> Text(
                        text = row.label ?: row.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(onClick = onPickSubject)
                            .padding(vertical = 12.dp)
                    )

                    row.field.kind == ExploreQuery.Kind.TEXTO -> OutlinedTextField(
                        value = row.text,
                        onValueChange = { onChange(row.copy(text = it)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    row.field.kind == ExploreQuery.Kind.CODIGO -> LanguageDropdown(
                        marc = row.text,
                        appLocale = appLocale,
                        onPick = { onChange(row.copy(text = it)) }
                    )

                    row.field.kind == ExploreQuery.Kind.RANGO -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NumberField(
                            value = row.from,
                            label = stringResource(R.string.explore_range_from),
                            modifier = Modifier.weight(1f)
                        ) { onChange(row.copy(from = it)) }
                        NumberField(
                            value = row.to,
                            label = stringResource(R.string.explore_range_to),
                            modifier = Modifier.weight(1f)
                        ) { onChange(row.copy(to = it)) }
                    }

                    else -> NumberField(
                        value = row.from,
                        label = stringResource(R.string.explore_range_min),
                        modifier = Modifier.fillMaxWidth()
                    ) { onChange(row.copy(from = it)) }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.explore_remove_filter)
                )
            }
        }
    }
}

/** Un número, o nada. Vacío es extremo abierto y no cero. */
@Composable
private fun NumberField(
    value: Int?,
    label: String,
    modifier: Modifier = Modifier,
    onChange: (Int?) -> Unit
) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { text ->
            val digits = text.filter { it.isDigit() }.take(6)
            onChange(digits.toIntOrNull())
        },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** El desplegable de idioma, que guarda el código MARC y enseña el nombre traducido. */
@Composable
private fun LanguageDropdown(marc: String, appLocale: Locale, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = OpenLibraryClient.localeTagForMarc(marc)
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(appLocale).replaceFirstChar { c -> c.uppercase() } }
        ?: marc

    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(shown)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            APP_LANGUAGES
                .map { it to Locale.forLanguageTag(it).getDisplayLanguage(appLocale).replaceFirstChar { c -> c.uppercase() } }
                .sortedBy { it.second }
                .forEach { (tag, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            open = false
                            OpenLibraryClient.marcCode(tag)?.let(onPick)
                        }
                    )
                }
        }
    }
}

/** Cómo se ordenan los resultados. No es un filtro y por eso no va entre las filas. */
@Composable
private fun OrdenSelector(
    orden: ExploreQuery.Orden,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onPick: (ExploreQuery.Orden) -> Unit
) {
    Box(modifier = modifier) {
        OutlinedButton(onClick = { onMenuOpen(true) }) {
            Text(stringResource(R.string.explore_sort, stringResource(ordenLabel(orden))))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }) {
            ExploreQuery.Orden.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(ordenLabel(option))) },
                    onClick = {
                        onMenuOpen(false)
                        onPick(option)
                    }
                )
            }
        }
    }
}

private fun fieldLabel(field: ExploreQuery.Field): Int = when (field) {
    ExploreQuery.Field.MATERIA -> R.string.explore_field_subject
    ExploreQuery.Field.TITULO -> R.string.explore_field_title
    ExploreQuery.Field.AUTOR -> R.string.explore_field_author
    ExploreQuery.Field.IDIOMA -> R.string.explore_field_language
    ExploreQuery.Field.ANIO -> R.string.explore_field_year
    ExploreQuery.Field.LUGAR -> R.string.explore_field_place
    ExploreQuery.Field.PERSONA -> R.string.explore_field_person
    ExploreQuery.Field.EPOCA -> R.string.explore_field_period
    ExploreQuery.Field.EDITORIAL -> R.string.explore_field_publisher
    ExploreQuery.Field.PAGINAS -> R.string.explore_field_pages
    ExploreQuery.Field.VALORACIONES -> R.string.explore_field_ratings
}

/**
 * La línea que explica un filtro en la hoja de «más filtros». Null en los que no la necesitan:
 * nadie tiene que explicar qué es un autor.
 */
private fun fieldHelp(field: ExploreQuery.Field): Int? = when (field) {
    ExploreQuery.Field.LUGAR -> R.string.explore_help_place
    ExploreQuery.Field.PERSONA -> R.string.explore_help_person
    ExploreQuery.Field.EPOCA -> R.string.explore_help_period
    ExploreQuery.Field.EDITORIAL -> R.string.explore_help_publisher
    ExploreQuery.Field.PAGINAS -> R.string.explore_help_pages
    ExploreQuery.Field.VALORACIONES -> R.string.explore_help_ratings
    ExploreQuery.Field.MATERIA,
    ExploreQuery.Field.TITULO,
    ExploreQuery.Field.AUTOR,
    ExploreQuery.Field.IDIOMA,
    ExploreQuery.Field.ANIO -> null
}

private fun ordenLabel(orden: ExploreQuery.Orden): Int = when (orden) {
    ExploreQuery.Orden.RELEVANCIA -> R.string.explore_sort_relevance
    ExploreQuery.Orden.LEIDOS -> R.string.explore_sort_read
    ExploreQuery.Orden.VALORACION -> R.string.explore_sort_rating
    ExploreQuery.Orden.NUEVOS -> R.string.explore_sort_new
    ExploreQuery.Orden.EDICIONES -> R.string.explore_sort_editions
    ExploreQuery.Orden.AZAR -> R.string.explore_sort_random
}

/** Una obra en la lista de resultados. */
@Composable
private fun WorkCard(work: OpenLibraryWork, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            work.coverUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.book_cover_desc),
                    modifier = Modifier.width(60.dp).height(90.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = work.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                work.authorNames?.firstOrNull()?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                val year = work.firstPublishYear
                val editions = work.editionCount ?: 0
                Text(
                    text = listOfNotNull(
                        year?.toString(),
                        if (editions > 0) {
                            pluralStringResource(R.plurals.explore_editions_count, editions, editions)
                        } else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * La lista de ediciones, que es donde se decide qué ejemplar acaba en la instancia.
 *
 * Existe porque dejarle elegir a BookWyrm sale mal: pasándole la obra, escoge él con una
 * preferencia por el inglés que no muerde cuando la edición no dice de qué idioma es —y un
 * tercio no lo dicen—, así que trae cualquiera. Y las estanterías guardan ejemplares, de modo
 * que equivocarse deja el mismo libro dos veces.
 */
@Composable
private fun EditionPickerDialog(
    work: OpenLibraryWork,
    preferredLanguage: String?,
    appLocale: Locale,
    api: BookWyrmApi,
    onOpenLocalBook: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editions by remember { mutableStateOf<List<OpenLibraryEdition>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var busyKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(work.workId) {
        val id = work.workId
        if (id == null) {
            loading = false; failed = true
            return@LaunchedEffect
        }
        try {
            val response = OpenLibraryClient.api.editionsOfWork(id, limit = 50, offset = 0)
            // El idioma elegido primero, las demás detrás, y las que no dicen idioma al final:
            // esconderlas no vale, porque la edición buena puede estar entre ellas.
            editions = response.entries.sortedWith(
                compareBy(
                    { edition ->
                        when (edition.languageCode) {
                            preferredLanguage -> 0
                            null -> 2
                            else -> 1
                        }
                    },
                    { it.publishDate ?: "" }
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
        } finally {
            loading = false
        }
    }

    /** Abre la edición en la instancia, importándola solo si allí no está ya. */
    fun choose(edition: OpenLibraryEdition) {
        busyKey = edition.key
        scope.launch {
            try {
                // Primero se pregunta por el ISBN: si la instancia ya tiene ese ejemplar, no hay
                // nada que importar y se evita crear un duplicado de algo que ya existe.
                var localUrl: String? = null
                val isbn = edition.isbn
                if (isbn != null) {
                    val response = runCatching { api.searchByIsbn(isbn) }.getOrNull()
                    if (response != null && response.isSuccessful) {
                        val body = runCatching { response.body()?.string() }.getOrNull()
                        if (!body.isNullOrBlank()) {
                            localUrl = runCatching {
                                org.json.JSONObject(body).optString("id").takeIf { it.isNotBlank() }
                            }.getOrNull()
                        }
                    }
                }

                if (localUrl == null) {
                    val remote = edition.remoteId
                    if (remote != null) {
                        val resolved = BookWyrmScraper.resolveLocalBookUrl(api, remote)
                        // resolveLocalBookUrl devuelve lo que se le dio cuando no consigue
                        // resolverlo, así que hay que mirar que de verdad haya cambiado de casa.
                        if (resolved != null && !resolved.contains("openlibrary.org")) {
                            localUrl = resolved
                        }
                    }
                }

                val url = localUrl
                if (url != null) {
                    onOpenLocalBook(url)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.explore_import_failed),
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
                busyKey = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.explore_editions_title, work.title.orEmpty())) },
        text = {
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }

                failed -> Text(stringResource(R.string.explore_source_unavailable))

                editions.isEmpty() -> Text(stringResource(R.string.explore_editions_none))

                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(editions, key = { it.key ?: it.hashCode().toString() }) { edition ->
                        EditionRow(
                            edition = edition,
                            appLocale = appLocale,
                            busy = busyKey == edition.key,
                            enabled = busyKey == null,
                            onChoose = { choose(edition) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.progress_btn_cancel)) }
        }
    )
}

/** Una edición: idioma, año, editorial e ISBN, que es con lo que se distinguen entre sí. */
@Composable
private fun EditionRow(
    edition: OpenLibraryEdition,
    appLocale: Locale,
    busy: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit
) {
    val languageName = edition.languageCode
        ?.let { OpenLibraryClient.localeTagForMarc(it) }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(appLocale).replaceFirstChar { c -> c.uppercase() } }
        ?: edition.languageCode
        ?: stringResource(R.string.explore_edition_unknown_language)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = languageName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            edition.title?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = listOfNotNull(
                    edition.publishDate,
                    edition.publishers?.firstOrNull(),
                    edition.isbn
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            TextButton(onClick = onChoose, enabled = enabled) {
                Text(stringResource(R.string.explore_editions_pick))
            }
        }
    }
}
