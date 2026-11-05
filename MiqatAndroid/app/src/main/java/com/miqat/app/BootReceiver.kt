package com.miqat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are cleared on reboot and drift on clock/timezone changes, so rebuild them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleNext(ctx)
    }
}
