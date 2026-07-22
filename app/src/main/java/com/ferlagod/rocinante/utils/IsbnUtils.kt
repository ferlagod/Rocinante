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

/**
 * Utilidades puras (sin dependencias de Android) para reconocer ISBNs a partir del
 * texto que devuelve el escáner de códigos de barras o que teclea el usuario.
 *
 * El objetivo NO es validar el dígito de control, sino decidir si merece la pena
 * consultar el endpoint estable `/isbn/<isbn>.json` antes de recurrir a la búsqueda
 * por texto. Comprueba únicamente longitud y forma.
 */
object IsbnUtils {

    /**
     * Reduce una cadena a sus caracteres de ISBN: dígitos y la posible 'X' de control
     * (normalizada a mayúscula). Descarta guiones, espacios, prefijos "ISBN", etc.
     */
    fun normalize(raw: String): String =
        raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

    /**
     * ¿La cadena, una vez normalizada, tiene forma de ISBN?
     *
     * - ISBN-13: exactamente 13 dígitos (los códigos de barras EAN de libros no llevan 'X').
     * - ISBN-10: 9 dígitos seguidos de un dígito de control que puede ser un dígito o 'X'.
     *
     * Cualquier otra longitud, o una 'X' fuera de la última posición de un ISBN-10,
     * se rechaza.
     */
    fun isIsbn(raw: String): Boolean {
        val n = normalize(raw)
        return when (n.length) {
            13 -> n.all { it.isDigit() }
            10 -> n.take(9).all { it.isDigit() } && (n[9].isDigit() || n[9] == 'X')
            else -> false
        }
    }
}
