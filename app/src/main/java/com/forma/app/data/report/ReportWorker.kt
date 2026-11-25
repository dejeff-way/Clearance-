package com.forma.app.data.report

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forma.app.FormaApp
import com.forma.app.MainActivity
import com.forma.app.R
import com.forma.app.health.HcAvailability
import com.forma.app.util.TimeProvider
import com.forma.engine.TrendAnalyzer
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Runs every Sunday at 6 am: pulls the latest watch data, writes last week's report and sends a notification. */
class ReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as FormaApp).container
        runCatching {
            if (c.healthSource.availability() == HcAvailability.AVAILABLE && c.healthSource.backgroundReadGranted()) c.healthSyncer.sync()
        }
        val r = runCatching { c.reports.refresh() }.getOrNull()
        if (r != null && c.settings.weeklyReportNotify.first()) ReportNotifier.show(applicationContext, r)
        schedule(applicationContext, c.time, replace = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "forma-weekly-report"

        /** Schedules the next run for the coming Sunday 6 am (local time). */
        fun schedule(context: Context, time: TimeProvider, replace: Boolean = false) {
            val now = time.now()
            val thisSunday = TrendAnalyzer.weekStart(now.toLocalDate())
            var next = thisSunday.atTime(WeeklyReportRepository.RELEASE).atZone(time.zone())
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            val delay = Duration.between(now, next).toMillis().coerceAtLeast(60_000)
            val req = OneTimeWorkRequestBuilder<ReportWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
            }
        }
    }
}

object ReportNotifier {
    const val CHANNEL = "weekly_report"
    const val EXTRA_ROUTE = "forma_route"
    private const val ID = 7001

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly report", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Your week in review, every Sunday morning"
        })
    }

    /** Short line for the notification. Lock screens only see "Your weekly report is ready". */
    fun summary(r: WeeklyReport): String = listOfNotNull(
        if (r.workoutsPlanned > 0 || r.workoutsDone > 0) "${r.workoutsDone}/${r.workoutsPlanned} workouts" else null,
        r.prs.takeIf { it.isNotEmpty() }?.let { "${it.size} PR${if (it.size == 1) "" else "s"}" },
        r.sleepScore?.let { "sleep $it" },
        r.stepsAvg?.let { "%,d steps/day".format(it) },
    ).joinToString(" · ").ifEmpty { "Tap to see your week" }

    fun show(context: Context, r: WeeklyReport) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_ROUTE, "report/${r.weekStart}")
        val pi = PendingIntent.getActivity(context, r.weekStart.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Your weekly report is ready").build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Your week is ready")
            .setContentText(summary(r))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .build()
        runCatching { @Suppress("MissingPermission") NotificationManagerCompat.from(context).notify(ID, n) }
    }
}
