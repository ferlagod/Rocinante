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
package com.ferlagod.rocinante.data.model

import com.ferlagod.rocinante.data.api.NetworkClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** Las reseñas de BookWyrm pueden llevar media estrella, y la app no debe perderla ni fallar. */
class HalfStarRatingTest {

    @Test
    fun `una reseña con media estrella se lee entera`() {
        val json = """{"id": "r1", "type": "Review", "content": "Bien", "name": null, "rating": 4.5}"""
        val review = NetworkClient.gson.fromJson(json, ActivityPubObject::class.java)
        assertEquals(4.5, review.rating!!, 0.0)
    }

    @Test
    fun `una reseña con estrellas enteras sigue leyéndose`() {
        val json = """{"id": "r2", "type": "Review", "content": "Bien", "name": null, "rating": 3}"""
        val review = NetworkClient.gson.fromJson(json, ActivityPubObject::class.java)
        assertEquals(3.0, review.rating!!, 0.0)
    }
}
