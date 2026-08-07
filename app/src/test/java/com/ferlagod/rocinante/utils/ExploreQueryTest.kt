package com.ferlagod.rocinante.utils

import com.ferlagod.rocinante.utils.ExploreQuery.Field
import com.ferlagod.rocinante.utils.ExploreQuery.Op
import com.ferlagod.rocinante.utils.ExploreQuery.Orden
import com.ferlagod.rocinante.utils.ExploreQuery.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lo que se prueba aquí es lo que no se ve: si un paréntesis cae en el sitio equivocado o si el
 * texto del usuario se cuela como sintaxis, la pantalla no da error —devuelve otros libros—.
 */
class ExploreQueryTest {

    @Test
    fun `una fila sola no lleva parentesis`() {
        val q = ExploreQuery.build(listOf(Row(Field.MATERIA, text = "historical_fiction", label = "Historical fiction")))
        assertEquals("subject_key:\"historical_fiction\"", q)
    }

    @Test
    fun `sin filas no hay consulta`() {
        assertNull(ExploreQuery.build(emptyList()))
        // Una fila en blanco es lo mismo que ninguna: si esto devolviera algo, buscar sin
        // escribir nada se traería el catálogo entero.
        assertNull(ExploreQuery.build(listOf(Row(Field.TITULO, text = "   "))))
        assertNull(ExploreQuery.build(listOf(Row(Field.ANIO))))
    }

    @Test
    fun `agrupa de arriba abajo con parentesis propios`() {
        // Open Library no respeta la precedencia booleana esperada, así que la agrupación tiene
        // que ser explícita y en el orden en que se lee la lista.
        val q = ExploreQuery.build(
            listOf(
                Row(Field.MATERIA, text = "historical_fiction", label = "Historical fiction"),
                Row(Field.IDIOMA, op = Op.O, text = "dan"),
                Row(Field.ANIO, op = Op.Y, from = 1990, to = 2026)
            )
        )
        assertEquals(
            "((subject_key:\"historical_fiction\" OR language:dan)" +
                " AND first_publish_year:[1990 TO 2026])",
            q
        )
    }

    @Test
    fun `el NO se escribe como AND NOT`() {
        val q = ExploreQuery.build(
            listOf(
                Row(Field.MATERIA, text = "historical_fiction", label = "Historical fiction"),
                Row(Field.MATERIA, op = Op.NO, text = "juvenile_fiction", label = "Juvenile fiction")
            )
        )
        assertEquals(
            "(subject_key:\"historical_fiction\" AND NOT subject_key:\"juvenile_fiction\")",
            q
        )
    }

    @Test
    fun `el operador de la primera fila no se usa`() {
        // Pasa al borrar la fila de arriba: la que era un NO se queda la primera, y una consulta
        // que empieza por NO no filtra, describe todo lo demás.
        val q = ExploreQuery.build(listOf(Row(Field.MATERIA, op = Op.NO, text = "juvenile_fiction", label = "Juvenile fiction")))
        assertEquals("subject_key:\"juvenile_fiction\"", q)
    }

    @Test
    fun `las filas vacias no dejan operadores sueltos`() {
        val q = ExploreQuery.build(
            listOf(
                Row(Field.MATERIA, text = "historical_fiction", label = "Historical fiction"),
                Row(Field.TITULO, op = Op.O, text = ""),
                Row(Field.IDIOMA, op = Op.Y, text = "dan")
            )
        )
        assertEquals("(subject_key:\"historical_fiction\" AND language:dan)", q)
    }

    @Test
    fun `el texto del usuario no se convierte en sintaxis`() {
        // Dos puntos, paréntesis y la palabra AND son texto dentro de una frase; si salieran de
        // las comillas, esto sería otra consulta y devolvería otros libros.
        val q = ExploreQuery.build(listOf(Row(Field.TITULO, text = "Fiction: general (1980) AND war")))
        assertEquals("title:\"Fiction: general (1980) AND war\"", q)
    }

    @Test
    fun `las comillas y las barras del usuario se escapan`() {
        val q = ExploreQuery.build(listOf(Row(Field.TITULO, text = "el \"gran\" c:\\libro")))
        assertEquals("title:\"el \\\"gran\\\" c:\\\\libro\"", q)
    }

    @Test
    fun `el idioma va sin comillas`() {
        // Comprobado contra Open Library: `language:"dan"` devuelve cero obras y `language:dan`
        // devuelve 100.418. Poner comillas aquí vacía la pantalla sin dar ningún error.
        assertEquals(
            "language:dan",
            ExploreQuery.build(listOf(Row(Field.IDIOMA, text = "dan")))
        )
    }

    @Test
    fun `un codigo solo puede ser un codigo`() {
        // Sin comillas el valor es sintaxis. Se descarta entero en vez de limpiarlo: quitándole
        // lo que sobra, `DAN" OR *:*` quedaría en `danor`, un idioma que no existe.
        assertNull(ExploreQuery.build(listOf(Row(Field.IDIOMA, text = " DAN\" OR *:* "))))
        assertEquals("language:dan", ExploreQuery.build(listOf(Row(Field.IDIOMA, text = " DAN "))))
    }

    @Test
    fun `los rangos admiten un extremo abierto`() {
        assertEquals(
            "first_publish_year:[* TO 1950]",
            ExploreQuery.build(listOf(Row(Field.ANIO, to = 1950)))
        )
        assertEquals(
            "first_publish_year:[1950 TO *]",
            ExploreQuery.build(listOf(Row(Field.ANIO, from = 1950)))
        )
    }

    @Test
    fun `ordenar por nota exige un minimo de votos`() {
        // Sin suelo, la nota media la lidera cualquier libro con cuatro votos generosos.
        val q = ExploreQuery.build(
            listOf(Row(Field.MATERIA, text = "historical_fiction", label = "Historical fiction")),
            orden = Orden.VALORACION
        )
        assertEquals(
            "(subject_key:\"historical_fiction\" AND ratings_count:[50 TO *])",
            q
        )
    }

    @Test
    fun `el minimo de votos no se pone dos veces`() {
        val q = ExploreQuery.build(
            listOf(
                Row(Field.MATERIA, text = "historical_fiction", label = "Historical fiction"),
                Row(Field.VALORACIONES, op = Op.Y, from = 200)
            ),
            orden = Orden.VALORACION
        )
        assertEquals(
            "(subject_key:\"historical_fiction\" AND ratings_count:[200 TO *])",
            q
        )
    }

    @Test
    fun `los demas ordenes no tocan la consulta`() {
        val rows = listOf(Row(Field.MATERIA, text = "historical_fiction", label = "Historical fiction"))
        assertEquals(ExploreQuery.build(rows), ExploreQuery.build(rows, Orden.LEIDOS))
    }
}
