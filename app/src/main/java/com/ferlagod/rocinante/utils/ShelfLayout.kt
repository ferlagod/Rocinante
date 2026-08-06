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
data class ShelfLayout(
    val sections: List<ShelfSection>,
    val hidden: Set<ShelfSection>,
    val alignment: ShelfAlignment
) {
    val visibleSections: List<ShelfSection> get() = sections.filterNot { it in hidden }

    fun isVisible(section: ShelfSection): Boolean = section !in hidden

    /** Apagar una estantería que no se puede apagar no hace nada, en vez de fallar. */
    fun toggled(section: ShelfSection): ShelfLayout = when {
        !section.canHide -> this
        section in hidden -> copy(hidden = hidden - section)
        else -> copy(hidden = hidden + section)
    }

    fun withAlignment(alignment: ShelfAlignment): ShelfLayout = copy(alignment = alignment)

    /**
     * Formato de disco: los ids separados por comas, con un "-" delante de los apagados
     * ("stopped_reading,-to_read,reading,read"). El mismo que usa [ProfileLayout], por lo
     * mismo: se lee de un vistazo al depurar. La posición se guarda aparte.
     */
    fun encode(): String = sections.joinToString(",") { section ->
        if (section in hidden) "-${section.id}" else section.id
    }

    companion object {
        /**
         * Orden por defecto: el de la enumeración, que es el que tenía la pantalla antes de
         * poder cambiarse.
         */
        val DEFAULT = ShelfLayout(ShelfSection.entries.toList(), emptySet(), ShelfAlignment.TOP)

        /**
         * Reconstruye la disposición guardada.
         *
         * Tolerante como la del perfil: ids desconocidos —estanterías retiradas en una versión
         * posterior— se ignoran, y las que esta versión conoce pero no estaban guardadas se
         * añaden al final, encendidas. Además ignora un "-" delante de una estantería que no se
         * puede apagar, para que un fichero manipulado a mano no deje la pantalla vacía.
         */
        fun decode(raw: String?, alignment: String?): ShelfLayout {
            val align = ShelfAlignment.fromId(alignment)
            if (raw.isNullOrBlank()) return DEFAULT.copy(alignment = align)

            val ordered = mutableListOf<ShelfSection>()
            val hidden = mutableSetOf<ShelfSection>()
            raw.split(",").forEach { entry ->
                val token = entry.trim()
                if (token.isEmpty()) return@forEach
                val isHidden = token.startsWith("-")
                val section = ShelfSection.fromId(token.removePrefix("-")) ?: return@forEach
                if (section in ordered) return@forEach
                ordered.add(section)
                if (isHidden && section.canHide) hidden.add(section)
            }
            if (ordered.isEmpty()) return DEFAULT.copy(alignment = align)

            ShelfSection.entries.forEach { section ->
                if (section !in ordered) ordered.add(section)
            }
            return ShelfLayout(ordered, hidden, align)
        }
    }
}
