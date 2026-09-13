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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ferlagod.rocinante.data.api.NetworkClient
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Clase principal de la aplicación Android [Rocinante].
 *
 * Hereda de [Application] y está anotada con `@HiltAndroidApp` para desencadenar
 * la generación de código de Hilt e inicializar el contenedor principal de
 * inyección de dependencias a nivel de aplicación (Application-level dependency container).
 * Implementa [ImageLoaderFactory] para proporcionar un motor de carga de imágenes Coil
 * optimizado con caché en disco y memoria para portadas y avatares.
 */
@HiltAndroidApp
class RocinanteApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        invalidateStaleCacheOnUpdate()
        initUserAgent()
    }

    /**
     * Comprueba si la aplicación se acaba de actualizar a una nueva versión de código.
     * Si es así, purga automáticamente los archivos temporales JSON de pantalla y la caché HTTP obsoleta
     * en [cacheDir] para garantizar que la nueva versión arranque con un estado limpio y no arrastre
     * formatos, errores visuales o parseos antiguos de la versión anterior.
     * Las preferencias de usuario, DataStore y cookies de sesión se conservan intactas en [filesDir].
     */
    private fun invalidateStaleCacheOnUpdate() {
        try {
            val currentVersion = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
            if (currentVersion.isBlank()) return

            val prefs = getSharedPreferences("app_version_prefs", android.content.Context.MODE_PRIVATE)
            val lastVersion = prefs.getString("last_version_installed", null)

            if (lastVersion != null && lastVersion != currentVersion) {
                // Actualización detectada: purgar archivos y carpetas de caché temporal
                cacheDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.endsWith(".json") || name == "http_cache" || name == "authors" || name == "book_details") {
                        file.deleteRecursively()
                    }
                }
            }

            prefs.edit().putString("last_version_installed", currentVersion).apply()
        } catch (_: Exception) {
            // Ignorar defensivamente cualquier excepción durante la purga de caché
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                NetworkClient.lastOkHttpClient ?: OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
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
