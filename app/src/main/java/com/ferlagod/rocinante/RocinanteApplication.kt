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
package com.ferlagod.rocinante

import android.app.Application
import android.webkit.WebSettings
import com.ferlagod.rocinante.data.api.NetworkClient
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase principal de la aplicación Android [Rocinante].
 *
 * Hereda de [Application] y está anotada con `@HiltAndroidApp` para desencadenar
 * la generación de código de Hilt e inicializar el contenedor principal de
 * inyección de dependencias a nivel de aplicación (Application-level dependency container).
 */
@HiltAndroidApp
class RocinanteApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initUserAgent()
    }

    /**
     * Fija el User-Agent de las peticiones OkHttp al mismo que usa el WebView del
     * sistema (con el que se hace el login y se obtiene la cookie de sesión/clearance),
     * añadiendo el sufijo honesto "Rocinante/<versión>". Así OkHttp y el WebView
     * presentan un UA idéntico y las protecciones que lo verifican no rechazan la
     * cookie al reproducirla. Si el WebView no está disponible se conserva el valor
     * de respaldo de [NetworkClient.userAgent].
     */
    private fun initUserAgent() {
        try {
            val webViewUa = WebSettings.getDefaultUserAgent(this)
            if (webViewUa.isNullOrBlank()) return
            val version = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (_: Exception) {
                null
            }
            NetworkClient.userAgent =
                if (version.isNullOrBlank()) "$webViewUa Rocinante" else "$webViewUa Rocinante/$version"
        } catch (_: Exception) {
            // WebView ausente o no inicializable: se mantiene el UA de respaldo.
        }
    }
}
