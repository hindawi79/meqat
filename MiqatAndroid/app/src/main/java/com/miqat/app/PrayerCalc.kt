package com.miqat.app

import kotlin.math.*

/**
 * Astronomical prayer-time calculation. Same method as the web page
 * (assets/index.html), so the notification times match what the screen shows.
 * All results are epoch milliseconds (UTC), rounded to the minute.
 */
object PrayerCalc {
    private const val R = PI / 180.0
    private fun s(d: Double) = sin(d * R)
    private fun c(d: Double) = cos(d * R)
    private fun t(d: Double) = tan(d * R)
    private fun asinD(x: Double) = asin(x) / R
    private fun acosD(x: Double) = acos(x) / R
    private fun atan2D(y: Double, x: Double) = atan2(y, x) / R
    private fun acotD(x: Double) = atan(1 / x) / R
    private fun fix(a: Double, b: Double): Double { val r = a - b * floor(a / b); return if (r < 0) r + b else r }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0); val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private data class Sun(val decl: Double, val eqt: Double)

    private fun sunPos(jd: Double): Sun {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * s(g) + 0.020 * s(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = atan2D(c(e) * s(l), c(l)) / 15
        return Sun(asinD(s(e) * s(l)), q / 15 - fix(ra, 24.0))
    }

    data class Settings(
        val lat: Double, val lng: Double,
        val fajrAngle: Double, val ishaAngle: Double, val ishaMin: Int,
        val asrFactor: Int,
    )

    /** Returns map of prayer key -> epoch ms (null when the sun never reaches the angle). */
    fun compute(year: Int, month: Int, day: Int, st: Settings, ramadan: Boolean): Map<String, Long?> {
        val jd = julian(year, month, day) - st.lng / (15 * 24)
        fun sp(h: Double) = sunPos(jd + h / 24)
        fun mid(h: Double) = fix(12 - sp(h).eqt, 24.0)
        fun angleT(a: Double, h: Double, ccw: Boolean): Double {
            val dc = sp(h).decl
            val v = (-s(a) - s(dc) * s(st.lat)) / (c(dc) * c(st.lat))
            if (v < -1 || v > 1) return Double.NaN
            val tt = acosD(v) / 15
            return mid(h) + if (ccw) -tt else tt
        }
        fun asrT(f: Int, h: Double): Double {
            val dc = sp(h).decl
            return angleT(-acotD(f + t(abs(st.lat - dc))), h, false)
        }

        val hours = linkedMapOf(
            "fajr" to angleT(st.fajrAngle, 5.0, true),
            "sunrise" to angleT(0.833, 6.0, true),
            "dhuhr" to mid(12.0),
            "asr" to asrT(st.asrFactor, 13.0),
            "maghrib" to angleT(0.833, 18.0, false),
        )
        hours["isha"] = if (st.ishaMin > 0)
            hours["maghrib"]!! + (st.ishaMin + if (ramadan) 30 else 0) / 60.0
        else angleT(st.ishaAngle, 18.0, false)

        val base = java.time.LocalDate.of(year, month, day)
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        return hours.mapValues { (_, h) ->
            if (h.isNaN()) null
            else {
                val ms = base + ((h - st.lng / 15) * 3_600_000).toLong()
                ((ms + 30_000) / 60_000) * 60_000
            }
        }
    }
}
