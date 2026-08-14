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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.BookWyrmApi
import com.ferlagod.rocinante.data.api.BookWyrmScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Crear una estantería propia: nombre, descripción y quién puede verla.
 *
 * Son los tres campos del formulario de la web, ni más ni menos. La descripción se manda aunque
 * la aplicación no pueda enseñarla todavía —la instancia no la devuelve en su JSON—, porque en
 * la web sí se ve y sería raro que una estantería hecha desde aquí no pudiera tener una.
 *
 * @param onCreated Se llama con el identificador de la estantería recién hecha.
 */
@Composable
fun CreateShelfDialog(
    api: BookWyrmApi,
    instanceUrl: String,
    username: String,
    context: Context,
    coroutineScope: CoroutineScope,
    onCreated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // Como en la web: pública de partida. Quien quiera guardársela lo dice aquí.
    var privacy by remember { mutableStateOf("public") }
    var privacyOpen by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val privacyOptions = listOf(
        "public" to stringResource(R.string.progress_privacy_public),
        "followers" to stringResource(R.string.progress_privacy_followers),
        "direct" to stringResource(R.string.progress_privacy_private)
    )

    fun save() {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || isSaving) return
        coroutineScope.launch {
            isSaving = true
            try {
                val ctx = BookWyrmScraper.getShelfCreateContext(api, instanceUrl, username)
                if (ctx == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.shelf_create_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                val response = api.createShelf(
                    name = cleanName,
                    description = description.trim(),
                    privacy = privacy,
                    user = ctx.userId,
                    csrfToken = ctx.csrfToken
                )
                // Creada, BookWyrm redirige a la estantería nueva y su dirección dice cómo se
                // llama. Un 200 es el formulario de vuelta porque algo no le valía.
                val identifier = response.headers()["Location"]
                    ?.trimEnd('/')
                    ?.substringAfterLast("/books/")
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotEmpty() }
                if (response.code() == 302 && identifier != null) {
                    onCreated(identifier)
                    onDismiss()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.shelf_create_failed),
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
                isSaving = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.shelf_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    label = { Text(stringResource(R.string.shelf_create_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 500) description = it },
                    label = { Text(stringResource(R.string.shelf_create_description)) },
                    enabled = !isSaving,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.shelf_create_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { privacyOpen = true }, enabled = !isSaving) {
                        Text(privacyOptions.first { it.first == privacy }.second)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = privacyOpen,
                        onDismissRequest = { privacyOpen = false }
                    ) {
                        privacyOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { privacy = value; privacyOpen = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { save() }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.progress_btn_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.progress_btn_cancel))
            }
        }
    )
}
