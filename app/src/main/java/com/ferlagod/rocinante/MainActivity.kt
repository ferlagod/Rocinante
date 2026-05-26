/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 Fernando Lago (ferlagod)
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU Affero General Public License (AGPLv3).
 * * AVISO DE DOBLE LICENCIA: Para uso comercial o propietario sin 
 * las obligaciones de liberación de código de la AGPLv3, 
 * es necesario adquirir una licencia comercial previa.
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
import androidx.activity.compose.setContent

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

    val sessionUiState by sessionViewModel.uiState.collectAsState()

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
        startDestination = startDestination
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

    if (!showWebView) {
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
                        modifier = Modifier.size(96.dp)
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
                            onLoginSuccess(cookies, instanceUrl, username)
                        }
                    }
                }

                loadUrl(loginUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}