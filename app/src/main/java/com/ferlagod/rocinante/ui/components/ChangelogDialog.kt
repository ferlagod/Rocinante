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

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ferlagod.rocinante.R

/**
 * Las novedades de la versión que se está ejecutando.
 *
 * Al publicar una versión nueva hay que tocar las dos cosas a la vez: el número y el texto.
 * Estaban en sitios distintos —el número dentro de `MainActivity` y el texto elegido allí
 * mismo—, y ahora que el aviso se enseña también desde las notificaciones conviene que haya
 * un solo sitio donde cambiarlo.
 */
object Changelog {
    /** Versión a la que corresponde el texto de abajo. */
    const val CURRENT_VERSION = "1.2.4"

    /** Texto de las novedades de [CURRENT_VERSION]. */
    val textRes = R.string.changelog_text_v1_2_4
}

/**
 * Diálogo con las novedades de la versión.
 *
 * Se abre solo al actualizar la app, y a mano desde el aviso de las notificaciones para quien
 * quiera volver a leerlas.
 *
 * @param onDismiss Se llama tanto al aceptar como al cerrar por fuera: en ambos casos las
 *   novedades se dan por leídas.
 */
@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.changelog_title, Changelog.CURRENT_VERSION),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            // Las novedades de una versión no caben en la pantalla de un móvil, y sin poder
            // desplazarlas la mitad de la lista no se llega a leer.
            Text(
                text = stringResource(Changelog.textRes),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}
