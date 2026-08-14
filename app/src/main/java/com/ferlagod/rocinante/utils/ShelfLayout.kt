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
 * Las cuatro estanterías de «Mis libros», que el usuario puede ordenar a su gusto.
 *
 * Solo dos se pueden apagar: la de abandonados y la de por leer. Quien no deja libros a
 * medias —o no apunta lo que piensa leer— tiene ahí una tarjeta que no usa nunca. «Leyendo»
 * y «Leídos» no llevan interruptor a propósito: son el sentido mismo de la pantalla, y sin
 * ellas «Mis libros» no enseñaría ningún libro.
 *
 * El [id] es lo que se guarda en disco, así que **no debe cambiarse** una vez publicado;
 * el [slug] es el que entiende BookWyrm y viaja en las URL de la instancia.
 */
enum class ShelfSection(val id: String, val slug: String, val canHide: Boolean) {
    STOPPED_READING("stopped_reading", "stopped-reading", canHide = true),
    TO_READ("to_read", "to-read", canHide = true),
    READING("reading", "reading", canHide = false),
    READ("read", "read", canHide = false);

    companion object {
        fun fromId(id: String): ShelfSection? = entries.firstOrNull { it.id == id }
        fun fromSlug(slug: String): ShelfSection? = entries.firstOrNull { it.slug == slug }
    }
}

/**
 * Dónde se agrupan las tarjetas de las estanterías.
 *
 * Son cuatro como mucho y la pantalla es alta: arriba dejan medio móvil en blanco y obligan a
 * estirar el pulgar. [BOTTOM] las baja hasta donde está la mano, sin mover el título.
 */
enum class ShelfAlignment(val id: String) {
    TOP("top"),
    BOTTOM("bottom");

    companion object {
        fun fromId(id: String?): ShelfAlignment = entries.firstOrNull { it.id == id } ?: TOP
    }
}

/**
 * Orden, visibilidad y posición de las estanterías de «Mis libros».
 *
 * @property sections todas las estanterías, en el orden elegido.
 * @property hidden las que están apagadas; siguen en [sections] para conservar su sitio si se
 *   vuelven a encender.
 * @property alignment si las tarjetas se agrupan arriba o abajo.
 */
/**
 * Orden, visibilidad y posición de las estanterías de «Mis libros».
 *
 * Una sola lista para todas: las cuatro de BookWyrm y las que el usuario se hace. Estaban
 * separadas y era artificial —quien quiere sus favoritos entre «Leyendo» y «Leídos» no debería
 * tener que pensar en de quién es cada estantería—, así que el orden es común.
 *
 * @property order todos los identificadores, en el orden elegido. Puede haber alguno que ahora
 *   mismo no exista: se conserva para cuando vuelva.
 * @property hidden los apagados; siguen en [order] para conservar su sitio.
 * @property alignment si las tarjetas se agrupan arriba o abajo.
 */
data class ShelfLayout(
    val order: List<String>,
    val hidden: Set<String>,
    val alignment: ShelfAlignment
) {

    /**
     * «Leyendo» y «Leídos» no se pueden apagar: son el sentido mismo de la pantalla, y sin
     * ellas «Mis libros» no enseñaría ningún libro. Las propias del usuario sí, todas.
     */
    fun canHide(identifier: String): Boolean =
        ShelfSection.fromSlug(identifier)?.canHide ?: true

    fun isVisible(identifier: String): Boolean = identifier !in hidden

    fun toggled(identifier: String): ShelfLayout = when {
        !canHide(identifier) -> this
        identifier in hidden -> copy(hidden = hidden - identifier)
        else -> copy(hidden = hidden + identifier)
    }

    fun withAlignment(alignment: ShelfAlignment): ShelfLayout = copy(alignment = alignment)

    /** Saca la estantería de [from] y la deja en [to]. */
    fun moved(from: Int, to: Int): ShelfLayout {
        if (from !in order.indices || to !in order.indices || from == to) return this
        val moved = order.toMutableList()
        moved.add(to, moved.removeAt(from))
        return copy(order = moved)
    }

    /** Mete al final, encendidas, las que aún no estaban. Lo contrario escondería la recién hecha. */
    fun withKnown(identifiers: List<String>): ShelfLayout {
        val newcomers = identifiers.filterNot { it in order }
        return if (newcomers.isEmpty()) this else copy(order = order + newcomers)
    }

    /** Las que existen ahora, en el orden elegido y sin las apagadas. */
    fun visible(identifiers: List<String>): List<String> {
        val present = identifiers.toSet()
        val known = order.filter { it in present }
        val newcomers = identifiers.filterNot { it in order }
        return (known + newcomers).filterNot { it in hidden }
    }

    /**
     * Formato de disco: los identificadores separados por comas, con un "-" delante de los
     * apagados ("read,-to-read,favoritter-315769").
     */
    fun encode(): String = order.joinToString(",") { if (it in hidden) "-$it" else it }

    companion object {
        val DEFAULT = ShelfLayout(
            ShelfSection.entries.map { it.slug },
            emptySet(),
            ShelfAlignment.TOP
        )

        /**
         * Reconstruye la disposición guardada.
         *
         * Se guardan **slugs**, que es como llama la instancia a sus estanterías, para que las
         * de serie y las del usuario quepan en la misma lista. Las versiones anteriores
         * guardaban los ids de la enumeración ("to_read"), así que se siguen entendiendo y se
         * traducen al leer: quien actualiza conserva su orden.
         *
         * Un identificador desconocido se conserva: puede ser una estantería propia que ahora
         * no está —borrada en la web, o una lista que llegó a medias— y su sitio debe esperarla.
         */
        fun decode(raw: String?, alignment: String?): ShelfLayout {
            val align = ShelfAlignment.fromId(alignment)
            if (raw.isNullOrBlank()) return DEFAULT.copy(alignment = align)

            val order = mutableListOf<String>()
            val hidden = mutableSetOf<String>()
            raw.split(",").forEach { entry ->
                val token = entry.trim()
                if (token.isEmpty()) return@forEach
                val isHidden = token.startsWith("-")
                val stored = token.removePrefix("-")
                if (stored.isEmpty()) return@forEach
                val identifier = ShelfSection.fromId(stored)?.slug ?: stored
                if (identifier in order) return@forEach
                order += identifier
                val canHide = ShelfSection.fromSlug(identifier)?.canHide ?: true
                if (isHidden && canHide) hidden += identifier
            }
            if (order.isEmpty()) return DEFAULT.copy(alignment = align)

            ShelfSection.entries.forEach { section ->
                if (section.slug !in order) order += section.slug
            }
            return ShelfLayout(order, hidden, align)
        }
    }
}
