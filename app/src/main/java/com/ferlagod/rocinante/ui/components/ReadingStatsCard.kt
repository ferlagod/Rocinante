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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.utils.ReadingStats
import java.text.NumberFormat

/**
 * Tarjeta de estadísticas de lectura del perfil: tres cifras destacadas (total de libros,
 * libros de este año y páginas acumuladas) y un gráfico de barras de libros por año.
 *
 * Todo procede de datos ya cacheados, así que la tarjeta no dispara ninguna petición.
 * Si la estantería "Leídos" aún no se ha abierto nunca, quien la usa no debe mostrarla.
 */
@Composable
fun ReadingStatsCard(
    stats: ReadingStats,
    currentYear: Int,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell(
                value = numberFormat.format(stats.totalBooks),
                label = stringResource(R.string.profile_stats_books),
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(modifier = Modifier.height(36.dp))
            StatCell(
                value = numberFormat.format(stats.booksThisYear),
                label = stringResource(R.string.profile_stats_this_year),
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(modifier = Modifier.height(36.dp))
            StatCell(
                value = numberFormat.format(stats.totalPages),
                label = stringResource(R.string.profile_stats_pages),
                modifier = Modifier.weight(1f)
            )
        }

        if (stats.hasChartData) {
            HorizontalDivider()
            BooksPerYearChart(
                data = stats.booksPerYear,
                currentYear = currentYear,
                numberFormat = numberFormat,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // Los datos que faltan se dicen, no se disimulan: si no, las cifras aparentan ser
        // totales cuando en realidad solo suman los libros que traían el dato.
        val caveats = buildList {
            if (stats.booksWithoutFinishDate > 0) {
                add(
                    pluralStringResource(
                        R.plurals.profile_stats_missing_dates,
                        stats.booksWithoutFinishDate,
                        stats.booksWithoutFinishDate
                    )
                )
            }
            if (stats.booksWithoutPages > 0) {
                add(
                    pluralStringResource(
                        R.plurals.profile_stats_missing_pages,
                        stats.booksWithoutPages,
                        stats.booksWithoutPages
                    )
                )
            }
        }
        if (caveats.isNotEmpty()) {
            Text(
                text = caveats.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Gráfico de barras de una sola serie: no lleva leyenda (el título la nombra) y solo se
 * etiquetan el año en curso y el año con más lecturas, para no repetir una cifra sobre
 * cada barra. Los años sin lecturas ocupan su hueco con una barra vacía.
 */
@Composable
private fun BooksPerYearChart(
    data: List<ReadingStats.YearCount>,
    currentYear: Int,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val axisColor = MaterialTheme.colorScheme.outlineVariant

    val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
    val peakYear = data.maxByOrNull { it.count }?.year
    // Con muchos años no caben todas las etiquetas: se muestra una de cada N y siempre la última.
    val labelStep = ((data.size + 7) / 8).coerceAtLeast(1)

    val chartDescription = stringResource(
        R.string.profile_stats_chart_desc,
        data.joinToString(", ") { "${it.year}: ${it.count}" }
    )

    Column(modifier = modifier.semantics { contentDescription = chartDescription }) {
        Text(
            text = stringResource(R.string.profile_stats_per_year),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            data.forEach { entry ->
                val showValue = entry.count > 0 && (entry.year == currentYear || entry.year == peakYear)
                Text(
                    text = if (showValue) numberFormat.format(entry.count) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            val slotWidth = size.width / data.size
            val gap = 2.dp.toPx()
            val barWidth = (slotWidth - gap).coerceAtLeast(1f)
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            val minVisibleHeight = 3.dp.toPx()

            data.forEachIndexed { index, entry ->
                if (entry.count <= 0) return@forEachIndexed
                val barHeight = (size.height * entry.count / maxCount).coerceAtLeast(minVisibleHeight)
                val left = index * slotWidth + gap / 2f
                // Solo se redondea el extremo del dato; el pie queda anclado al eje.
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                offset = Offset(left, size.height - barHeight),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                            ),
                            topLeft = radius,
                            topRight = radius,
                            bottomRight = CornerRadius.Zero,
                            bottomLeft = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, color = if (entry.year == currentYear) barColor else mutedBarColor)
            }

            drawLine(
                color = axisColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            data.forEachIndexed { index, entry ->
                val show = index % labelStep == 0 || index == data.lastIndex
                Text(
                    text = if (show) entry.year.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
