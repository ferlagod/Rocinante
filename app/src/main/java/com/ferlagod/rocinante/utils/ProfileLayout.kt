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
 * Bloques que el usuario puede ordenar u ocultar en su perfil. La cabecera —avatar, nombre
 * y seguidores— no está aquí a propósito: es la identidad del perfil y no se puede quitar.
 *
 * El [id] es lo que se guarda en disco, así que **no debe cambiarse** una vez publicado;
 * renombrar una constante de Kotlin es libre, cambiar su id descoloca los perfiles guardados.
 */
enum class ProfileSection(val id: String) {
    READING_STATS("reading_stats"),
    CURRENTLY_READING("currently_reading"),
    TO_READ("to_read"),
    BIO("bio"),
    READING_GOAL("reading_goal"),
    TOP_RATED("top_rated"),
    TOP_AUTHORS("top_authors"),
    RATINGS("ratings"),
    LANGUAGES("languages"),
    FORMATS("formats"),
    SUGGESTED_USERS("suggested_users");

    companion object {
        fun fromId(id: String): ProfileSection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Orden y visibilidad de los bloques del perfil.
 *
 * @property sections todos los bloques, en el orden elegido.
 * @property hidden los que están desactivados; siguen en [sections] para conservar su sitio
 *   si se vuelven a activar.
 */
data class ProfileLayout(
    val sections: List<ProfileSection>,
    val hidden: Set<ProfileSection>
) {
    val visibleSections: List<ProfileSection> get() = sections.filterNot { it in hidden }

    fun isVisible(section: ProfileSection): Boolean = section !in hidden

    fun toggled(section: ProfileSection): ProfileLayout = copy(
        hidden = if (section in hidden) hidden - section else hidden + section
    )

    /**
     * Formato de disco: los ids separados por comas, con un "-" delante de los ocultos
     * ("bio,-formats,ratings"). Legible y fácil de inspeccionar al depurar.
     */
    fun encode(): String = sections.joinToString(",") { section ->
        if (section in hidden) "-${section.id}" else section.id
    }

    companion object {
        /** Orden por defecto: el mismo en que se fueron añadiendo los bloques al perfil. */
        val DEFAULT = ProfileLayout(ProfileSection.entries.toList(), emptySet())

        /**
         * Reconstruye la disposición guardada.
         *
         * Tolera lo que el futuro traiga: los ids desconocidos —bloques retirados en una
         * versión posterior— se ignoran, y los bloques que la versión actual conoce pero no
         * estaban guardados se añaden al final, visibles. Así, cuando se añade un bloque
         * nuevo, aparece también a quien ya tenía una disposición propia en lugar de
         * desaparecer por no figurar en la lista.
         */
        fun decode(raw: String?): ProfileLayout {
            if (raw.isNullOrBlank()) return DEFAULT

            val ordered = mutableListOf<ProfileSection>()
            val hidden = mutableSetOf<ProfileSection>()
            raw.split(",").forEach { entry ->
                val token = entry.trim()
                if (token.isEmpty()) return@forEach
                val isHidden = token.startsWith("-")
                val section = ProfileSection.fromId(token.removePrefix("-")) ?: return@forEach
                if (section in ordered) return@forEach
                ordered.add(section)
                if (isHidden) hidden.add(section)
            }
            if (ordered.isEmpty()) return DEFAULT

            ProfileSection.entries.forEach { section ->
                if (section !in ordered) ordered.add(section)
            }
            return ProfileLayout(ordered, hidden)
        }
    }
}
