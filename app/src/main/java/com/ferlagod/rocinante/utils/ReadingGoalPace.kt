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

/**
 * Ritmo del reto de lectura anual: compara los libros leídos con los que tocarían a
 * estas alturas del año, repartiendo la meta de forma uniforme entre sus días.
 */
object ReadingGoalPace {

    /**
     * @return libros de adelanto (positivo) o de retraso (negativo) respecto al ritmo que
     *   exige la meta hoy; 0 significa ir al día. Null si la meta no es utilizable.
     *
     * La fecha se recibe como parámetro —en vez de leer el reloj aquí— para que la
     * función sea comprobable y no dependa del día en que se ejecute la prueba.
     */
    fun booksAheadOfSchedule(booksRead: Int, goal: Int, today: LocalDate): Int? {
        if (goal <= 0) return null
        val expected = goal.toDouble() * today.dayOfYear / today.lengthOfYear()
        return Math.round(booksRead - expected).toInt()
    }
}
