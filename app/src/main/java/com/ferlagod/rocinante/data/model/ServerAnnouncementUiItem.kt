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

/**
 * Tipos visuales de anuncios del servidor admitidos por BookWyrm (basados en Bulma CSS).
 */
enum class AnnouncementDisplayType {
    PRIMARY,
    SUCCESS,
    INFO,
    WARNING,
    DANGER
}

/**
 * Representa un aviso o anuncio del servidor publicado por el administrador de la instancia BookWyrm.
 *
 * En BookWyrm, el administrador puede crear anuncios generales para informar a toda la comunidad
 * (mantenimiento técnico, cambios de normas, avisos del servidor, etc.). En la interfaz web se
 * representan en el layout general como un elemento `<aside class="notification ...">`.
 *
 * @property id Identificador único del anuncio (ej. "42").
 * @property preview Titular o resumen introductorio del anuncio.
 * @property contentText Texto descriptivo detallado (opcional).
 * @property contentHtml Contenido descriptivo en HTML (opcional).
 * @property eventDate Fecha del evento asociada al aviso si la hubiere (ej. "Hoy", "25 Sep").
 * @property displayType Tipo de severidad/color del anuncio ([AnnouncementDisplayType]).
 * @property postedByName Nombre visible del administrador que publicó el anuncio.
 * @property postedByUrl Enlace al perfil del administrador en la instancia.
 */
data class ServerAnnouncementUiItem(
    val id: String,
    val preview: String,
    val contentText: String? = null,
    val contentHtml: String? = null,
    val eventDate: String? = null,
    val displayType: AnnouncementDisplayType = AnnouncementDisplayType.INFO,
    val postedByName: String? = null,
    val postedByUrl: String? = null
)
