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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.model.BookEnrichment
import com.ferlagod.rocinante.data.model.ShelfBookItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Los libros leídos a los que les falta alguna fecha de lectura, con los dos huecos para
 * ponerlas sin ir libro por libro.
 *
 * Sin fecha de fin el libro no entra en «libros por año», y sin las dos no cuenta en los días
 * de lectura. Hasta ahora corregirlo era abrir la ficha de cada uno, y son justo los libros
 * viejos, que suelen ser muchos.
 *
 * A diferencia de las páginas, **las fechas son de quien lee y no del libro**: esto solo toca
 * la lectura propia, no la ficha que ve el resto de la instancia.
 *
 * Se guarda de uno en uno y no todo de golpe: cada libro cuesta una petición para saber si ya
 * tiene una lectura dada de alta —hay que corregir esa y no crear otra al lado—, y así se ve
 * cuál ha entrado y cuál no en vez de un botón que o todo o nada.
 *
 * @param books Libros de la estantería a los que les falta alguna fecha.
 * @param enrichment Lo que ya se sabe de cada uno, para partir de la fecha que sí esté puesta.
 * @param onSaved Se llama con lo guardado, para que quien abrió el diálogo lo apunte y las
 *   cifras del perfil dejen de decir que faltan.
 */
@Composable
fun MissingReadDatesDialog(
    books: List<ShelfBookItem>,
    enrichment: Map<String, BookEnrichment>,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onSaved: (bookId: String, startIso: String?, finishIso: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // Lo elegido y lo que está en marcha, por libro. Se parte de lo que ya conste, para que
    // poner la fecha que falta no borre la que había.
    val started = remember {
        mutableStateMapOf<String, String>().apply {
            books.forEach { b -> b.id?.let { id -> enrichment[id]?.started?.let { put(id, it) } } }
        }
    }
    val finished = remember {
        mutableStateMapOf<String, String>().apply {
            books.forEach { b -> b.id?.let { id -> enrichment[id]?.finished?.let { put(id, it) } } }
        }
    }
    val saving = remember { mutableStateMapOf<String, Boolean>() }
    // Los ya guardados se quedan en la lista, pero apagados: quitarlos de debajo del dedo haría
    // saltar la fila siguiente al hueco que se acaba de dejar.
    val done = remember { mutableStateMapOf<String, Boolean>() }
    var picking by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun save(book: ShelfBookItem) {
        val id = book.id ?: return
        val start = started[id]
        val finish = finished[id]
        if (start == null && finish == null) return
        if (start != null && finish != null && start > finish) {
            toast(context.getString(R.string.book_read_dates_order))
            return
        }
        if (saving[id] == true) return
        coroutineScope.launch {
            saving[id] = true
            try {
                val ctx = BookWyrmScraper.getReadDatesContext(api, id)
                if (ctx == null) {
                    toast(context.getString(R.string.missing_dates_failed, book.title.orEmpty()))
                    return@launch
                }
                // Si ya hay una lectura dada de alta se corrige esa —la más reciente, que es la
                // que le falta el dato—; si no hay ninguna, se crea.
                val latest = ctx.readthroughs.maxByOrNull { it.finishDate ?: it.startDate ?: "" }
                val response = if (latest != null) {
                    api.editReadthrough(
                        readthroughId = latest.id,
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
                // Al corregir, BookWyrm contesta 200 vacío o redirige. Al crear, un 200 es el
                // formulario devuelto con errores: ahí solo vale la redirección.
                val ok = response.code() == 302 || (latest != null && response.isSuccessful)
                if (ok) {
                    done[id] = true
                    onSaved(id, start, finish)
                } else {
                    toast(com.ferlagod.rocinante.utils.NetworkErrors.message(context, response.code()))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                toast(com.ferlagod.rocinante.utils.NetworkErrors.message(context, e))
            } finally {
                saving[id] = false
            }
        }
    }

    val pending = books.filter { it.id != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(pluralStringResource(R.plurals.missing_dates_title, pending.size, pending.size))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.missing_dates_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(pending, key = { it.id ?: it.title.orEmpty() }) { book ->
                        val id = book.id.orEmpty()
                        val isDone = done[id] == true
                        val busy = saving[id] == true
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = book.title.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { picking = id to true },
                                    enabled = !busy && !isDone,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = formatShortDate(started[id])
                                            ?: stringResource(R.string.book_label_started),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = { picking = id to false },
                                    enabled = !busy && !isDone,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = formatShortDate(finished[id])
                                            ?: stringResource(R.string.book_label_finished),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                when {
                                    busy -> CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    else -> IconButton(
                                        onClick = { save(book) },
                                        enabled = !isDone &&
                                            (started[id] != null || finished[id] != null)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = stringResource(
                                                R.string.missing_dates_save
                                            ),
                                            tint = if (isDone) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
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

    picking?.let { (id, isStart) ->
        ReadDatePickerDialog(
            initialIso = if (isStart) started[id] else finished[id],
            onDismiss = { picking = null },
            onPick = { iso ->
                if (isStart) started[id] = iso else finished[id] = iso
                picking = null
            }
        )
    }
}

/**
 * La fecha en el formato corto del idioma del teléfono: los botones son estrechos y el formato
 * medio («1. jan. 2025») no cabe en dos de ellos uno al lado del otro.
 */
private fun formatShortDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val parsed = parser.parse(iso) ?: return iso
        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(parsed)
    } catch (e: Exception) {
        iso
    }
}
