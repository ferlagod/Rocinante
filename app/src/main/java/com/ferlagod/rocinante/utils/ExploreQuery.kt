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
 * Construye la consulta de «Explorar»: una lista de filtros que el usuario apila en pantalla,
 * convertida en el parámetro `q` que entiende Open Library.
 *
 * Va por `q` y no por los parámetros sueltos (`subject=`, `language=`) porque esos no saben
 * hacer más que un filtro de cada clase, y porque el de materia es impreciso de una manera que
 * se mide: `subject=historical fiction` es un O entre las palabras y devuelve 116.933 obras
 * —todo lo que lleve «historical» *o* «fiction» en alguna materia—, mientras que la misma
 * materia entre comillas dentro de `q` devuelve 11.896. Las comillas del valor las ignora el
 * parámetro suelto, así que la precisión solo se consigue aquí.
 */
object ExploreQuery {

    /** Qué se puede filtrar. `solr` es el campo tal cual lo llama el índice de Open Library. */
    enum class Field(val solr: String, val kind: Kind) {
        // Por clave y no por frase. La frase funciona —`subject:"historical fiction"` son 11.896
        // obras— pero acepta cualquier cosa que uno escriba, y una materia que Open Library no
        // tenga no da error: da cero libros. Con la clave, la materia solo puede ser una de las
        // suyas, porque es su servidor quien la da.
        MATERIA("subject_key", Kind.TEXTO),
        TITULO("title", Kind.TEXTO),
        AUTOR("author_name", Kind.TEXTO),
        IDIOMA("language", Kind.CODIGO),
        ANIO("first_publish_year", Kind.RANGO),
        LUGAR("place", Kind.TEXTO),
        PERSONA("person", Kind.TEXTO),
        EPOCA("time", Kind.TEXTO),
        EDITORIAL("publisher", Kind.TEXTO),
        PAGINAS("number_of_pages_median", Kind.RANGO),
        VALORACIONES("ratings_count", Kind.MINIMO)
    }

    /**
     * De qué forma se escribe el valor de cada campo.
     *
     * [CODIGO] existe porque las comillas no son inofensivas en todas partes. Medido: `title:"krig"`
     * y `title:krig` devuelven las mismas 1.009 obras, pero `language:"dan"` devuelve **cero** y
     * `language:dan` devuelve 100.418. El campo de idioma guarda códigos sin analizar y una frase
     * entre comillas no le encaja. Y no da error: deja la pantalla en blanco como si no hubiera
     * libros, que es la misma trampa que los códigos MARC.
     */
    enum class Kind { TEXTO, CODIGO, RANGO, MINIMO }

    /** Lo que une una fila con lo que hay encima. La primera fila no lleva ninguno. */
    enum class Op { Y, O, NO }

    /**
     * Una fila de la pantalla.
     *
     * @property op Cómo se une con todo lo anterior. En la primera fila no se mira: una consulta
     *   que empieza por NO no filtra nada, describe «todo lo demás».
     * @property text El valor de los campos de texto. En [Field.IDIOMA] es el código MARC de tres
     *   letras («dan»), no la etiqueta de la aplicación: la conversión es cosa de quien crea la
     *   fila, para que esto no dependa de la capa de red y se pueda probar suelto.
     * @property from Extremo inferior de los campos de rango y valor de los de mínimo. Null es
     *   extremo abierto.
     * @property to Extremo superior de los campos de rango. Null es extremo abierto.
     * @property label Solo para enseñar. La materia se busca por su clave, `historical_fiction`,
     *   y eso es lo que no puede leer nadie: en la fila tiene que poner «Historical fiction». No
     *   entra en la consulta.
     */
    data class Row(
        val field: Field,
        val op: Op = Op.Y,
        val text: String = "",
        val from: Int? = null,
        val to: Int? = null,
        val label: String? = null
    )

