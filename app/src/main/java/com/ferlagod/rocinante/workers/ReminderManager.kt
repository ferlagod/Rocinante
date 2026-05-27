/*
 * Rocinante - Cliente Android para BookWyrm
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: se puede redistribuir y/o modificar
 * bajo los términos de la GNU General Public License versión 3 (GPLv3).
 */
package com.ferlagod.rocinante.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Gestor encargado de programar y cancelar los recordatorios de lectura diarios utilizando WorkManager.
 */
object ReminderManager {
    private const val WORK_NAME = "ReadingReminder"

    /**
     * Programa una tarea periódica diaria para recordar al usuario que lea.
     * Calcula el retraso inicial necesario para que se ejecute a la hora y minuto especificados.
     *
     * @param context Contexto de la aplicación.
     * @param hour Hora en formato de 24 horas.
     * @param minute Minuto de ejecución.
     */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Si la hora ya pasó hoy, se programa para mañana a la misma hora
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val workRequest = OneTimeWorkRequestBuilder<ReadingReminderWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Cancela la tarea programada de recordatorios de lectura.
     *
     * @param context Contexto de la aplicación.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
