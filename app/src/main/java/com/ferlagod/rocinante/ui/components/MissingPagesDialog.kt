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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.model.ShelfBookItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Los libros leídos a los que la instancia no les pone número de páginas, con un hueco para
 * escribirlo sin salir de aquí.
 *
 * Esos libros no suman en las páginas totales del perfil, y hasta ahora la única salida era
 * ir a la web y editar la ficha de cada uno.
 *
 * Ojo con lo que se escribe: **las páginas son del libro, no de quien lo lee**, así que esto
 * cambia la ficha para toda la instancia, igual que editarla desde la web. Por eso se guarda
 * de uno en uno, con lo que se ha escrito a la vista, y no de golpe.
 *
 * @param books Libros sin páginas, tal y como los da la estantería.
 * @param onSaved Se llama con el libro y las páginas guardadas, para que quien abrió el
 *   diálogo lo apunte y las cifras del perfil dejen de decir que faltan.
 */
@Composable
fun MissingPagesDialog(
    books: List<ShelfBookItem>,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onSaved: (bookId: String, pages: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Lo escrito y lo que está en marcha, por libro.
    val entered = remember { mutableStateMapOf<String, String>() }
    val saving = remember { mutableStateMapOf<String, Boolean>() }
    var savedCount by remember { mutableStateOf(0) }

    fun save(book: ShelfBookItem) {
        val id = book.id ?: return
        val pages = entered[id]?.trim()?.toIntOrNull() ?: return
        if (pages <= 0 || saving[id] == true) return
        coroutineScope.launch {
            saving[id] = true
            val ok = runCatching { BookWyrmScraper.setBookPages(api, id, pages) }.getOrDefault(false)
            saving[id] = false
            if (ok) {
                savedCount++
                entered.remove(id)
                onSaved(id, pages)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.missing_pages_failed, book.title.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Los ya guardados salen de la lista; cuando no queda ninguno, el diálogo ya no pinta nada.
    val pending = books.filter { it.id != null && (it.pages ?: 0) <= 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.missing_pages_title, pending.size)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.missing_pages_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pending, key = { it.id ?: it.title.orEmpty() }) { book ->
                        val id = book.id.orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = book.title.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = entered[id].orEmpty(),
                                onValueChange = { new ->
                                    entered[id] = new.filter { it.isDigit() }.take(5)
                                },
                                singleLine = true,
                                label = { Text(stringResource(R.string.book_label_pages)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(110.dp)
                            )
                            if (saving[id] == true) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { save(book) },
                                    enabled = (entered[id]?.toIntOrNull() ?: 0) > 0
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.missing_pages_save)
                                    )
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
}
