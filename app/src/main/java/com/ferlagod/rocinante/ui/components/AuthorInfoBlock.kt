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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.model.BookWyrmAuthor
import com.ferlagod.rocinante.utils.HtmlUtils

/**
 * La ficha de quien escribe: su biografía, sus fechas y los sitios donde hay más.
 *
 * Sale en la pestaña «Diverse» del libro y al final de sus libros dentro de la estantería. Es la
 * misma en los dos sitios a propósito: son los mismos datos, y con dos copias una acabaría
 * enseñando algo que la otra no.
 *
 * @param author lo que devuelve su ficha en la instancia.
 * @param openLink qué hacer al pulsar uno de los sitios; lo pone quien la usa, para respetar el
 *   ajuste de abrir enlaces que ya tiene la aplicación.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorInfoBlock(author: BookWyrmAuthor, openLink: (String) -> Unit) {
    val authorName = author.name?.trim()?.takeIf { it.isNotEmpty() } ?: return
    BookInfoSection(authorName) {
        author.bio?.let { HtmlUtils.stripHtml(it) }
            ?.trim()?.takeIf { it.isNotEmpty() }?.let { bio ->
                Text(text = bio, style = MaterialTheme.typography.bodyMedium)
            }
        formatDetailDate(author.born)?.let {
            BookInfoRow(stringResource(R.string.author_born), it)
        }
        formatDetailDate(author.died)?.let {
            BookInfoRow(stringResource(R.string.author_died), it)
        }
        // Los sitios del autor, como botones y no como filas de texto: un botón se ve que se
        // puede pulsar, y aquí hay cuatro sitios distintos donde una lista de direcciones sería
        // un muro. Material no trae logotipos, así que el icono dice de qué clase de sitio se
        // trata y el texto dice cuál: dibujar algo parecido a una W de Wikipedia sería fingir
        // una marca que no tenemos.
        val links = buildList {
            author.website?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(Triple(stringResource(R.string.author_website), it, Icons.Default.Language))
            }
            author.wikipediaLink?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(Triple(stringResource(R.string.author_wikipedia), it, Icons.Default.Article))
            }
            // Wikidata y Open Library no vienen como direcciones sino como claves; la dirección
            // se arma con ellas.
            author.wikidata?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(Triple("Wikidata", "https://www.wikidata.org/wiki/$it", Icons.Default.DataObject))
            }
            author.openlibraryKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
                add(
                    Triple(
                        "Open Library",
                        "https://openlibrary.org/authors/$it",
                        Icons.AutoMirrored.Filled.LibraryBooks
                    )
                )
            }
        }
        if (links.isNotEmpty()) {
            // Centrados: con un solo botón, pegado a la izquierda, parecía que faltaba algo al
            // lado. Así uno queda en medio, dos quedan en medio juntos, y una fila que se dobla
            // reparte la última línea igual que las de arriba.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                links.forEach { (label, url, icon) ->
                    OutlinedButton(onClick = { openLink(url) }) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
