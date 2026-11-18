/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: usted puede redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada
 * por la Fundación para el Software Libre, ya sea la versión 3
 * de la Licencia, o (a su elección) cualquier versión posterior.
 */
package com.ferlagod.rocinante.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventaireClientTest {

    // Respuestas reales de inventaire.io (recortadas) para el libro del issue #6.
    private val searchJson = """
        {"results":[
          {"id":"4b3b745c8f98c27bdecc3e833ff14902","type":"works","uri":"inv:4b3b745c8f98c27bdecc3e833ff14902","label":"una mente in frammenti","claims":{},"_score":37017.734},
          {"id":"Q3400532","type":"works","uri":"wd:Q3400532","label":"Frammenti di antropologia anarchica","description":"libro di David Graeber","image":"/img/entities/0ed97bd44de3014380c3a34bd12df6f322ab7437","claims":{},"_score":518.91113},
          {"id":"Q1","type":"works","uri":"wd:Q1","label":"Parecido","description":"libro di Qualcuno","image":"/img/entities/abc","claims":{},"_score":20000.0}
        ],"total":10000}
    """.trimIndent()

    private val isbnJson = """
        {"entities":{"inv:4b3b745c8f98c27bdecc3e833ff1537d":{"type":"edition","labels":{"fromclaims":"una mente in frammenti"},
          "claims":{"wdt:P212":["978-88-340-1884-2"],"wdt:P629":["inv:4b3b745c8f98c27bdecc3e833ff14902"],"wdt:P1476":["una mente in frammenti"],"wdt:P577":["2019-05"]},
          "uri":"inv:4b3b745c8f98c27bdecc3e833ff1537d","image":{}}},
         "redirects":{"isbn:9788834018842":"inv:4b3b745c8f98c27bdecc3e833ff1537d"}}
    """.trimIndent()

    @Test
    fun `la busqueda deja fuera el ruido y usa la clave del conector de BookWyrm`() {
        val results = InventaireClient.parseSearch(JsonParser.parseString(searchJson).asJsonObject)

        assertEquals(listOf("una mente in frammenti", "Parecido"), results.map { it.title })
        val first = results.first()
        // La misma dirección que da la instancia, para que los repetidos se junten y resolve-book la entienda.
        assertEquals("https://inventaire.io/api/entities?action=by-uris&uris=inv:4b3b745c8f98c27bdecc3e833ff14902", first.key)
        assertEquals(first.key, first.remoteId)
        assertTrue(first.isRemote)
        assertEquals(InventaireClient.SOURCE_NAME, first.source)
        assertEquals("https://inventaire.io/img/entities/abc", results[1].cover)
        assertEquals("libro di Qualcuno", results[1].author)
    }

    @Test
    fun `el ISBN encuentra la edicion aunque no tenga portada`() {
        val results = InventaireClient.parseEntities(JsonParser.parseString(isbnJson).asJsonObject)

        assertEquals(1, results.size)
        val edition = results.single()
        assertEquals("una mente in frammenti", edition.title)
        assertEquals(2019, edition.year)
        assertEquals(null, edition.cover)
        assertEquals("https://inventaire.io/api/entities?action=by-uris&uris=inv:4b3b745c8f98c27bdecc3e833ff1537d", edition.remoteId)
    }
}