    /**
     * Cómo se ordenan los resultados. No es un filtro y por eso no vive entre las filas: es un
     * parámetro aparte de Open Library.
     *
     * @property value Lo que se manda en `sort=`. Null es la relevancia, que es no mandar nada.
     * @property needsRatingsFloor Si hay que exigir un mínimo de votos. Solo `rating` lo necesita,
     *   y lo necesita de verdad: ordenando por nota sin suelo, «The Nightingale» con 41 votos
     *   sale por delante de «Nineteen Eighty-Four» con 415. La media sola no ordena nada.
     */
    enum class Orden(val value: String?, val needsRatingsFloor: Boolean = false) {
        RELEVANCIA(null),
        LEIDOS("readinglog"),
        VALORACION("rating", needsRatingsFloor = true),
        NUEVOS("new"),
        EDICIONES("editions"),
        AZAR("random.hourly")
    }

    /** Cuántos votos hacen falta para que la nota media signifique algo. */
    const val RATINGS_FLOOR = 50

    /**
     * La consulta, o null si no hay ninguna fila con contenido.
     *
     * Null importa: sin él, el botón de buscar se llevaría el catálogo entero de Open Library.
     *
     * Los paréntesis se ponen siempre, agrupando de arriba abajo, y no es por prudencia. El
     * analizador de Open Library no respeta la precedencia booleana que uno esperaría: medido
     * sobre datos reales, `A O B Y C` devuelve 17.813 obras, que no es ni `(A O B) Y C` —22.883—
     * ni `A O (B Y C)` —25.540—. Sin paréntesis propios, lo que el usuario ve en la lista no es
     * lo que se busca, y no hay forma de explicarle el resultado.
     */
    fun build(rows: List<Row>, orden: Orden = Orden.RELEVANCIA): String? {
        val clauses = rows.mapNotNull { row -> clause(row)?.let { row.op to it } }
        if (clauses.isEmpty()) return null

        // La primera fila entra sola: su operador no tiene nada a lo que unirse.
        var q = clauses.first().second
        for ((op, clause) in clauses.drop(1)) {
            val joint = when (op) {
                Op.Y -> "AND"
                Op.O -> "OR"
                Op.NO -> "AND NOT"
            }
            q = "($q $joint $clause)"
        }

        if (orden.needsRatingsFloor && rows.none { it.field == Field.VALORACIONES && clause(it) != null }) {
            q = "($q AND ${Field.VALORACIONES.solr}:[$RATINGS_FLOOR TO *])"
        }
        return q
    }

    /** Una fila suelta, o null si está vacía y no filtra nada. */
    private fun clause(row: Row): String? = when (row.field.kind) {
        Kind.TEXTO -> row.text.trim().takeIf { it.isNotEmpty() }
            ?.let { "${row.field.solr}:${phrase(it)}" }

        // Sin comillas, así que el valor sí es sintaxis. Lo que no sea un código entero se
        // descarta en vez de limpiarse: quitándole los caracteres malos, `dan" OR *:*` se
        // convierte en `danor`, que es un código que no existe y una búsqueda vacía. Estos
        // valores salen de nuestras propias tablas y no del teclado, pero eso es cierto hoy y
        // basta con que alguien añada un campo para que deje de serlo.
        Kind.CODIGO -> row.text.trim().lowercase()
            .takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '_' } }
            ?.let { "${row.field.solr}:$it" }

        Kind.RANGO -> if (row.from == null && row.to == null) null else {
            "${row.field.solr}:[${row.from ?: "*"} TO ${row.to ?: "*"}]"
        }

        Kind.MINIMO -> row.from?.let { "${row.field.solr}:[$it TO *]" }
    }

    /**
     * El valor como frase entre comillas.
     *
     * Va entre comillas por dos razones a la vez. Una es de significado: `title:the war` sin
     * comillas son dos condiciones sueltas, y con comillas es el título que lleva esas dos
     * palabras seguidas —32.617 obras frente a 274.344—. La otra es que lo escribe el usuario:
     * dentro de una frase, los dos puntos, los paréntesis y las palabras AND y OR son texto y no
     * sintaxis, así que buscar «Fiction: general (1980)» no rompe la consulta ni la cambia por
     * otra. Solo hay que escapar lo que sí sigue significando algo dentro de las comillas.
     */
    private fun phrase(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
