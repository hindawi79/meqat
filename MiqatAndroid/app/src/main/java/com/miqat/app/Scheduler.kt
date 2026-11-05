package com.miqat.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.IslamicCalendar
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps exactly one alarm pending: the next adhan (or pre-adhan reminder).
 * When it fires, AlarmReceiver posts the notification and calls scheduleNext() again.
 */
object Scheduler {
    const val CHANNEL = "adhan"
    private const val PREFS = "miqat"
    private const val KEY = "settings"

    private val NAMES = linkedMapOf(
        "fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر",
        "maghrib" to "المغرب", "isha" to "العشاء",
    )

    fun saveSettings(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
        scheduleNext(ctx)
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "مواقيت الصلاة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيه عند دخول وقت كل صلاة"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
        }
        nm.createNotificationChannel(ch)
    }

    private fun pending(ctx: Context, intent: Intent?): PendingIntent? {
        val i = intent ?: Intent(ctx, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (intent == null) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(ctx, 1, i, flags)
    }

    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        pending(ctx, null)?.let { am.cancel(it) }

        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        val j = try { JSONObject(raw) } catch (e: Exception) { return }
        if (!j.optBoolean("enabled", false)) return

        val zone = try { ZoneId.of(j.optString("tz")) } catch (e: Exception) { ZoneId.systemDefault() }
        val st = PrayerCalc.Settings(
            lat = j.optDouble("lat", 21.4225), lng = j.optDouble("lng", 39.8262),
            fajrAngle = j.optDouble("fajr", 18.5), ishaAngle = j.optDouble("isha", 0.0),
            ishaMin = j.optInt("ishaMin", 0), asrFactor = j.optInt("asr", 1),
        )
        val pre = j.optInt("pre", 0)
        val place = j.optString("place", "")
        val now = System.currentTimeMillis()

        data class Ev(val at: Long, val title: String, val body: String, val adhan: Boolean)
        val events = mutableListOf<Ev>()
        val today = LocalDate.now(zone)
        for (offset in 0..1) {
            val d = today.plusDays(offset.toLong())
            val ramadan = j.optBoolean("umm", false) && isRamadan(d, zone)
            val times = PrayerCalc.compute(d.year, d.monthValue, d.dayOfMonth, st, ramadan)
            for ((k, name) in NAMES) {
                val t = times[k] ?: continue
                events += Ev(t, "حان الآن موعد أذان $name", "بتوقيت $place", true)
                if (pre > 0) events += Ev(t - pre * 60_000L, "بقي $pre دقائق على أذان $name", place, false)
            }
        }
        val next = events.filter { it.at > now + 1000 }.minByOrNull { it.at } ?: return

        val intent = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("title", next.title).putExtra("body", next.body).putExtra("adhan", next.adhan)
        val pi = pending(ctx, intent)!!
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
        }
    }

    private fun isRamadan(d: LocalDate, zone: ZoneId): Boolean = try {
        val cal = IslamicCalendar(android.icu.util.TimeZone.getTimeZone(zone.id))
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        cal.get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN
    } catch (e: Exception) { false }
}
