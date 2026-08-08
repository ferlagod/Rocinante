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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import com.ferlagod.rocinante.data.model.ShelfBookItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** El mismo ámbar que [RatingStars], para que las estrellas se vean iguales en todas partes. */
private val StarColor = Color(0xFFF5A623)

/**
 * Los libros leídos que no llevan estrellas, con las cinco a mano para ponérselas.
 *
 * **Puntuar publica.** En BookWyrm una valoración es una publicación como cualquier otra, así
 * que ponerle estrellas a veinte libros viejos manda veinte cosas a quien te siga. Por eso la
 * visibilidad se elige aquí arriba y viene puesta en privado: lo normal al rellenar huecos es
 * querer arreglar las cifras propias, no contárselo a nadie. Quien quiera lo contrario lo
 * cambia una vez y vale para todas.
 *
 * @param books Libros leídos sin valoración.
 * @param onSaved Se llama con lo puntuado, para que quien abrió el diálogo lo apunte y el
 *   aviso del perfil deje de contarlo.
 */
@Composable
fun MissingRatingsDialog(
    books: List<ShelfBookItem>,
    api: BookWyrmApi,
    context: Context,
    coroutineScope: CoroutineScope,
    onSaved: (bookId: String, rating: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val chosen = remember { mutableStateMapOf<String, Double>() }
    val saving = remember { mutableStateMapOf<String, Boolean>() }
    val done = remember { mutableStateMapOf<String, Boolean>() }
    // Privado por defecto: rellenar huecos no es lo mismo que reseñar.
    var privacy by remember { mutableStateOf("direct") }
    var privacyOpen by remember { mutableStateOf(false) }

    val privacyOptions = listOf(
        "direct" to stringResource(R.string.progress_privacy_private),
        "followers" to stringResource(R.string.progress_privacy_followers),
        "public" to stringResource(R.string.progress_privacy_public)
    )

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun save(book: ShelfBookItem) {
        val id = book.id ?: return
        val rating = chosen[id] ?: return
        if (saving[id] == true || done[id] == true) return
        coroutineScope.launch {
            saving[id] = true
            try {
                // Del mismo sitio salen el id de la edición y el del usuario que pide este
                // formulario; se lee la página del libro una vez, igual que para las fechas.
                val ctx = BookWyrmScraper.getReadDatesContext(api, id)
                if (ctx == null) {
                    toast(context.getString(R.string.missing_ratings_failed, book.title.orEmpty()))
                    return@launch
                }
                val response = api.postReviewRating(
                    book = ctx.bookId,
                    user = ctx.userId,
                    // Entero cuando lo es: BookWyrm acepta «4» y «4.5», pero no «4.0».
                    rating = if (rating % 1.0 == 0.0) rating.toInt().toString() else rating.toString(),
                    privacy = privacy
                )
                // Igual que al reseñar: un 200 puede ser el formulario devuelto con errores, y
                // una redirección al login significa que la sesión se ha caído.
                val body = if (response.isSuccessful) response.body()?.string().orEmpty() else ""
                val ok = when {
                    response.isSuccessful ->
                        !body.contains("class=\"errorlist\"") && !body.contains("error_1_id_")
                    response.code() == 302 ->
                        response.headers()["Location"]?.contains("/login") != true
                    else -> false
                }
                if (ok) {
                    done[id] = true
                    onSaved(id, rating)
                } else {
                    toast(context.getString(R.string.missing_ratings_failed, book.title.orEmpty()))
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
            Text(pluralStringResource(R.plurals.missing_ratings_title, pending.size, pending.size))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.missing_ratings_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // La visibilidad vale para todo el diálogo: se elige una vez y no libro a libro.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.missing_ratings_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { privacyOpen = true }) {
                        Text(privacyOptions.first { it.first == privacy }.second)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = privacyOpen, onDismissRequest = { privacyOpen = false }) {
                        privacyOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { privacy = value; privacyOpen = false }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
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
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val value = chosen[id] ?: 0.0
                                for (star in 1..5) {
                                    val icon = when {
                                        value >= star -> Icons.Filled.Star
                                        value >= star - 0.5 -> Icons.AutoMirrored.Filled.StarHalf
                                        else -> Icons.Filled.StarBorder
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isDone) StarColor.copy(alpha = 0.5f) else StarColor,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .padding(end = 2.dp)
                                            .then(
                                                if (!busy && !isDone) {
                                                    Modifier.clickable {
                                                        // Tocar la misma estrella otra vez la
                                                        // parte por la mitad: BookWyrm admite
                                                        // medias y el gráfico del perfil las
                                                        // enseña, así que tienen que caber.
                                                        chosen[id] = if (value == star.toDouble()) {
                                                            star - 0.5
                                                        } else {
                                                            star.toDouble()
                                                        }
                                                    }
                                                } else Modifier
                                            )
                                    )
                                }
                                if (busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp).padding(start = 8.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    TextButton(
                                        onClick = { save(book) },
                                        enabled = !isDone && chosen[id] != null
                                    ) {
                                        Text(stringResource(R.string.missing_ratings_save))
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
}
