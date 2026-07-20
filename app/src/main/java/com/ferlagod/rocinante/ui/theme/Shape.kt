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
package com.ferlagod.rocinante.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Definición del sistema de formas (Shapes) de la aplicación utilizando Material Design 3.
 *
 * Define los radios de curvatura de esquinas estándar aplicados en toda la interfaz de usuario:
 * - **small** (8.dp): Esquinas redondeadas para elementos pequeños (portadas de libros, chips, botones secundarios).
 * - **medium** (16.dp): Esquinas redondeadas para elementos medianos (tarjetas de publicaciones, diálogos interactivos).
 * - **large** (24.dp): Esquinas pronunciadas para contenedores grandes (hojas inferiores / BottomSheets, tarjetas destacadas).
 */
val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)
