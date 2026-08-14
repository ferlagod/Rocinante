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
package com.ferlagod.rocinante.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Cuánto falta para terminar un libro, al ritmo que se lleva.
 *
 * La cuenta es sencilla —lo leído en los días que se lleva da un ritmo, y lo que queda dividido
 * por ese ritmo da los días—, pero tiene bastantes maneras de salir mal: un libro empezado hoy,
 * uno sin fecha de inicio, uno del que no se sabe cuántas páginas tiene. En todos esos casos
 * **no se inventa un número**: se devuelve null y la pantalla no enseña nada, que es mejor que
 * una previsión sacada del aire.
 *
 * Todo en una función aparte para poder probarla sin Android delante, porque el resultado se
 * enseña en dos sitios y tiene que decir lo mismo en los dos.
 */
object ReadingPace {

    /**
     * Qué parte del libro se lleva leída, entre 0 y 1.
     *
     * BookWyrm cuenta el progreso de dos maneras: por página o por porcentaje. Por página hace
     * falta saber cuántas tiene el libro; si no consta, no hay fracción que calcular.
     *
     * @param progress lo que dice la instancia: un número de página o un porcentaje.
     * @param isPercent si ese número es un porcentaje.
     * @param totalPages páginas del libro, o null si no se sabe.
     */
    fun fractionRead(progress: Int?, isPercent: Boolean, totalPages: Int?): Double? {
        if (progress == null || progress <= 0) return null
        val fraction = if (isPercent) {
            progress / 100.0
        } else {
            val pages = totalPages?.takeIf { it > 0 } ?: return null
            progress.toDouble() / pages
        }
        return fraction.takeIf { it > 0.0 && it < 1.0 }
    }

    /**
     * Días que faltan al ritmo que se lleva, redondeados hacia arriba.
     *
     * Hacia arriba y no al más cercano: quedando media jornada de lectura, la respuesta honrada
     * es «un día», no «cero». Por eso nunca devuelve 0 mientras quede algo por leer.
     *
     * El día de inicio cuenta como uno: quien empezó ayer lleva dos días leyendo, no uno. Sin
     * eso, un libro empezado hoy daría una división por cero.
     *
     * @return null si no hay con qué calcularlo: sin fecha de inicio, sin progreso, con el libro
     *   acabado, o con una fecha de inicio en el futuro.
     */
    fun daysLeft(startedIso: String?, fraction: Double?, today: LocalDate): Int? {
        val started = parseDate(startedIso) ?: return null
        val read = fraction?.takeIf { it > 0.0 && it < 1.0 } ?: return null
        val elapsed = ChronoUnit.DAYS.between(started, today) + 1
        if (elapsed <= 0) return null
        val days = ceil(elapsed * (1.0 - read) / read)
        // Un libro leído a paso de tortuga puede dar cifras absurdas; más allá de unos años la
        // previsión no dice nada útil y es mejor callarse.
        if (days > 3650) return null
        return days.toInt().coerceAtLeast(1)
    }

    private fun parseDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
    }
}
