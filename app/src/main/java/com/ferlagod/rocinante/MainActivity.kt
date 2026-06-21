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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.local.SessionStorage
import com.ferlagod.rocinante.ui.theme.RocinanteTheme
import com.ferlagod.rocinante.data.model.SessionData
import com.ferlagod.rocinante.ui.screens.home.HomeScreen
import com.ferlagod.rocinante.ui.screens.login.SessionViewModel
import com.ferlagod.rocinante.ui.screens.login.SessionViewModelFactory
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.activity.compose.BackHandler

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Actividad principal de Android y punto de entrada a la aplicación Compose.
 * Inicializa el tema global, maneja la pantalla de bienvenida (Splash Screen)
 * y establece el marco visual principal (Edge-To-Edge).
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Instalar SplashScreen ANTES de super.onCreate / setContent
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Aplicar el tema real (post-splash) definido como postSplashScreenTheme en themes.xml
        setTheme(R.style.Theme_Rocinante_Main)
        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val settingsPreferences = remember { com.ferlagod.rocinante.data.local.SettingsPreferences(context) }
            val settingsState by settingsPreferences.settingsFlow.collectAsState(initial = com.ferlagod.rocinante.data.local.SettingsData())
            
            val darkTheme = when (settingsState.themeMode) {
                com.ferlagod.rocinante.data.local.ThemeMode.LIGHT -> false
                com.ferlagod.rocinante.data.local.ThemeMode.DARK -> true
                com.ferlagod.rocinante.data.local.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            RocinanteTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RocinanteApp()
                }
            }
        }
    }
}

/**
 * Composable raíz que establece la arquitectura de navegación general de la app.
 * Gestiona el paso entre las pantallas principales (Login, Home, Settings) y la 
 * comprobación continua de una sesión activa de forma segura.
 */
@Composable
fun RocinanteApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isRootScreen = currentRoute == "home" || currentRoute == "login"
    var showExitConfirmation by remember { mutableStateOf(false) }

    BackHandler(enabled = isRootScreen) {
        showExitConfirmation = true
    }

    val coroutineScope = rememberCoroutineScope()
    val settingsPreferences = remember { com.ferlagod.rocinante.data.local.SettingsPreferences(context) }
    var showChangelog by remember { mutableStateOf(false) }
    val currentVersion = "1.0.4"

    LaunchedEffect(Unit) {
        val prefs = settingsPreferences.settingsFlow.first()
        if (prefs.lastChangelogVersion != currentVersion) {
            showChangelog = true
        }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = {
                showChangelog = false
                coroutineScope.launch {
                    settingsPreferences.setLastChangelogVersion(currentVersion)
                }
            },
            title = { Text(text = stringResource(R.string.changelog_title, currentVersion), fontWeight = FontWeight.Bold) },
            text = { 
                Text(stringResource(R.string.changelog_text_v1_0_4))
            },
            confirmButton = {
                TextButton(onClick = {
                    showChangelog = false
                    coroutineScope.launch {
                        settingsPreferences.setLastChangelogVersion(currentVersion)
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.exit_dialog_title)) },
            text = { Text(stringResource(R.string.exit_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    (context as? android.app.Activity)?.finish()
                }) {
                    Text(stringResource(R.string.exit_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(R.string.exit_dialog_cancel))
                }
            }
        )
    }

    val sessionStorage = remember { SessionStorage(context) }
    val sessionFactory = remember { SessionViewModelFactory(sessionStorage) }
    val sessionViewModel: SessionViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = sessionFactory)

    val sessionUiState by sessionViewModel.uiState.collectAsStateWithLifecycle()

    if (sessionUiState.isCheckingSession) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (sessionUiState.session != null) "home" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { cookie, instance, user ->
                    sessionViewModel.saveSession(
                        SessionData(
                            instanceUrl = instance,
                            username = user,
                            cookie = cookie
                        )
                    )

                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            val session = sessionUiState.session

            if (session != null) {
                HomeScreen(
                    cookie = session.cookie,
                    instanceUrl = session.instanceUrl,
                    username = session.username,
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }
        }

        composable("settings") {
            val session = sessionUiState.session
            if (session != null) {
                com.ferlagod.rocinante.ui.screens.settings.SettingsScreen(
                    username = session.username,
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        sessionViewModel.logout()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

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

@Composable
fun BookWyrmLoginWebView(
    loginUrl: String,
    instanceUrl: String,
    username: String,
    onLoginSuccess: (String, String, String) -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

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
                                    username // Fallback al usuario original si el scraping falla
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