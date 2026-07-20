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
package com.ferlagod.rocinante.ui.screens.login

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.ferlagod.rocinante.R

/**
 * Pantalla inicial de autenticación donde el usuario provee sus credenciales básicas
 * para acceder a una instancia de BookWyrm. Maneja delegación a vistas web
 * para el flujo de autorización real usando cookies.
 *
 * @param onLoginSuccess Callback que se ejecuta cuando el inicio de sesión se completa satisfactoriamente.
 */
@Composable
fun LoginScreen(onLoginSuccess: (String, String, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var instanceUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var showWebView by remember { mutableStateOf(false) }

    // Animación de entrada suave al aparecer
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "login_alpha"
    )

    Crossfade(
        targetState = showWebView,
        label = "login_crossfade",
        animationSpec = tween(durationMillis = 500)
    ) { isWebView ->
        if (!isWebView) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .alpha(alpha),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Logo + nombre de la app
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_rocinante_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(96.dp).clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Card de formulario
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.login_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = instanceUrl,
                            onValueChange = { instanceUrl = it },
                            label = { Text(stringResource(R.string.login_field_instance)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                                focusedLabelColor    = MaterialTheme.colorScheme.primary,
                                cursorColor          = MaterialTheme.colorScheme.primary
                            )
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(stringResource(R.string.login_field_username)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor  = MaterialTheme.colorScheme.primary,
                                cursorColor        = MaterialTheme.colorScheme.primary
                            )
                        )

                        Button(
                            onClick = {
                                if (instanceUrl.isNotBlank() && username.isNotBlank()) {
                                    showWebView = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.login_btn_login),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón secundario para crear cuenta
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://joinbookwyrm.com/"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.login_btn_create_account),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.login_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
            val loginUrl = if (instanceUrl.startsWith("http")) {
                "$instanceUrl/login"
            } else {
                "https://$instanceUrl/login"
            }

            BookWyrmLoginWebView(
                loginUrl = loginUrl,
                instanceUrl = instanceUrl,
                username = username,
                onLoginSuccess = onLoginSuccess
            )
        }
    }
}

/**
 * Componente que muestra un WebView para realizar el proceso de login en la instancia de BookWyrm.
 * Intercepta la carga de páginas para detectar cuando el usuario ha iniciado sesión exitosamente.
 *
 * @param loginUrl URL inicial de inicio de sesión.
 * @param instanceUrl URL base de la instancia.
 * @param username Nombre de usuario a pre-rellenar (si es posible).
 * @param onLoginSuccess Callback ejecutado al detectar una sesión válida, devuelve la cookie de sesión.
 */
@Composable
fun BookWyrmLoginWebView(
    loginUrl: String,
    instanceUrl: String,
    username: String,
    onLoginSuccess: (String, String, String) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Fijar el UA del WebView al mismo que reproducirá OkHttp, para que la
                // cookie de sesión/clearance emitida durante el login sea válida después.
                settings.userAgentString = com.ferlagod.rocinante.data.api.NetworkClient.userAgent

                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDarkTheme)
                } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDark(
                        settings,
                        if (isDarkTheme) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                    )
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        val cookies = CookieManager.getInstance()
                            .getCookie(url)
                            .orEmpty()

                        val hasSession = cookies.contains("sessionid=")
                        val isStillLoginPage = url.contains("/login")

                        if (hasSession && !isStillLoginPage) {
                            // Inyectamos JavaScript para extraer el nombre de usuario de la sesión real en BookWyrm
                            view.evaluateJavascript(
                                "(function() { " +
                                "  try { " +
                                "    var profileLink = document.querySelector('nav a[href^=\"/user/\"], .navbar a[href^=\"/user/\"]'); " +
                                "    if (!profileLink) { profileLink = document.querySelector('a[href^=\"/user/\"]'); } " +
                                "    if (profileLink) { " +
                                "        var parts = profileLink.getAttribute('href').split('/'); " +
                                "        if (parts.length > 2) return parts[2]; " +
                                "    } " +
                                "  } catch(e) { console.error(e); } " +
                                "  return null; " +
                                "})();"
                            ) { result ->
                                val realUsername = result?.trim('"', '\'')
                                
                                val finalUsername = if (realUsername.isNullOrBlank() || realUsername == "null") {
                                    username.removePrefix("@").substringBefore("@").trim() // Fallback al usuario original si el scraping falla
                                } else {
                                    realUsername
                                }
                                
                                onLoginSuccess(cookies, instanceUrl, finalUsername)
                            }
                        }
                    }
                }

                loadUrl(loginUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
