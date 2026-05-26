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

class ReadingReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val sessionStorage = SessionStorage(context)
            val session = sessionStorage.sessionFlow.firstOrNull()
            
            if (session != null) {
                // Fetch reading shelf
                val api = NetworkClient.createAuthenticatedApi(session.instanceUrl, session.cookie)
                val cleanBase = if (session.instanceUrl.startsWith("http")) session.instanceUrl else "https://${session.instanceUrl}"
                val baseUrl = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
                val cleanUser = session.username.removePrefix("@").trim()
                val shelfJsonUrl = "${baseUrl}user/$cleanUser/shelf/reading.json?page=1"
                
                val response = api.getShelfData(shelfJsonUrl)
                val books = response.orderedItems ?: emptyList()
                
                if (books.isNotEmpty()) {
                    val randomBook = books.random()
                    showNotification(
                        title = context.getString(R.string.reminder_title),
                        message = context.getString(R.string.reminder_msg_continue_reading, randomBook.title ?: context.getString(R.string.reminder_fallback_your_book))
                    )
                } else {
                    showNotification(
                        title = context.getString(R.string.app_name),
                        message = context.getString(R.string.reminder_msg_find_next_book)
                    )
                }
            }
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Si falla la red, no enviamos notificación para no molestar
            return Result.retry()
        }
    }

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
