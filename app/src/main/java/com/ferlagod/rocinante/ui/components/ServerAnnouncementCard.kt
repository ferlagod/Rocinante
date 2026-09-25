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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.model.AnnouncementDisplayType
import com.ferlagod.rocinante.data.model.ServerAnnouncementUiItem

/**
 * Tarjeta para mostrar un aviso o anuncio global del servidor emitido por el administrador de la instancia.
 * Permite expandir el cuerpo del mensaje y descartarlo de forma permanente en las preferencias locales.
 */
@Composable
fun ServerAnnouncementCard(
    announcement: ServerAnnouncementUiItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onUserClicked: ((String) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetails = !announcement.contentText.isNullOrBlank() || !announcement.contentHtml.isNullOrBlank()

    val (containerColor, contentColor) = when (announcement.displayType) {
        AnnouncementDisplayType.DANGER -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        AnnouncementDisplayType.WARNING -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        AnnouncementDisplayType.SUCCESS -> Pair(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        AnnouncementDisplayType.PRIMARY, AnnouncementDisplayType.INFO -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    val iconVector = when (announcement.displayType) {
        AnnouncementDisplayType.DANGER, AnnouncementDisplayType.WARNING -> Icons.Default.Warning
        AnnouncementDisplayType.SUCCESS -> Icons.Default.CheckCircle
        AnnouncementDisplayType.PRIMARY, AnnouncementDisplayType.INFO -> Icons.Default.Campaign
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = stringResource(R.string.server_announcement_title),
                        modifier = Modifier.size(22.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.server_announcement_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    if (!announcement.eventDate.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = contentColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = announcement.eventDate,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.server_announcement_dismiss),
                        modifier = Modifier.size(18.dp),
                        tint = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Titular o preview del aviso
            Text(
                text = announcement.preview,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )

            // Contenido expandible
            if (hasDetails && expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                val detailText = remember(announcement) {
                    when {
                        !announcement.contentHtml.isNullOrBlank() ->
                            HtmlCompat.fromHtml(announcement.contentHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                        else -> announcement.contentText.orEmpty().trim()
                    }
                }
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Pie: autor y botón de expandir si hay detalles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!announcement.postedByName.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.server_announcement_posted_by, announcement.postedByName),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                        modifier = Modifier
                            .then(
                                if (onUserClicked != null && !announcement.postedByUrl.isNullOrBlank()) {
                                    Modifier.clickable { onUserClicked(announcement.postedByUrl) }
                                } else {
                                    Modifier
                                }
                            )
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (hasDetails) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = if (expanded) stringResource(R.string.server_announcement_show_less)
                            else stringResource(R.string.server_announcement_show_more),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor
                        )
                    }
                }
            }
        }
    }
}
