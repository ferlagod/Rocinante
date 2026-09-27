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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.utils.ReadingStats
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Tarjeta de estadísticas de lectura del perfil: tres cifras destacadas (total de libros,
 * libros de este año y páginas acumuladas), desglose de lecturas/relecturas, resumen de páginas,
 * y gráficos alternables entre histórico anual y meses del año.
 */
@Composable
fun ReadingStatsCard(
    stats: ReadingStats,
    currentYear: Int,
    modifier: Modifier = Modifier,
    onFixMissingPages: (() -> Unit)? = null,
    onFixMissingDates: (() -> Unit)? = null,
    onYearClick: ((Int) -> Unit)? = null,
    onMonthClick: ((year: Int, month: Int) -> Unit)? = null,
    showReadingPace: Boolean = false,
    selectedYear: Int? = null,
    availableYears: List<Int> = emptyList(),
    onSelectYear: ((Int?) -> Unit)? = null,
    onShareClick: (() -> Unit)? = null
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val displayYear = stats.filterYear ?: currentYear
    var chartViewByMonths by remember { mutableStateOf(false) }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        // Cabecera con selector temporal y botón de compartir
        if (availableYears.isNotEmpty() || onShareClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedYear == null,
                        onClick = { onSelectYear?.invoke(null) },
                        label = { Text(stringResource(R.string.profile_stats_filter_all_time)) }
                    )
                    availableYears.take(6).forEach { year ->
                        FilterChip(
                            selected = selectedYear == year,
                            onClick = { onSelectYear?.invoke(if (selectedYear == year) null else year) },
                            label = { Text(year.toString()) }
                        )
                    }
                }
                if (onShareClick != null) {
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.profile_stats_share_summary),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        // Celdas principales de estadísticas
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
                subValue = if (stats.rereadsCount > 0) {
                    stringResource(R.string.profile_stats_reads_breakdown, stats.uniqueBooksCount, stats.rereadsCount)
                } else null,
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(modifier = Modifier.height(36.dp))
            StatCell(
                value = numberFormat.format(stats.booksThisYear),
                label = if (stats.filterYear != null) {
                    stringResource(R.string.profile_stats_filter_year, stats.filterYear)
                } else {
                    stringResource(R.string.profile_stats_this_year)
                },
                subValue = if (stats.pagesThisYear > 0) {
                    "${numberFormat.format(stats.pagesThisYear)} págs."
                } else null,
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(modifier = Modifier.height(36.dp))
            StatCell(
                value = numberFormat.format(stats.totalPages),
                label = stringResource(R.string.profile_stats_pages),
                subValue = stats.avgPagesPerBook?.let {
                    stringResource(R.string.profile_stats_pages_per_book, it.roundToInt())
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Resumen detallado: «12 libros este año · 3.840 págs. · media de 320 págs./libro»
        if (stats.booksThisYear > 0 && stats.pagesThisYear > 0 && stats.avgPagesPerBookThisYear != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        R.string.profile_stats_summary_this_year,
                        stats.booksThisYear,
                        numberFormat.format(stats.pagesThisYear),
                        stats.avgPagesPerBookThisYear.roundToInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Ritmo de lectura independiente del reto anual
        if (showReadingPace && stats.hasReadingDays) {
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
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .then(if (canFixDates) Modifier.clickable { onFixMissingDates?.invoke() } else Modifier)
            )
        }

        // Gráficos de lectura: Por años vs Meses
        if (stats.hasChartData || stats.hasMonthlyData) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (chartViewByMonths) {
                        stringResource(R.string.profile_stats_view_months, displayYear)
                    } else {
                        stringResource(R.string.profile_stats_per_year)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !chartViewByMonths,
                        onClick = { chartViewByMonths = false },
                        label = { Text(stringResource(R.string.profile_stats_view_years)) }
                    )
                    FilterChip(
                        selected = chartViewByMonths,
                        onClick = { chartViewByMonths = true },
                        label = { Text(stringResource(R.string.profile_stats_view_months, displayYear)) }
                    )
                }
            }

            if (chartViewByMonths) {
                BooksPerMonthChart(
                    data = stats.booksPerMonthThisYear,
                    year = displayYear,
                    numberFormat = numberFormat,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onMonthClick = onMonthClick
                )
            } else {
                BooksPerYearChart(
                    data = stats.booksPerYear,
                    currentYear = currentYear,
                    numberFormat = numberFormat,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onYearClick = onYearClick
                )
            }
        }

        // Advertencias sobre datos que faltan
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
    onRatingClick: ((Double) -> Unit)? = null,
    // Qué hacer con los libros sin valorar, que son los que no entran en el reparto.
    onFixMissingRatings: (() -> Unit)? = null
) {
    if (!stats.hasRatingData) return

    val numberFormat = remember { NumberFormat.getInstance() }
    val averageText = stats.averageRating
        ?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) } ?: ""
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
                // Igual que las páginas y las fechas: el aviso lleva a los libros de los que
                // habla, en vez de quedarse en aviso.
                val canFix = onFixMissingRatings != null
                Text(
                    text = pluralStringResource(
                        R.plurals.profile_stats_missing_ratings,
                        stats.booksWithoutRating,
                        stats.booksWithoutRating
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canFix) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (canFix) {
                        Modifier.clickable { onFixMissingRatings!!() }
                    } else {
                        Modifier
                    }
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
    onLanguageClick: ((String) -> Unit)? = null,
    // Qué hacer con los libros sin idioma. Ese dato no se toca desde aquí: lleva a la
    // instancia, que es donde se edita.
    onFixMissing: (() -> Unit)? = null
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
        modifier = modifier,
        onCaveatClick = onFixMissing
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
    onFormatClick: ((String) -> Unit)? = null,
    // Qué hacer con los libros sin formato. Igual que el idioma: se edita en la instancia.
    onFixMissing: (() -> Unit)? = null
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
        modifier = modifier,
        onCaveatClick = onFixMissing
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
    // Qué hacer al tocar la nota de los que faltan, o null si no hay nada que hacer con ellos.
    onCaveatClick: (() -> Unit)? = null,
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
                    color = if (onCaveatClick != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (onCaveatClick != null) {
                        Modifier.clickable { onCaveatClick() }
                    } else {
                        Modifier
                    }
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
    val animatedProgress by animateFloatAsState(
        targetValue = if (count > 0 && maxCount > 0) (count / maxCount.toFloat()).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "BarProgress"
    )

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
            if (animatedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
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
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    subValue: String? = null
) {
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
        if (!subValue.isNullOrBlank()) {
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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

    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val peakYear = data.maxByOrNull { it.count }?.year
    // Con muchos años no caben todas las etiquetas: se muestra una de cada N y siempre la última.
    val labelStep = ((data.size + 7) / 8).coerceAtLeast(1)

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "YearChartAnim"
    )

    val chartDescription = stringResource(
        R.string.profile_stats_chart_desc,
        data.joinToString(", ") { "${it.year}: ${it.count}" }
    )

    Column(modifier = modifier.semantics { contentDescription = chartDescription }) {
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
                val barHeight = ((size.height * entry.count / maxCount) * animProgress).coerceAtLeast(minVisibleHeight)
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

/**
 * Gráfico de barras de los 12 meses del año consultado: destaca el mes pico de lecturas
 * y permite pulsar un mes para abrir los libros terminados en él.
 */
@Composable
private fun BooksPerMonthChart(
    data: List<ReadingStats.MonthCount>,
    year: Int,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier,
    onMonthClick: ((year: Int, month: Int) -> Unit)? = null
) {
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val axisColor = MaterialTheme.colorScheme.outlineVariant

    val maxCount = data.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val peakMonth = data.maxByOrNull { it.count }?.month

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "MonthChartAnim"
    )

    val monthNames = remember {
        (1..12).map { m ->
            java.time.Month.of(m).getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault()
            ).take(3).replaceFirstChar { it.uppercase() }
        }
    }

    val chartDescription = stringResource(
        R.string.profile_stats_month_chart_desc,
        year,
        data.joinToString(", ") { "${monthNames.getOrElse(it.month - 1) { _ -> it.month.toString() }}: ${it.count}" }
    )

    Column(modifier = modifier.semantics { contentDescription = chartDescription }) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            data.forEach { entry ->
                val showValue = entry.count > 0 && entry.month == peakMonth
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
                .then(
                    if (onMonthClick != null && data.isNotEmpty()) {
                        Modifier.pointerInput(data, year) {
                            detectTapGestures { offset ->
                                val slot = size.width.toFloat() / data.size
                                val index = (offset.x / slot).toInt().coerceIn(0, data.size - 1)
                                val entry = data[index]
                                if (entry.count > 0) onMonthClick(year, entry.month)
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
                val barHeight = ((size.height * entry.count / maxCount) * animProgress).coerceAtLeast(minVisibleHeight)
                val left = index * slotWidth + gap / 2f
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
                drawPath(path, color = if (entry.month == peakMonth) barColor else mutedBarColor)
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
                Text(
                    text = monthNames.getOrElse(index) { entry.month.toString() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
