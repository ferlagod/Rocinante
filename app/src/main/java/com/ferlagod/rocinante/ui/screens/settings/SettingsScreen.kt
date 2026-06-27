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
package com.ferlagod.rocinante.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.R.string.settings_clear_cache_desc
import com.ferlagod.rocinante.R.string.settings_version
import com.ferlagod.rocinante.data.local.SettingsPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ferlagod.rocinante.data.local.ThemeMode

/**
 * Pantalla principal de ajustes y configuración de la aplicación.
 * Permite al usuario modificar preferencias visuales, opciones de comportamiento,
 * gestionar notificaciones y cerrar la sesión activa.
 *
 * @param username Nombre del usuario autenticado actualmente, utilizado para advertencias de confirmación.
 * @param onBack Callback para retroceder a la pantalla anterior en la navegación.
 * @param onLogout Callback ejecutado al confirmar el cierre de sesión.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    username: String,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val settingsPreferences = remember { SettingsPreferences(context) }
    val factory = remember { SettingsViewModelFactory(settingsPreferences) }
    val viewModel: SettingsViewModel = viewModel(factory = factory)

    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Notifications permission state
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted && settingsState.reminderEnabled) {
                com.ferlagod.rocinante.workers.ReminderManager.schedule(context, settingsState.reminderHour, settingsState.reminderMinute)
                Toast.makeText(context, context.getString(R.string.reminder_activated_toast), Toast.LENGTH_SHORT).show()
            } else if (!isGranted) {
                viewModel.setReminderEnabled(false)
                Toast.makeText(context, context.getString(R.string.permission_denied_toast), Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Appearance Section
            item {
                SettingsSectionTitle(stringResource(R.string.settings_appearance))
            }
            item {
                ThemeSelector(
                    currentTheme = settingsState.themeMode,
                    onThemeSelected = { viewModel.setThemeMode(it) }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_language),
                    subtitle = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "Sistema" },
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )
            }
            item { Divider() }

            item {
                SettingsSectionTitle(stringResource(R.string.settings_notifications))
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = settingsState.reminderEnabled,
                    onCheckedChange = { checked ->
                        viewModel.setReminderEnabled(checked)
                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                com.ferlagod.rocinante.workers.ReminderManager.schedule(context, settingsState.reminderHour, settingsState.reminderMinute)
                                Toast.makeText(context, context.getString(R.string.reminder_activated_toast), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            com.ferlagod.rocinante.workers.ReminderManager.cancel(context)
                            Toast.makeText(context, context.getString(R.string.notifications_disabled_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            item { Divider() }

            // Behavior Section
            item {
                SettingsSectionTitle(stringResource(R.string.settings_behavior))
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.settings_open_links),
                    subtitle = stringResource(R.string.settings_open_links_desc),
                    checked = settingsState.openLinksExternally,
                    onCheckedChange = { viewModel.setOpenLinksExternally(it) }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = stringResource(settings_clear_cache_desc),
                    onClick = {
                        context.imageLoader.diskCache?.clear()
                        context.imageLoader.memoryCache?.clear()
                        Toast.makeText(context, context.getString(R.string.settings_cache_cleared), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            item { Divider() }

            // About Section
            item {
                SettingsSectionTitle(stringResource(R.string.settings_about))
            }
            item {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                SettingsItem(
                    title = stringResource(settings_version),
                    subtitle = "${packageInfo.versionName} (${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)})",
                    onClick = null
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_developer),
                    subtitle = "ferlagod",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://frikiverse.zone/"))
                        context.startActivity(intent)
                    }
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_donate),
                    subtitle = stringResource(R.string.settings_donate_desc),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://liberapay.com/ferlagod./"))
                        context.startActivity(intent)
                    }
                )
            }
            item { Divider() }

            // Account Section
            item {
                SettingsSectionTitle(stringResource(R.string.settings_account))
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_logout),
                    subtitle = stringResource(R.string.settings_logout_desc),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showLogoutDialog = true }
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm, username)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.settings_logout_confirm_btn), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.settings_logout_cancel))
                }
            }
        )
    }
}

/**
 * Componente visual que muestra un título de sección dentro de la pantalla de ajustes.
 * @param title Texto del título a mostrar.
 */
@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

/**
 * Elemento de configuración genérico clicable con un icono, título, subtítulo y una acción opcional.
 * 
 * @param icon Icono principal a mostrar.
 * @param title Título principal del ajuste.
 * @param subtitle Descripción secundaria del ajuste.
 * @param onClick Acción a realizar al pulsar el elemento.
 * @param trailingContent Componente visual opcional a mostrar al final de la fila.
 */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 16.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null && icon == null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Ajuste tipo interruptor (Switch) para opciones booleanas (ej. mostrar notificaciones).
 *
 * @param icon Icono principal.
 * @param title Título del ajuste.
 * @param subtitle Descripción del ajuste.
 * @param checked Estado actual del interruptor.
 * @param onCheckedChange Callback ejecutado cuando se cambia el estado.
 */
@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

/**
 * Selector de tema de la aplicación (Light, Dark, System).
 * Muestra tres botones para elegir la apariencia visual preferida.
 *
 * @param currentTheme Tema actualmente seleccionado.
 * @param onThemeSelected Callback al seleccionar un nuevo tema.
 */
@Composable
fun ThemeSelector(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(
            ThemeMode.SYSTEM to R.string.settings_theme_system,
            ThemeMode.LIGHT to R.string.settings_theme_light,
            ThemeMode.DARK to R.string.settings_theme_dark
        )

        options.forEach { (mode, textRes) ->
            FilterChip(
                selected = currentTheme == mode,
                onClick = { onThemeSelected(mode) },
                label = { Text(stringResource(textRes)) },
                leadingIcon = if (currentTheme == mode) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
    }
}

/**
 * Separador visual estándar para listas de configuraciones.
 */
@Composable
fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    )
}

/**
 * Diálogo para seleccionar el idioma de la interfaz de la aplicación.
 * Permite cambiar la configuración regional (Locale) dinámicamente.
 *
 * @param onDismiss Callback ejecutado al cerrar el diálogo.
 */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val languages = listOf(
        "" to "Sistema",
        "es" to "Español",
        "en" to "English",
        "gl" to "Galego",
        "ca" to "Català",
        "fr" to "Français",
        "de" to "Deutsch",
        "it" to "Italiano",
        "pt" to "Português",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "ro" to "Română",
        "cs" to "Čeština",
        "sv" to "Svenska",
        "el" to "Ελληνικά",
        "fi" to "Suomi",
        "uk" to "Українська"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            LazyColumn {
                items(languages.size) { index ->
                    val (tag, name) = languages[index]
                    Text(
                        text = if (tag.isEmpty()) stringResource(R.string.settings_theme_system) else name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                AppCompatDelegate.setApplicationLocales(
                                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(tag)
                                )
                                onDismiss()
                            }
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_logout_cancel))
            }
        }
    )
}

// WorkManager functions removed, using ReminderManager instead
