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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.model.ShelfBookItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Los libros a los que la instancia no les pone un dato —el idioma, el formato—, con lo
 * necesario para escribirlo sin salir de aquí.
 *
 * Las opciones no vienen escritas en la aplicación: se leen del formulario de edición de la
 * propia instancia, que es quien decide qué formatos admite y cómo se llaman. Si el campo no
 * es una lista, se escribe a mano.
 *
 * Ojo con lo que se escribe: **el dato es del libro, no de quien lo lee**, así que esto cambia
 * la ficha para toda la instancia, igual que editarla desde la web. Por eso se guarda de uno en
 * uno, con lo elegido a la vista, y no de golpe.
 *
 * @param field nombre del campo en el formulario ("languages", "physical_format"…).
 * @param onSaved se llama con el libro y lo guardado, para que quien abrió el diálogo lo
 *   apunte y las cifras del perfil dejen de decir que falta.
 */
@Composable
fun MissingBookFieldDialog(
    books: List<ShelfBookItem>,
    field: String,
    title: String,
    explanation: String,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onSaved: (bookId: String, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    val entered = remember { mutableStateMapOf<String, String>() }
    val saving = remember { mutableStateMapOf<String, Boolean>() }
    val saved = remember { mutableStateMapOf<String, Boolean>() }

    // Lo que la instancia admite en este campo. Se pregunta una sola vez, por el primer libro
    // de la lista: el formulario es el mismo para todos.
    var options by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loadingOptions by remember { mutableStateOf(true) }
    LaunchedEffect(field, books.firstOrNull()?.id) {
        val sample = books.firstOrNull()?.id
        options = if (sample == null) {
            emptyList()
        } else {
            runCatching { BookWyrmScraper.getBookFieldOptions(api, sample, field) }
                .getOrDefault(emptyList())
        }
        loadingOptions = false
    }

    fun save(book: ShelfBookItem) {
        val id = book.id ?: return
        val value = entered[id]?.trim().orEmpty()
        if (value.isEmpty() || saving[id] == true) return
        coroutineScope.launch {
            saving[id] = true
            val ok = runCatching { BookWyrmScraper.setBookField(api, id, field, value) }
                .getOrDefault(false)
            saving[id] = false
            if (ok) {
                saved[id] = true
                onSaved(id, value)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.missing_field_failed, book.title.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val pending = books.filter { it.id != null && saved[it.id] != true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (loadingOptions) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
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
                            if (options.isEmpty()) {
                                OutlinedTextField(
                                    value = entered[id].orEmpty(),
                                    onValueChange = { entered[id] = it.take(60) },
                                    singleLine = true,
                                    modifier = Modifier.width(140.dp)
                                )
                            } else {
                                var expanded by remember { mutableStateOf(false) }
                                val chosen = entered[id]
                                OutlinedButton(onClick = { expanded = true }) {
                                    Text(
                                        text = options.firstOrNull { it.first == chosen }?.second
                                            ?: stringResource(R.string.missing_field_choose),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    options.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                entered[id] = value
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            if (saving[id] == true) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { save(book) },
                                    enabled = !entered[id].isNullOrBlank()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(
                                            R.string.missing_pages_save
                                        )
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
