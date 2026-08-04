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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.model.ShelfBookItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Los libros leídos que no tienen autor en la instancia, con un hueco para escribirlo.
 *
 * Sin autor no cuentan en «Autores más leídos», y hasta ahora solo se podían arreglar desde
 * la web. Igual que con las páginas, esto cambia la ficha del libro para toda la instancia.
 *
 * Un autor no es un campo del libro sino una ficha aparte, así que la instancia pregunta
 * primero si se refiere a alguno de los que ya tiene. Esa pregunta se traslada tal cual en vez
 * de contestarla por cuenta propia: elegir bien es lo que evita acabar con la misma persona
 * dada de alta dos veces, que es algo que esta instancia ya arrastra.
 *
 * @param onSaved Se llama con el libro y el nombre guardado, para que el perfil deje de
 *   contarlo entre los que faltan.
 */
@Composable
fun MissingAuthorsDialog(
    books: List<ShelfBookItem>,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onSaved: (bookId: String, authorName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val entered = remember { mutableStateMapOf<String, String>() }
    val working = remember { mutableStateMapOf<String, Boolean>() }

    // La pregunta de la instancia, mientras esté sin contestar.
    var choiceFor by remember { mutableStateOf<ShelfBookItem?>(null) }
    var choiceName by remember { mutableStateOf("") }
    var choiceForm by remember { mutableStateOf<BookWyrmScraper.BookEditForm?>(null) }
    var choiceOptions by remember { mutableStateOf<List<BookWyrmScraper.AuthorOption>>(emptyList()) }

    fun failed(book: ShelfBookItem) {
        Toast.makeText(
            context,
            context.getString(R.string.missing_authors_failed, book.title.orEmpty()),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun propose(book: ShelfBookItem) {
        val id = book.id ?: return
        val name = entered[id]?.trim().orEmpty()
        if (name.isEmpty() || working[id] == true) return
        coroutineScope.launch {
            working[id] = true
            val result = runCatching { BookWyrmScraper.proposeBookAuthor(api, id, name) }
                .getOrDefault(BookWyrmScraper.AddAuthorResult.Failed)
            working[id] = false
            when (result) {
                is BookWyrmScraper.AddAuthorResult.Saved -> {
                    entered.remove(id)
                    onSaved(id, name)
                }
                is BookWyrmScraper.AddAuthorResult.NeedsChoice -> {
                    choiceFor = book
                    choiceName = name
                    choiceForm = result.form
                    choiceOptions = result.options
                }
                is BookWyrmScraper.AddAuthorResult.Failed -> failed(book)
            }
        }
    }

    fun confirm(option: BookWyrmScraper.AuthorOption) {
        val book = choiceFor ?: return
        val form = choiceForm ?: return
        val id = book.id ?: return
        coroutineScope.launch {
            working[id] = true
            choiceFor = null
            val ok = runCatching { BookWyrmScraper.confirmBookAuthor(api, form, option) }
                .getOrDefault(false)
            working[id] = false
            if (ok) {
                entered.remove(id)
                // El nombre que se enseña es el de la ficha elegida, no lo que se escribió.
                onSaved(
                    id,
                    if (option.kind == BookWyrmScraper.AuthorOption.Kind.NEW) choiceName
                    else option.label
                )
            } else {
                failed(book)
            }
        }
    }

    val pending = books.filter { it.id != null }

    if (choiceFor != null) {
        AlertDialog(
            onDismissRequest = { choiceFor = null },
            title = { Text(stringResource(R.string.missing_authors_choice_title, choiceName)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    choiceOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirm(option) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = false, onClick = { confirm(option) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (option.kind) {
                                        BookWyrmScraper.AuthorOption.Kind.NEW ->
                                            stringResource(R.string.missing_authors_choice_new, choiceName)
                                        else -> option.label
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                // Lo que distingue a dos autores con el mismo nombre.
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
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choiceFor = null }) {
                    Text(stringResource(R.string.progress_btn_cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.missing_authors_title, pending.size, pending.size)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.missing_authors_explanation),
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
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = book.title.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = entered[id].orEmpty(),
                                    onValueChange = { entered[id] = it },
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.book_label_author)) },
                                    modifier = Modifier.weight(1f)
                                )
                                if (working[id] == true) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = { propose(book) },
                                        enabled = entered[id]?.isNotBlank() == true
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.book_close))
            }
        }
    )
}
