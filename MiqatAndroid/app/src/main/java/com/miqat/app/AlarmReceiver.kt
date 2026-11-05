package com.miqat.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.ensureChannel(ctx)
        val title = intent.getStringExtra("title") ?: "ميقات"
        val body = intent.getStringExtra("body") ?: ""
        val adhan = intent.getBooleanExtra("adhan", false)

        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (adhan) open.putExtra("tab", "prayer")
        val pi = PendingIntent.getActivity(
            ctx, 2, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n = Notification.Builder(ctx, Scheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java)
                .notify(if (adhan) 10 else 11, n)
        } catch (e: SecurityException) {
            // Notification permission not granted; nothing to show.
        }

        Scheduler.scheduleNext(ctx)
    }
}
