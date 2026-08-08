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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier,
    // Qué hacer con los libros a los que les faltan las páginas. Sin esto la advertencia se
    // queda en advertencia, que es como estaba.
    onFixMissingPages: (() -> Unit)? = null,
    // Qué hacer con los libros a los que les falta la fecha de fin, que son los que no salen
    // en la gráfica por años.
    onFixMissingDates: (() -> Unit)? = null,
    // Qué hacer al tocar el año de la gráfica, para ir a ver los libros de ese año.
    onYearClick: ((Int) -> Unit)? = null
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                onYearClick = onYearClick
            )
        }

        // Los datos que faltan se dicen, no se disimulan: si no, las cifras aparentan ser
        // totales cuando en realidad solo suman los libros que traían el dato.
        if (stats.booksWithoutFinishDate > 0 || stats.booksWithoutPages > 0) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (stats.booksWithoutFinishDate > 0) {
                    // Igual que las páginas: se puede hacer algo, así que el aviso lleva a
                    // los libros de los que habla.
                    val canFixDates = onFixMissingDates != null
                    Text(
                        text = pluralStringResource(
                            R.plurals.profile_stats_missing_dates,
                            stats.booksWithoutFinishDate,
                            stats.booksWithoutFinishDate
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canFixDates) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (canFixDates) {
                            Modifier.clickable { onFixMissingDates!!() }
                        } else {
                            Modifier
                        }
                    )
                }
                if (stats.booksWithoutPages > 0) {
                    // Se puede hacer algo al respecto, así que el propio aviso lleva a la lista
                    // de esos libros: no hace falta un botón aparte diciendo lo mismo.
                    val canFix = onFixMissingPages != null
                    Text(
                        text = pluralStringResource(
                            R.plurals.profile_stats_missing_pages,
                            stats.booksWithoutPages,
                            stats.booksWithoutPages
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canFix) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (canFix) {
                            Modifier.clickable { onFixMissingPages!!() }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

/**
 * Añadido del reto de lectura: si se va por delante o por detrás del ritmo, y cuántos días
 * se tarda en leer un libro (este año y en total), con la misma anatomía que seguidores
 * y seguidos.
 *
 * Va dentro de la tarjeta del reto, debajo de su barra de progreso.
 *
 * @param booksAheadOfSchedule libros de adelanto (positivo) o retraso (negativo); null si
 *   la meta no permite calcularlo.
 */
@Composable
fun ReadingGoalPaceSection(
    stats: ReadingStats,
    booksAheadOfSchedule: Int?,
    modifier: Modifier = Modifier,
    // Qué hacer con los libros a los que les falta alguna fecha. La media de días se calcula
    // solo con los que tienen las dos, y ese es justo el renglón que lo dice.
    onFixMissingDates: (() -> Unit)? = null
) {
    if (booksAheadOfSchedule == null && !stats.hasReadingDays) return

    // Días por libro se enseñan como número entero: la media sale con decimales, pero
    // "12,4 días" finge una precisión que no hay cuando se calcula sobre unos pocos libros.
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    Column(modifier = modifier.fillMaxWidth()) {
        if (booksAheadOfSchedule != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val onSchedule = booksAheadOfSchedule >= 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (onSchedule) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = if (onSchedule) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        booksAheadOfSchedule > 0 -> pluralStringResource(
                            R.plurals.profile_goal_ahead,
                            booksAheadOfSchedule,
                            booksAheadOfSchedule
                        )
                        booksAheadOfSchedule < 0 -> pluralStringResource(
                            R.plurals.profile_goal_behind,
                            -booksAheadOfSchedule,
                            -booksAheadOfSchedule
                        )
                        else -> stringResource(R.string.profile_goal_on_track)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (stats.hasReadingDays) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCell(
                    value = stats.avgReadingDaysThisYear?.let { numberFormat.format(it.roundToInt()) } ?: "–",
                    label = stringResource(R.string.profile_stats_days_this_year),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(modifier = Modifier.height(36.dp))
                StatCell(
                    value = stats.avgReadingDaysAllTime?.let { numberFormat.format(it.roundToInt()) } ?: "–",
                    label = stringResource(R.string.profile_stats_days_total),
                    modifier = Modifier.weight(1f)
                )
            }
            // La base es pequeña porque BookWyrm rara vez guarda la fecha de inicio: se dice
            // sobre cuántos libros se ha calculado en lugar de presentarlo como la media de todos.
            val canFixDates = onFixMissingDates != null && stats.booksWithReadingDays < stats.totalBooks
            Text(
                text = stringResource(
                    R.string.profile_stats_days_basis,
                    stats.booksWithReadingDays,
                    stats.totalBooks
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (canFixDates) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (canFixDates) {
                    Modifier.clickable { onFixMissingDates!!() }
                } else {
                    Modifier
                }
            )
        }
    }
}

/**
 * Autores más leídos, en barras horizontales: los nombres son textos largos y en vertical
 * no cabrían. Cada fila lleva su cifra al final, que aquí es el contenido y no un adorno.
 *
 * Como el resto de la tarjeta, se calcula con lo que ya está cacheado.
 */
@Composable
fun TopAuthorsCard(
    stats: ReadingStats,
    modifier: Modifier = Modifier,
    // Qué hacer con los libros a los que les falta el autor; sin esto el aviso se queda en
    // aviso, como estaba.
    onFixMissingAuthors: (() -> Unit)? = null,
    // Qué hacer al tocar un autor. La gráfica dice cuántos libros suyos hay leídos; esto lleva
    // a verlos. Nada cambia de aspecto: la barra solo se puede tocar.
    onAuthorClick: ((String) -> Unit)? = null
) {
    if (!stats.hasAuthorData) return

    val maxCount = stats.topAuthors.maxOf { it.count }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val chartDescription = stringResource(
        R.string.profile_stats_authors_desc,
        stats.topAuthors.joinToString(", ") { "${it.name}: ${it.count}" }
    )

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { contentDescription = chartDescription }
        ) {
            Text(
                text = stringResource(R.string.profile_stats_top_authors),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            stats.topAuthors.forEach { author ->
                HorizontalBarRow(
                    count = author.count,
                    maxCount = maxCount,
                    labelWeight = 0.42f,
                    barColor = barColor,
                    trackColor = trackColor,
                    onClick = onAuthorClick?.let { { it(author.name) } }
                ) {
                    Text(
                        text = author.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (stats.booksWithoutAuthor > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                // Igual que con las páginas: el aviso lleva a los libros de los que habla.
                val canFix = onFixMissingAuthors != null
                Text(
                    text = pluralStringResource(
                        R.plurals.profile_stats_missing_authors,
                        stats.booksWithoutAuthor,
                        stats.booksWithoutAuthor
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canFix) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (canFix) Modifier.clickable { onFixMissingAuthors!!() } else Modifier
                )
            }
        }
    }
}

/**
 * Reparto de las valoraciones propias, con la misma anatomía que los autores más leídos:
 * la etiqueta a la izquierda —aquí las estrellas—, la barra en medio y la cifra al final.
 */
@Composable
fun RatingsCard(
    stats: ReadingStats,
    modifier: Modifier = Modifier,
    // Qué hacer al tocar una nota, para ir a ver esos libros. Nada cambia de aspecto.
    onRatingClick: ((Double) -> Unit)? = null
) {
    if (!stats.hasRatingData) return

    val numberFormat = remember { NumberFormat.getInstance() }
    // La media se redondea a media estrella, que es lo mínimo que se puede puntuar en
    // BookWyrm: un 4,28 no corresponde a ninguna valoración que se pueda dar.
    val averageText = stats.averageRating
        ?.let { numberFormat.format(Math.round(it * 2) / 2.0) } ?: ""
    val maxCount = stats.ratingDistribution.maxOf { it.count }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val chartDescription = stringResource(
        R.string.profile_stats_ratings_desc,
        stats.ratingDistribution.joinToString(", ") {
            "${numberFormat.format(it.rating)}: ${it.count}"
        }
    )

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { contentDescription = chartDescription }
        ) {
            Text(
                text = stringResource(R.string.profile_stats_ratings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.profile_stats_average_rating, averageText) +
                    "  ·  " +
                    pluralStringResource(
                        R.plurals.profile_stats_rating_count,
                        stats.ratedBooks,
                        stats.ratedBooks
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            stats.ratingDistribution.forEach { bucket ->
                HorizontalBarRow(
                    count = bucket.count,
                    maxCount = maxCount,
                    labelWeight = 0.30f,
                    barColor = barColor,
                    trackColor = trackColor,
                    // Solo las notas que alguien ha usado: una barra a cero no lleva a ningún
                    // sitio, y tocarla dejaría una lista vacía sin explicación.
                    onClick = onRatingClick?.takeIf { bucket.count > 0 }
                        ?.let { { it(bucket.rating) } }
                ) {
                    RatingStars(rating = bucket.rating, starSize = 14.dp)
                }
            }

            if (stats.booksWithoutRating > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.profile_stats_missing_ratings,
                        stats.booksWithoutRating,
                        stats.booksWithoutRating
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Idiomas de lectura. La etiqueta lleva la bandera del idioma delante del nombre; los
 * idiomas sin bandera se quedan solo con el nombre.
 */
@Composable
fun LanguagesCard(
    stats: ReadingStats,
    modifier: Modifier = Modifier,
    // Qué hacer al tocar un idioma, para ir a ver esos libros. Nada cambia de aspecto.
    onLanguageClick: ((String) -> Unit)? = null
) {
    if (!stats.hasLanguageData) return

    val maxCount = stats.languageDistribution.maxOf { it.count }.coerceAtLeast(1)

    BarChartCard(
        title = stringResource(R.string.profile_stats_languages),
        chartDescription = stringResource(
            R.string.profile_stats_languages_desc,
            stats.languageDistribution.joinToString(", ") { "${it.label}: ${it.count}" }
        ),
        caveat = if (stats.booksWithoutLanguage > 0) {
            pluralStringResource(
                R.plurals.profile_stats_missing_languages,
                stats.booksWithoutLanguage,
                stats.booksWithoutLanguage
            )
        } else {
            null
        },
        modifier = modifier
    ) { barColor, trackColor ->
        stats.languageDistribution.forEach { language ->
            HorizontalBarRow(
                count = language.count,
                maxCount = maxCount,
                labelWeight = 0.42f,
                barColor = barColor,
                trackColor = trackColor,
                onClick = onLanguageClick?.let { { it(language.label) } }
            ) {
                Text(
                    text = language.flag?.let { "$it ${language.label}" } ?: language.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Formatos de los ejemplares leídos (tapa dura, bolsillo, audiolibro…).
 */
@Composable
fun FormatsCard(
    stats: ReadingStats,
    modifier: Modifier = Modifier,
    // Qué hacer al tocar un formato, para ir a ver esos libros. Nada cambia de aspecto.
    onFormatClick: ((String) -> Unit)? = null
) {
    if (!stats.hasFormatData) return

    val maxCount = stats.formatDistribution.maxOf { it.count }.coerceAtLeast(1)

    BarChartCard(
        title = stringResource(R.string.profile_stats_formats),
        chartDescription = stringResource(
            R.string.profile_stats_formats_desc,
            stats.formatDistribution.joinToString(", ") { "${it.format}: ${it.count}" }
        ),
        caveat = if (stats.booksWithoutFormat > 0) {
            pluralStringResource(
                R.plurals.profile_stats_missing_formats,
                stats.booksWithoutFormat,
                stats.booksWithoutFormat
            )
        } else {
            null
        },
        modifier = modifier
    ) { barColor, trackColor ->
        stats.formatDistribution.forEach { format ->
            HorizontalBarRow(
                count = format.count,
                maxCount = maxCount,
                labelWeight = 0.42f,
                barColor = barColor,
                trackColor = trackColor,
                onClick = onFormatClick?.let { { it(format.format) } }
            ) {
                Text(
                    text = formatLabel(format.format),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Traduce los valores de formato de BookWyrm. Si aparece uno desconocido se muestra tal
 * cual: es preferible un término en inglés que esconder un dato real.
 */
@Composable
fun formatLabel(rawFormat: String): String = when (rawFormat) {
    "Hardcover" -> stringResource(R.string.book_format_hardcover)
    "Paperback" -> stringResource(R.string.book_format_paperback)
    "EBook" -> stringResource(R.string.book_format_ebook)
    "AudiobookFormat" -> stringResource(R.string.book_format_audiobook)
    "GraphicNovel" -> stringResource(R.string.book_format_graphic_novel)
    else -> rawFormat
}

/**
 * Envoltorio común de los gráficos de barras horizontales: título, filas y, si procede,
 * la nota sobre los libros que no traen el dato.
 */
@Composable
private fun BarChartCard(
    title: String,
    chartDescription: String,
    caveat: String?,
    modifier: Modifier = Modifier,
    rows: @Composable (barColor: Color, trackColor: Color) -> Unit
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .semantics { contentDescription = chartDescription }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            rows(barColor, trackColor)
            if (caveat != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = caveat,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Una fila del gráfico de barras horizontales: etiqueta, barra y cifra. La etiqueta es un
 * bloque libre para que cada gráfico ponga lo suyo —un nombre de autor o unas estrellas—
 * sin que las dos filas se separen visualmente.
 *
 * La barra va sobre una pista tenue para que las filas cortas sigan leyéndose como una escala.
 */
@Composable
private fun HorizontalBarRow(
    count: Int,
    maxCount: Int,
    labelWeight: Float,
    barColor: Color,
    trackColor: Color,
    // Qué hacer al tocar la fila, o null si no hace nada. La fila mide y se coloca igual en
    // ambos casos: solo gana el toque.
    onClick: (() -> Unit)? = null,
    label: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(labelWeight),
            contentAlignment = Alignment.CenterStart
        ) {
            label()
        }
        Box(
            modifier = Modifier
                .weight(0.90f - labelWeight)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(trackColor)
        ) {
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(count / maxCount.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(barColor)
                )
            }
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.10f)
        )
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
    modifier: Modifier = Modifier,
    // Qué hacer al tocar una barra, para ir a ver los libros de ese año.
    onYearClick: ((Int) -> Unit)? = null
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
                // El gráfico es un lienzo, así que el toque hay que traducirlo a barra: se
                // divide el ancho entre los años, igual que al dibujarlas. Los años a cero no
                // llevan a ningún sitio; tocarlos dejaría una lista vacía sin explicación.
                .then(
                    if (onYearClick != null && data.isNotEmpty()) {
                        Modifier.pointerInput(data) {
                            detectTapGestures { offset ->
                                val slot = size.width.toFloat() / data.size
                                val index = (offset.x / slot).toInt().coerceIn(0, data.size - 1)
                                val entry = data[index]
                                if (entry.count > 0) onYearClick(entry.year)
                            }
                        }
                    } else Modifier
                )
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
