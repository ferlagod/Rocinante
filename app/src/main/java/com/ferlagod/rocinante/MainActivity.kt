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
import com.ferlagod.rocinante.ui.screens.login.LoginScreen
import com.ferlagod.rocinante.data.model.*

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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.activity.compose.BackHandler

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.first
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Actividad principal de Android y punto de entrada a la aplicación Compose.
 * Inicializa el tema global, maneja la pantalla de bienvenida (Splash Screen)
 * y establece el marco visual principal (Edge-To-Edge).
 */
@AndroidEntryPoint
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
    val currentVersion = "1.1.9"

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
                Text(stringResource(R.string.changelog_text_v1_1_9))
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

    val sessionViewModel: SessionViewModel = androidx.hilt.navigation.compose.hiltViewModel()

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
