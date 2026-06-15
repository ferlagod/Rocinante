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
package com.ferlagod.rocinante.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ferlagod.rocinante.MainActivity
import com.ferlagod.rocinante.R
import com.ferlagod.rocinante.data.api.NetworkClient
import com.ferlagod.rocinante.data.local.SessionStorage
import kotlinx.coroutines.flow.firstOrNull

/**
 * Worker periódico que se encarga de recordar al usuario continuar leyendo uno de los libros
 * actualmente en su estantería de "Leyendo" ("reading"). Si no tiene libros en esa estantería,
 * le sugiere buscar un libro nuevo.
 *
 * @param context Contexto de ejecución.
 * @param workerParams Parámetros del worker de WorkManager.
 */
class ReadingReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /**
     * Realiza el trabajo en segundo plano: obtiene la sesión del usuario, consulta los libros
     * en la estantería de lectura activa y muestra una notificación local.
     *
     * @return [Result.success] si se ejecutó correctamente o [Result.retry] si falla la conexión de red.
     */
    override suspend fun doWork(): Result {
        try {
            val sessionStorage = SessionStorage(context)
            val session = sessionStorage.sessionFlow.firstOrNull()
            
            if (session != null) {
                var bookTitle: String? = null
                var hasBooksInShelf = true

                try {
                    // Fetch reading shelf
                    val api = NetworkClient.createAuthenticatedApi(session.instanceUrl, session.cookie)
                    val cleanBase = if (session.instanceUrl.startsWith("http")) session.instanceUrl else "https://${session.instanceUrl}"
                    val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
                    val cleanUser = session.username.removePrefix("@").trim()
                    val shelfJsonUrl = "${baseUrl}user/$cleanUser/shelf/reading.json?page=1"
                    
                    val response = api.getShelfData(shelfJsonUrl)
                    val books = response.orderedItems ?: emptyList()
                    
                    if (books.isNotEmpty()) {
                        bookTitle = books.random().title
                    } else {
                        hasBooksInShelf = false
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    e.printStackTrace()
                    // Error de red, asumiremos el fallback genérico
                }

                if (hasBooksInShelf) {
                    showNotification(
                        title = context.getString(R.string.reminder_title),
                        message = context.getString(R.string.reminder_msg_continue_reading, bookTitle ?: context.getString(R.string.reminder_fallback_your_book))
                    )
                } else {
                    showNotification(
                        title = context.getString(R.string.app_name),
                        message = context.getString(R.string.reminder_msg_find_next_book)
                    )
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e.printStackTrace()
        } finally {
            // Reprogramar para el día siguiente (evita el drift)
            try {
                val settings = com.ferlagod.rocinante.data.local.SettingsPreferences(context).settingsFlow.firstOrNull()
                if (settings != null && settings.reminderEnabled) {
                    ReminderManager.schedule(context, settings.reminderHour, settings.reminderMinute)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            }
        }
        return Result.success()
    }

    /**
     * Crea y muestra una notificación local del sistema.
     *
     * @param title Título de la notificación.
     * @param message Mensaje del cuerpo de la notificación.
     */
    private fun showNotification(title: String, message: String) {
        val channelId = "rocinante_reading_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.reminder_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_rocinante_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
