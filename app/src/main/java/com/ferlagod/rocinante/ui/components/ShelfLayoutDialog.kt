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

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.utils.ShelfAlignment
import com.ferlagod.rocinante.utils.ShelfLayout
import com.ferlagod.rocinante.utils.ShelfSection

/** Alto fijo de cada fila: es lo que permite traducir el arrastre a un cambio de posición. */
private val RowHeight = 56.dp

/**
 * Diálogo para ordenar las estanterías de «Mis libros», apagar las que no se usan y elegir si
 * las tarjetas se agrupan arriba o abajo.
 *
 * Es el hermano de [ProfileLayoutDialog] y funciona igual: los cambios no se aplican hasta
 * pulsar Guardar, así que salir del diálogo deja la pantalla como estaba. Reutiliza las
 * cadenas genéricas de aquel —guardar, cancelar, restablecer y la descripción del asa— porque
 * dicen exactamente lo mismo y ya están traducidas a los diecisiete idiomas.
 */
@Composable
fun ShelfLayoutDialog(
    initialLayout: ShelfLayout,
    onDismiss: () -> Unit,
    onSave: (ShelfLayout) -> Unit
) {
    var layout by remember(initialLayout) { mutableStateOf(initialLayout) }

    // Índice de la fila que se está arrastrando y cuánto se ha movido desde su sitio.
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { RowHeight.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.shelf_layout_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.shelf_layout_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                layout.sections.forEachIndexed { index, section ->
                    // La fila se identifica por su estantería y no por su posición: sin esta
                    // clave Compose reutiliza la fila al reordenar y reinicia el detector de
                    // gestos, con lo que el arrastre se pierde al pasar sobre la vecina.
                    key(section) {
                        val isDragging = draggingIndex == index
                        ShelfRow(
                            section = section,
                            enabled = layout.isVisible(section),
                            isDragging = isDragging,
                            dragOffset = if (isDragging) dragOffset else 0f,
                            onToggle = { layout = layout.toggled(section) },
                            onDragStart = {
                                draggingIndex = layout.sections.indexOf(section)
                                dragOffset = 0f
                            },
                            onDrag = { delta ->
                                // Se parte de la posición actual de ESTA estantería, no del
                                // índice capturado al componer, que se queda obsoleto en
                                // cuanto la lista se reordena.
                                val from = layout.sections.indexOf(section)
                                dragOffset += delta
                                // Al superar una fila entera se intercambia con la vecina y se
                                // descuenta esa altura, así el dedo sigue sobre la fila.
                                val steps = (dragOffset / rowHeightPx).toInt()
                                if (steps != 0) {
                                    val to = (from + steps).coerceIn(0, layout.sections.lastIndex)
                                    if (to != from) {
                                        val reordered = layout.sections.toMutableList()
                                        reordered.add(to, reordered.removeAt(from))
                                        layout = layout.copy(sections = reordered)
                                        dragOffset -= (to - from) * rowHeightPx
                                        draggingIndex = to
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.shelf_layout_align),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShelfAlignment.entries.forEach { alignment ->
                        FilterChip(
                            selected = layout.alignment == alignment,
                            onClick = { layout = layout.withAlignment(alignment) },
                            label = { Text(alignmentLabel(alignment)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(layout) }) {
                Text(stringResource(R.string.profile_edit_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { layout = ShelfLayout.DEFAULT }) {
                    Text(stringResource(R.string.profile_layout_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.profile_edit_cancel))
                }
            }
        }
    )
}

@Composable
private fun ShelfRow(
    section: ShelfSection,
    enabled: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    onToggle: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val label = shelfLabel(section)
    val dragDescription = stringResource(R.string.profile_layout_drag_desc, label)
    val alwaysDescription = stringResource(R.string.shelf_layout_always, label)

    Surface(
        // Sin color propio: la fila hereda el fondo del diálogo. Solo la que se arrastra se
        // tiñe, como señal de que está en movimiento.
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffset }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = dragDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = dragDescription }
                    .pointerInput(section) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            }
                        )
                    }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
            // «Leyendo» y «Leídos» no se pueden quitar: el interruptor se enseña encendido y
            // apagado a la vez —imposible de mover— en lugar de dejar un hueco, para que se
            // vea que la fila también tiene ese ajuste y que ahí está decidido.
            Switch(
                checked = enabled,
                onCheckedChange = if (section.canHide) ({ onToggle() }) else null,
                enabled = section.canHide,
                modifier = if (section.canHide) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = alwaysDescription }
                }
            )
        }
    }
}

/**
 * Nombre de cada estantería, el mismo que se lee en su tarjeta, para que la lista del diálogo
 * se corresponda con lo que hay en pantalla.
 */
@Composable
private fun shelfLabel(section: ShelfSection): String = when (section) {
    ShelfSection.STOPPED_READING -> stringResource(R.string.shelf_stopped_title)
    ShelfSection.TO_READ -> stringResource(R.string.shelf_to_read_title)
    ShelfSection.READING -> stringResource(R.string.shelf_reading_title)
    ShelfSection.READ -> stringResource(R.string.shelf_read_title)
}

@Composable
private fun alignmentLabel(alignment: ShelfAlignment): String = when (alignment) {
    ShelfAlignment.TOP -> stringResource(R.string.shelf_layout_align_top)
    ShelfAlignment.BOTTOM -> stringResource(R.string.shelf_layout_align_bottom)
}
